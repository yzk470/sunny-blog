package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public record Result(String sessionId, String runId, String mode, String answer, String validation) {
    }

    public Result chat(String sessionId, String question, String requestedMode,
                       Consumer<AgentEvent> events) {
        if (!llm.isConfigured()) {
            throw new IllegalStateException("Agent 模型未配置，请设置 DEEPSEEK_API_KEY");
        }
        if (question == null || question.isBlank()) throw new IllegalArgumentException("问题不能为空");
        AgentMemoryService.Session session = memory.getOrCreate(sessionId);
        String mode = resolveMode(question, requestedMode);
        AgentRunService.AgentRun run = runs.create(session.getId(), question, mode);
        events.accept(new AgentEvent("session", session.getId(), run.getId()));
        events.accept(new AgentEvent("status", "正在读取记忆与技能…", ""));

        String memoryContext = memory.buildPromptContext(session.getId(), 7000);
        memory.append(session.getId(), "user", question, memory.classify(question), "question");

        String answer;
        String validation = "";
        try {
            if ("plan".equals(mode)) {
                events.accept(new AgentEvent("status", "识别为长任务，进入 plan-and-execute…", ""));
                answer = planExecute.run(question, memoryContext, run, events);
                validation = "独立验收已包含在计划执行流程中";
            } else {
                events.accept(new AgentEvent("status", "识别为短任务，进入 ReAct 循环…", ""));
                answer = react.run(question, memoryContext, run, events, 8);
                Validation result = validate(question, answer);
                validation = result.reason;
                runs.addStep(run, "VALIDATION", result.passed ? "独立验收通过" : "独立验收未通过",
                        result.reason, result.passed);
                events.accept(new AgentEvent("validation",
                        result.passed ? "独立验收通过" : "独立验收要求修改", result.reason));
                if (!result.passed) {
                    String revised = react.run("""
                            用户任务：%s

                            你上一版回答：
                            %s

                            独立验收意见：
                            %s

                            请不要辩解，直接修正并重新完成任务。
                            """.formatted(question, answer, result.reason),
                            memoryContext, run, events, 6);
                    Validation second = validate(question, revised);
                    validation = second.reason;
                    runs.addStep(run, "VALIDATION", second.passed ? "复审通过" : "复审未通过",
                            second.reason, second.passed);
                    events.accept(new AgentEvent("validation",
                            second.passed ? "复审通过" : "复审仍有问题", second.reason));
                    answer = revised;
                }
            }
        } catch (Exception ex) {
            log.warn("Agent execution failed: {}", ex.getMessage(), ex);
            runs.addStep(run, "ERROR", "执行失败", ex.getMessage(), false);
            String failed = "抱歉，任务执行失败：" + ex.getMessage()
                    + "\n\n你可以把任务拆小一点，或补充更明确的输入后重试。";
            memory.append(session.getId(), "assistant", failed, "MEDIUM", "error");
            runs.finish(run, failed, "FAILED", validation);
            events.accept(new AgentEvent("error", "执行失败", ex.getMessage()));
            return new Result(session.getId(), run.getId(), mode, failed, validation);
        }

        String finalAnswer = answer == null || answer.isBlank() ? "任务已结束，但没有生成有效回答。" : answer;
        memory.append(session.getId(), "assistant", finalAnswer, "MEDIUM", "answer");
        runs.finish(run, finalAnswer, "DONE", validation);
        events.accept(new AgentEvent("answer", finalAnswer, validation));
        events.accept(new AgentEvent("done", "完成", run.getId()));
        return new Result(session.getId(), run.getId(), mode, finalAnswer, validation);
    }

    /**
     * A separate validator call. It receives only the task and candidate answer,
     * deliberately excluding the executor's thoughts and tool trace.
     */
    public Validation validate(String task, String answer) {
        String system = """
                你是独立验收员。你只能看到用户任务和候选结果，看不到执行者的思考过程。
                判断结果是否真正完成任务；如果只是声称做了、空泛、遗漏关键产物、或答非所问，就不通过。
                只输出 JSON：{"passed":true/false,"reason":"一句中文理由"}。
                """;
        String user = "用户任务：\n" + task + "\n\n候选结果：\n" + answer;
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

    private String resolveMode(String question, String requested) {
        if ("plan".equalsIgnoreCase(requested)) return "plan";
        if ("react".equalsIgnoreCase(requested)) return "react";
        String text = question.toLowerCase();
        boolean longTask = question.length() > 110
                || text.matches("(?s).*(开发|实现|搭建|项目|多步骤|完整方案|调研报告|生成一个|写一个.*(网站|系统|工具)|先.*然后|批量|脚本文件).*");
        return longTask ? "plan" : "react";
    }
}
