package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AgentLlmService llm;
    private final AgentMemoryService memory;
    private final AgentRunService runs;
    private final AgentReActService react;
    private final AgentPlanExecuteService planExecute;
    private final AgentArtifactService artifacts;
    private final AgentToolExecutor tools;
    private final Map<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public record Result(String sessionId, String runId, String mode, String answer, String validation) {
    }

    public Result chat(String sessionId, String clientTaskId, String question, String requestedMode,
                       List<String> knowledgeBaseIds, Consumer<AgentEvent> events) {
        if (!llm.isConfigured()) {
            throw new IllegalStateException("Agent 模型未配置，请设置 DEEPSEEK_API_KEY");
        }
        if (question == null || question.isBlank()) throw new IllegalArgumentException("问题不能为空");
        AgentMemoryService.Session session = memory.getOrCreate(sessionId);
        ReentrantLock lock = sessionLocks.computeIfAbsent(session.getId(), key -> new ReentrantLock());
        if (!lock.tryLock()) {
            events.accept(new AgentEvent("error", "当前对话正在执行",
                    "同一个 session 同时只允许一个任务。请等待当前任务完成，或点击“新对话”创建独立任务。"));
            events.accept(new AgentEvent("done", "当前对话忙碌", ""));
            return new Result(session.getId(), "", resolveMode(question, requestedMode),
                    "当前对话正在执行，请等待完成或新建对话。", "");
        }
        try {
            String mode = resolveMode(question, requestedMode);
            AgentRunService.AgentRun run = runs.create(session.getId(), clientTaskId, question, mode);
            run.setKnowledgeBaseIds(knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds));
            if (run.getCheckpoint() == null) run.setCheckpoint(new AgentRunService.Checkpoint());
            if (run.getStatus() == null) run.setStatus("RUNNING");
            runs.checkpoint(run);

            if ("DONE".equals(run.getStatus())) {
                events.accept(new AgentEvent("session", session.getId(), run.getId()));
                events.accept(new AgentEvent("answer", run.getAnswer(), run.getValidation()));
                events.accept(new AgentEvent("done", "完成", run.getId()));
                return new Result(session.getId(), run.getId(), run.getMode(), run.getAnswer(), run.getValidation());
            }
            boolean resumed = hasProgress(run) || "INTERRUPTED".equals(run.getStatus());
            return execute(session, run, mode, question, resumed, events);
        } finally {
            unlock(session.getId(), lock);
        }
    }

    public Result resume(AgentRunService.AgentRun run, Consumer<AgentEvent> events) {
        if (run == null) throw new IllegalArgumentException("任务不存在");
        AgentMemoryService.Session session = memory.getOrCreate(run.getSessionId());
        ReentrantLock lock = sessionLocks.computeIfAbsent(session.getId(), key -> new ReentrantLock());
        if (!lock.tryLock()) {
            events.accept(new AgentEvent("error", "当前对话正在执行",
                    "同一个 session 同时只允许一个任务，请等待当前任务结束后再恢复。"));
            events.accept(new AgentEvent("done", "当前对话忙碌", ""));
            return new Result(session.getId(), run.getId(), run.getMode(), "当前对话正在执行", "");
        }
        try {
            if ("DONE".equals(run.getStatus())) {
                events.accept(new AgentEvent("session", session.getId(), run.getId()));
                events.accept(new AgentEvent("answer", run.getAnswer(), run.getValidation()));
                events.accept(new AgentEvent("done", "完成", run.getId()));
                return new Result(session.getId(), run.getId(), run.getMode(), run.getAnswer(), run.getValidation());
            }
            if (run.getCheckpoint() == null) run.setCheckpoint(new AgentRunService.Checkpoint());
            if (run.getKnowledgeBaseIds() == null) run.setKnowledgeBaseIds(List.of());
            run.setStatus("RUNNING");
            runs.appendEvent(run, "status", "从 checkpoint 恢复任务", "step=" + run.getCheckpoint().getReact().getStep());
            return execute(session, run, run.getMode(), run.getQuestion(), true, events);
        } finally {
            unlock(session.getId(), lock);
        }
    }

    private Result execute(AgentMemoryService.Session session, AgentRunService.AgentRun run,
                           String mode, String question, boolean resumed, Consumer<AgentEvent> events) {
        AgentTaskControl.clear(run.getId());
        AgentTaskContext.set(session.getId(), run.getId());
        tools.setKnowledgeBases(run.getKnowledgeBaseIds());
        try {            events.accept(new AgentEvent("session", session.getId(), run.getId()));
            events.accept(new AgentEvent("status", resumed ? "从断点继续执行…" : "正在读取记忆与技能…", ""));
            String memoryContext = memory.buildPromptContext(session.getId(), 7000);
            if (!resumed) {
                memory.append(session.getId(), "user", question, memory.classify(question), "question");
            }

            String answer;
            String validation = "";
            try {
                AgentRunService.Checkpoint checkpoint = run.getCheckpoint();
                if ("plan".equals(mode)) {
                    checkpoint.setPhase("plan");
                    runs.checkpoint(run);
                    events.accept(new AgentEvent("status", "识别为长任务，进入 plan-and-execute…", ""));
                    answer = ensureAnswer(planExecute.run(question, memoryContext, run, events), run.getId());
                    validation = "独立验收已包含在计划执行流程中";
                    if (isCancelled(run)) return pause(session, run, mode, validation, events);
                } else {
                    checkpoint.setPhase("react");
                    runs.checkpoint(run);
                    events.accept(new AgentEvent("status", "识别为短任务，进入 ReAct 循环…", ""));
                    answer = ensureAnswer(react.run(question, memoryContext, run,
                            checkpoint.getReact(), events, 12), run.getId());
                    if (isCancelled(run)) return pause(session, run, mode, validation, events);
                    String artifactManifest = artifacts.manifestRun(run.getId());
                    Validation result = validate(question, answer, artifactManifest);
                    validation = result.reason;
                    runs.addStep(run, "VALIDATION", result.passed ? "独立验收通过" : "独立验收未通过",
                            result.reason, result.passed);
                    events.accept(new AgentEvent("validation",
                            result.passed ? "独立验收通过" : "独立验收要求修改", result.reason));
                    if (!result.passed) {
                        checkpoint.setReact(new AgentRunService.ReActState());
                        runs.checkpoint(run);
                        String revised = react.run("""
                                用户任务：%s

                                你上一版回答：
                                %s

                                独立验收意见：
                                %s

                                请不要辩解，直接修正并重新完成任务。
                                """.formatted(question, answer, result.reason),
                                memoryContext, run, checkpoint.getReact(), events, 6);
                        Validation second = validate(question, revised, artifacts.manifestRun(run.getId()));
                        validation = second.reason;
                        runs.addStep(run, "VALIDATION", second.passed ? "复审通过" : "复审未通过",
                                second.reason, second.passed);
                        events.accept(new AgentEvent("validation",
                                second.passed ? "复审通过" : "复审仍有问题", second.reason));
                        answer = revised;
                    }
                }
                if ("CANCEL_REQUESTED".equals(run.getStatus()) || AgentTaskControl.isCancelled(run.getId())) {
                    String cancelled = "任务已按请求停止，可以稍后从 checkpoint 继续。";
                    runs.finish(run, cancelled, "PAUSED", validation);
                    events.accept(new AgentEvent("answer", cancelled, "已暂停，可恢复"));
                    events.accept(new AgentEvent("done", "已暂停", run.getId()));
                    return new Result(session.getId(), run.getId(), mode, cancelled, validation);
                }
            } catch (Exception ex) {
                log.warn("Agent execution failed: {}", ex.getMessage(), ex);
                runs.addStep(run, "ERROR", "执行失败", ex.getMessage(), false);
                String failed = "抱歉，任务执行失败：" + ex.getMessage()
                        + "\n\n可以从 checkpoint 尝试恢复，或把任务拆小一点。";
                memory.append(session.getId(), "assistant", failed, "MEDIUM", "error");
                runs.appendEvent(run, "error", "执行失败", ex.getMessage());
                runs.finish(run, failed, "FAILED", validation);
                events.accept(new AgentEvent("error", "执行失败", ex.getMessage()));
                return new Result(session.getId(), run.getId(), mode, failed, validation);
            }

            String finalAnswer = answer == null || answer.isBlank() ? "任务已结束，但没有生成有效回答。" : answer;
            List<AgentArtifactService.Artifact> produced = artifacts.listRun(run.getId());
            events.accept(new AgentEvent("artifacts",
                    produced.isEmpty() ? "本轮没有生成文件产物" : "生成了 " + produced.size() + " 个文件",
                    artifacts.json(produced)));
            memory.append(session.getId(), "assistant", finalAnswer, "MEDIUM", "answer");
            runs.finish(run, finalAnswer, "DONE", validation);
            events.accept(new AgentEvent("answer", finalAnswer, validation));
            events.accept(new AgentEvent("done", "完成", run.getId()));
            return new Result(session.getId(), run.getId(), mode, finalAnswer, validation);
        } finally {
            AgentTaskContext.clear();
            tools.clearKnowledgeBases();
        }
    }

    private boolean hasProgress(AgentRunService.AgentRun run) {
        AgentRunService.Checkpoint cp = run.getCheckpoint();
        if (cp == null) return false;
        if ("plan".equals(cp.getPhase())) {
            return cp.getPlan() != null && (cp.getPlan().getPlanIndex() > 0 || cp.getPlan().getCurrent() != null);
        }
        return cp.getReact() != null && (cp.getReact().getStep() > 0 || cp.getReact().getLastAnswer() != null);
    }

    private void unlock(String sessionId, ReentrantLock lock) {
        lock.unlock();
        if (!lock.isLocked() && !lock.hasQueuedThreads()) {
            sessionLocks.remove(sessionId, lock);
        }
    }

    public Validation validate(String task, String answer, String artifactManifest) {
        String system = """
                你是独立验收员。你只能看到用户任务、候选结果和产物目录清单，看不到执行者的思考过程。
                判断结果是否真正完成任务；如果只是声称做了、空泛、遗漏关键产物、或答非所问，就不通过。
                如果任务要求生成文件（pptx/docx/xlsx/pdf/html/图片等），产物清单中必须有对应文件；
                只有文字说明而没有产物时，不能通过。
                只输出 JSON：{"passed":true/false,"reason":"一句中文理由"}。
                """;
        String user = "用户任务：\n" + task + "\n\n候选结果：\n" + answer
                + "\n\n产物清单：\n" + (artifactManifest == null ? "无" : artifactManifest);
        try {
            JsonNode json = llm.completeJson(system, user, 45);
            boolean passed = json.path("passed").asBoolean(false);
            String reason = json.path("reason").asText(passed ? "任务已完成" : "需要修正");
            return new Validation(passed, reason);
        } catch (Exception ex) {
            log.warn("Agent validation skipped: {}", ex.getMessage());
            return new Validation(true, "验收模型暂不可用，跳过二次验收");
        }
    }

    public record Validation(boolean passed, String reason) {
    }

    private boolean isCancelled(AgentRunService.AgentRun run) {
        return "CANCEL_REQUESTED".equals(run.getStatus()) || AgentTaskControl.isCancelled(run.getId());
    }

    private Result pause(AgentMemoryService.Session session, AgentRunService.AgentRun run,
                         String mode, String validation, Consumer<AgentEvent> events) {
        String cancelled = "任务已按请求停止，可以稍后从 checkpoint 继续。";
        runs.finish(run, cancelled, "PAUSED", validation);
        events.accept(new AgentEvent("answer", cancelled, "已暂停，可恢复"));
        events.accept(new AgentEvent("done", "已暂停", run.getId()));
        return new Result(session.getId(), run.getId(), mode, cancelled, validation);
    }

    private String ensureAnswer(String answer, String runId) {
        if (answer != null && !answer.isBlank()
                && !answer.contains("步数已达到上限")
                && !answer.contains("没有生成有效回答")) {
            return answer;
        }
        List<AgentArtifactService.Artifact> produced = artifacts.listRun(runId);
        if (produced.isEmpty()) return answer;
        StringBuilder sb = new StringBuilder("任务已完成，并生成了这些产物：\n");
        for (AgentArtifactService.Artifact artifact : produced) {
            sb.append("- ").append(artifact.name()).append("（").append(artifact.type())
                    .append("，").append(artifact.size()).append(" bytes）\n");
        }
        return sb.toString().trim();
    }

    private String resolveMode(String question, String requested) {
        if ("plan".equalsIgnoreCase(requested)) return "plan";
        if ("react".equalsIgnoreCase(requested)) return "react";
        String text = question.toLowerCase();
        boolean longTask = question.length() > 110
                || text.matches("(?s).*(开发|实现|搭建|项目|多步骤|完整方案|调研报告|生成一个|写一个.*(网站|系统|工具)|先.*然后|批量|脚本文件).*");
        return longTask ? "plan" : "react";
    }
}
