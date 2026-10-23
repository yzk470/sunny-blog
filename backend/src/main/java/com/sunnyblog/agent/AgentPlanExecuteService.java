package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plan-and-execute with a durable checkpoint after every plan step.
 * The graph is intentionally a small explicit state machine so it can resume
 * from the exact plan index after a disconnect or server restart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPlanExecuteService {

    private final AgentLlmService llm;
    private final AgentReActService react;
    private final AgentRunService runs;
    private final AgentArtifactService artifacts;

    public String run(String task, String memoryContext, AgentRunService.AgentRun run,
                      java.util.function.Consumer<AgentEvent> events) {
        AgentRunService.Checkpoint checkpoint = run.getCheckpoint();
        if (checkpoint == null) {
            checkpoint = new AgentRunService.Checkpoint();
            run.setCheckpoint(checkpoint);
        }
        AgentRunService.PlanState plan = checkpoint.getPlan();
        if (plan == null) {
            plan = new AgentRunService.PlanState();
            checkpoint.setPlan(plan);
        }
        if (plan.isPaused()) plan.setPaused(false);
        checkpoint.setPhase("plan");

        if (plan.getPlan().isEmpty()) {
            Map<String, Object> planned = planNode(task, memoryContext, run, events);
            @SuppressWarnings("unchecked")
            List<String> steps = (List<String>) planned.get("plan");
            plan.setPlan(new ArrayList<>(steps));
            plan.setPlanIndex(0);
            runs.checkpoint(run);
        }

        if (plan.isRevisionInProgress() && plan.getCurrent() != null) {
            String revised = react.run("""
                    总任务：%s

                    上一版结果：
                    %s

                    独立验收意见：
                    %s

                    请根据验收意见重新检查并修正结果。
                    """.formatted(task, plan.getAnswer(), plan.getValidation()),
                    memoryContext, run, plan.getCurrent(), events, 8);
            if ("CANCEL_REQUESTED".equals(run.getStatus()) || AgentTaskControl.isCancelled(run.getId())) {
                plan.setPaused(true);
                runs.checkpoint(run);
                return "任务已按请求暂停，当前计划进度已写入 checkpoint。";
            }
            plan.setCurrent(null);
            plan.setAnswer(revised);
            plan.setRevisionInProgress(false);
            runs.checkpoint(run);
            Validation second = validate(task, revised, artifacts.manifestRun(run.getId()));
            plan.setValidation(second.reason);
            plan.setPassed(second.passed);
            runs.addStep(run, "VALIDATION", second.passed ? "复审通过" : "复审未通过",
                    second.reason, second.passed);
            events.accept(new AgentEvent("validation",
                    second.passed ? "复审通过" : "复审仍有问题", second.reason));
            runs.checkpoint(run);
            return plan.getAnswer();
        }

        for (int i = plan.getPlanIndex(); i < plan.getPlan().size(); i++) {
            if ("CANCEL_REQUESTED".equals(run.getStatus()) || AgentTaskControl.isCancelled(run.getId())) {
                String paused = "任务已按请求暂停，当前计划进度已写入 checkpoint。";
                plan.setPaused(true);
                runs.checkpoint(run);
                return paused;
            }
            String step = plan.getPlan().get(i);
            if (plan.getCurrent() == null) {
                plan.setCurrent(new AgentRunService.ReActState());
            }
            plan.setPlanIndex(i);
            runs.checkpoint(run);
            events.accept(new AgentEvent("plan_step", "执行计划 " + (i + 1) + "/" + plan.getPlan().size(), step));
            runs.addStep(run, "PLAN_STEP", "计划步骤 " + (i + 1), step, true);
            String stepTask = """
                    总任务：%s

                    当前步骤（%d/%d）：%s

                    只完成当前步骤，已完成的步骤结果如下：
                    %s
                    """.formatted(task, i + 1, plan.getPlan().size(), step,
                    plan.getResults().isBlank() ? "（无）" : plan.getResults());
            String answer = react.run(stepTask, memoryContext, run, plan.getCurrent(), events, 8);
            if ("CANCEL_REQUESTED".equals(run.getStatus()) || AgentTaskControl.isCancelled(run.getId())) {
                plan.setPaused(true);
                runs.checkpoint(run);
                return "任务已按请求暂停，当前计划进度已写入 checkpoint。";
            }
            plan.setCurrent(null);
            plan.setAnswer(answer);
            plan.setResults(plan.getResults() + "\n[" + (i + 1) + "] " + step + "\n结果：" + answer + "\n");
            plan.setPlanIndex(i + 1);
            runs.checkpoint(run);
        }

        if (!plan.isPassed()) {
            Validation validation = validate(task, plan.getAnswer(), artifacts.manifestRun(run.getId()));
            plan.setValidation(validation.reason);
            plan.setPassed(validation.passed);
            runs.addStep(run, "VALIDATION", validation.passed ? "验收通过" : "验收未通过",
                    validation.reason, validation.passed);
            events.accept(new AgentEvent("validation",
                    validation.passed ? "独立验收通过" : "独立验收要求修改", validation.reason));
            runs.checkpoint(run);
            if (!validation.passed && plan.getRevision() < 1) {
                plan.setRevision(plan.getRevision() + 1);
                plan.setRevisionInProgress(true);
                plan.setCurrent(new AgentRunService.ReActState());
                runs.checkpoint(run);
                String revised = react.run("""
                        总任务：%s

                        上一版结果：
                        %s

                        独立验收意见：
                        %s

                        请根据验收意见重新检查并修正结果。
                        """.formatted(task, plan.getAnswer(), validation.reason),
                        memoryContext, run, plan.getCurrent(), events, 8);
                if ("CANCEL_REQUESTED".equals(run.getStatus()) || AgentTaskControl.isCancelled(run.getId())) {
                    plan.setPaused(true);
                    runs.checkpoint(run);
                    return "任务已按请求暂停，当前计划进度已写入 checkpoint。";
                }
                plan.setCurrent(null);
                plan.setRevisionInProgress(false);
                plan.setAnswer(revised);
                Validation second = validate(task, revised, artifacts.manifestRun(run.getId()));
                plan.setValidation(second.reason);
                plan.setPassed(second.passed);
                runs.addStep(run, "VALIDATION", second.passed ? "复审通过" : "复审未通过",
                        second.reason, second.passed);
                events.accept(new AgentEvent("validation",
                        second.passed ? "复审通过" : "复审仍有问题", second.reason));
                runs.checkpoint(run);
            }
        }
        return plan.getAnswer().isBlank() ? "计划执行完成，但没有生成结果。" : plan.getAnswer();
    }

    private Map<String, Object> planNode(String task, String memoryContext,
                                         AgentRunService.AgentRun run,
                                         java.util.function.Consumer<AgentEvent> events) {
        events.accept(new AgentEvent("status", "长任务：正在制定执行计划…", ""));
        String system = """
                你是任务规划器。把一个长任务拆成 2～6 个可执行步骤。
                每一步都应当有明确产物，优先使用 agent 的文件/脚本/skill 工具。
                所有路径必须相对于 workspace/skills/artifacts；脚本写在 workspace/scripts 下。
                不要规划 shell 命令或管道；请把命令逻辑写成脚本，再用 run_script 执行。
                用户要 PPT/Word/Excel/PDF/HTML/图片时，最后必须有真实产物文件写入 artifacts。
                不允许规划 /tmp、绝对路径或直接访问系统文件。
                只输出 JSON：{"steps":["步骤1","步骤2"],"reason":"一句理由"}。
                """;
        String user = "历史记忆：\n" + memoryContext + "\n\n任务：\n" + task;
        JsonNode json = llm.completeJson(system, user, 60);
        List<String> steps = new ArrayList<>();
        JsonNode array = json.path("steps");
        if (array.isArray()) {
            for (JsonNode node : array) {
                String step = node.asText("").trim();
                if (!step.isBlank()) steps.add(step);
            }
        }
        if (steps.isEmpty()) steps.add(task);
        if (steps.size() > 6) steps = new ArrayList<>(steps.subList(0, 6));
        runs.addStep(run, "PLAN", "执行计划", String.join("\n", steps.stream().map(s -> "- " + s).toList()), true);
        events.accept(new AgentEvent("plan", "执行计划", String.join("\n", steps)));
        return Map.of("plan", steps);
    }

    private Validation validate(String task, String answer, String artifactManifest) {
        String system = """
                你是独立验收员，只能看到任务、候选结果和产物清单，不能看到执行者的思考或工具记录。
                判断候选结果是否真正完成用户任务；如果只是声称做了、空泛、遗漏关键产物、或答非所问，就不通过。
                如果任务要求生成文件，产物清单中必须有对应文件；只有文字说明时不能通过。
                只输出 JSON：{"passed":true/false,"reason":"一句中文理由"}。
                """;
        String user = "任务：\n" + task + "\n\n候选结果：\n" + answer
                + "\n\n产物清单：\n" + artifactManifest;
        try {
            JsonNode json = llm.completeJson(system, user, 45);
            boolean passed = json.path("passed").asBoolean(false);
            String reason = json.path("reason").asText(passed ? "任务已完成" : "需要补充或修正");
            return new Validation(passed, reason);
        } catch (Exception ex) {
            return new Validation(true, "验收模型不可用，跳过二次验收");
        }
    }

    private record Validation(boolean passed, String reason) {
    }
}
