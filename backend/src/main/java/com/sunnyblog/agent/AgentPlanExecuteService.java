package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Long tasks use a LangGraph4j state graph:
 * plan -> execute all steps -> independent validate -> revise (at most once).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPlanExecuteService {

    private final AgentLlmService llm;
    private final AgentReActService react;
    private final AgentRunService runs;
    private final AgentProperties properties;

    public String run(String task, String memoryContext, AgentRunService.AgentRun run,
                      Consumer<AgentEvent> events) {
        try {
            Map<String, Channel<?>> channels = new HashMap<>();
            channels.put("task", Channels.base(() -> ""));
            channels.put("plan", Channels.base(ArrayList::new));
            channels.put("answer", Channels.base(() -> ""));
            channels.put("stepResults", Channels.base(() -> ""));
            channels.put("passed", Channels.base(() -> false));
            channels.put("validation", Channels.base(() -> ""));
            channels.put("revision", Channels.base(() -> 0));
            StateGraph<AgentState> graph = new StateGraph<>(channels, AgentState::new);
            AsyncNodeAction<AgentState> planAction = state ->
                    CompletableFuture.completedFuture(planNode(task, memoryContext, run, events));
            graph.addNode("plan", planAction);
            AsyncNodeAction<AgentState> executeAction = state -> {
                List<String> plan = stringList(state.data().get("plan"));
                StringBuilder results = new StringBuilder();
                String answer = "";
                for (int i = 0; i < plan.size(); i++) {
                    String step = plan.get(i);
                    events.accept(new AgentEvent("plan_step", "执行计划 " + (i + 1) + "/" + plan.size(), step));
                    runs.addStep(run, "PLAN_STEP", "计划步骤 " + (i + 1), step, true);
                    String stepTask = """
                            总任务：%s

                            当前步骤（%d/%d）：%s

                            只完成当前步骤，已完成的步骤结果如下：
                            %s
                            """.formatted(task, i + 1, plan.size(), step,
                            results.length() == 0 ? "（无）" : results.toString());
                    answer = react.run(stepTask, memoryContext, run, events, 6);
                    results.append("\n[").append(i + 1).append("] ").append(step)
                            .append("\n结果：").append(answer).append("\n");
                }
                return CompletableFuture.completedFuture(Map.<String, Object>of(
                        "answer", answer,
                        "stepResults", results.toString()
                ));
            };
            graph.addNode("execute", executeAction);
            AsyncNodeAction<AgentState> validateAction = state -> {
                String answer = state.value("answer", "");
                Validation validation = validate(task, answer);
                runs.addStep(run, "VALIDATION", validation.passed ? "验收通过" : "验收未通过",
                        validation.reason, validation.passed);
                events.accept(new AgentEvent("validation",
                        validation.passed ? "独立验收通过" : "独立验收要求修改", validation.reason));
                return CompletableFuture.completedFuture(Map.<String, Object>of(
                        "passed", validation.passed,
                        "validation", validation.reason
                ));
            };
            graph.addNode("validate", validateAction);
            AsyncNodeAction<AgentState> reviseAction = state -> {
                int revision = (int) state.data().getOrDefault("revision", 0);
                String feedback = state.value("validation", "");
                String revised = react.run("""
                        总任务：%s

                        上一版结果：
                        %s

                        独立验收意见：
                        %s

                        请根据验收意见重新检查并修正结果。
                        """.formatted(task, state.value("answer", ""), feedback),
                        memoryContext, run, events, 6);
                return CompletableFuture.completedFuture(Map.<String, Object>of(
                        "answer", revised,
                        "revision", revision + 1
                ));
            };
            graph.addNode("revise", reviseAction);
            AsyncNodeAction<AgentState> finishAction = state ->
                    CompletableFuture.completedFuture(Map.<String, Object>of("answer", state.value("answer", "")));
            graph.addNode("finish", finishAction);

            graph.addEdge(StateGraph.START, "plan");
            graph.addEdge("plan", "execute");
            graph.addEdge("execute", "validate");
            AsyncEdgeAction<AgentState> route = state -> {
                boolean passed = state.value("passed", false);
                int revision = (int) state.data().getOrDefault("revision", 0);
                return CompletableFuture.completedFuture(passed || revision >= 1 ? "finish" : "revise");
            };
            graph.addConditionalEdges("validate", route, Map.of("finish", "finish", "revise", "revise"));
            graph.addEdge("revise", "validate");
            graph.addEdge("finish", StateGraph.END);

            CompiledGraph<AgentState> compiled = graph.compile();
            compiled.setMaxIterations(20);
            AgentState finalState = compiled.invoke(Map.of("task", task)).orElseThrow();
            return finalState.value("answer", "计划执行完成，但没有生成结果。");
        } catch (Exception ex) {
            log.warn("Plan-and-execute failed: {}", ex.getMessage(), ex);
            runs.addStep(run, "ERROR", "计划执行失败", ex.getMessage(), false);
            return "长任务执行失败：" + ex.getMessage();
        }
    }

    private Map<String, Object> planNode(String task, String memoryContext,
                                         AgentRunService.AgentRun run, Consumer<AgentEvent> events) {
        events.accept(new AgentEvent("status", "长任务：正在制定执行计划…", ""));
        String system = """
                你是任务规划器。把一个长任务拆成 2～6 个可执行步骤。
                每一步都应当有明确产物，优先使用 agent 的文件/脚本/skill 工具。
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

    private Validation validate(String task, String answer) {
        String system = """
                你是独立验收员，只能看到任务和候选结果，不能看到执行者的思考或工具记录。
                判断候选结果是否真正完成用户任务，是否存在空泛、遗漏、未执行却声称执行等问题。
                只输出 JSON：{"passed":true/false,"reason":"一句中文理由"}。
                """;
        String user = "任务：\n" + task + "\n\n候选结果：\n" + answer;
        try {
            JsonNode json = llm.completeJson(system, user, 45);
            boolean passed = json.path("passed").asBoolean(false);
            String reason = json.path("reason").asText(passed ? "任务已完成" : "需要补充或修正");
            return new Validation(passed, reason);
        } catch (Exception ex) {
            return new Validation(true, "验收模型不可用，跳过二次验收");
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private record Validation(boolean passed, String reason) {
    }
}
