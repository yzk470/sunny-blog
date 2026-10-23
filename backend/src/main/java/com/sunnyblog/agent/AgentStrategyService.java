package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Produces a small ranked strategy portfolio. The agent is expected to fail at
 * least once, so a task must not depend on a single approach.
 */
@Service
@RequiredArgsConstructor
public class AgentStrategyService {

    private final AgentLlmService llm;

    public record StrategyPlan(String successCriteria, List<AgentRunService.Strategy> strategies, int taskBudget) {
    }

    public StrategyPlan plan(String task, String memoryContext, String mode) {
        int budget = (mode != null && "plan".equals(mode)) || task.length() > 120 ? 60 : 36;
        List<AgentRunService.Strategy> strategies = new ArrayList<>();
        if (llm.isConfigured()) {
            String system = """
                    你是 Agent 策略规划器。失败是常态，不要假设第一条路线一定成功。
                    为一个任务设计 2～3 条真正不同、可执行的候选路线，按成功概率排序。
                    每条路线说明：方法、主要风险、失败后的备选手段。
                    路线之间不能只是换个文件名或重复同一动作。
                    只输出 JSON：
                    {"successCriteria":"怎样才算真正完成","strategies":[
                      {"title":"路线名","approach":"具体做法","risk":"最大风险","fallback":"失败后怎么换"}
                    ]}
                    """;
            String user = "历史记忆：\n" + memoryContext + "\n\n任务：\n" + task;
            try {
                JsonNode json = llm.completeJson(system, user, 60);
                JsonNode array = json.path("strategies");
                if (array.isArray()) {
                    for (JsonNode node : array) {
                        String title = node.path("title").asText("").trim();
                        String approach = node.path("approach").asText("").trim();
                        if (title.isBlank() || approach.isBlank()) continue;
                        AgentRunService.Strategy strategy = new AgentRunService.Strategy();
                        strategy.setId("s" + (strategies.size() + 1));
                        strategy.setTitle(title);
                        strategy.setApproach(approach);
                        strategy.setRisk(node.path("risk").asText(""));
                        strategy.setFallback(node.path("fallback").asText(""));
                        strategies.add(strategy);
                        if (strategies.size() >= 3) break;
                    }
                }
                String criteria = json.path("successCriteria").asText("");
                if (strategies.isEmpty()) strategies = fallback(task);
                if (criteria.isBlank()) criteria = defaultCriteria(task);
                List<AgentRunService.Strategy> distinct = distinct(strategies);
                if (distinct.size() < 2) {
                    for (AgentRunService.Strategy extra : fallback(task)) {
                        if (distinct.size() >= 2) break;
                        if (distinct.stream().noneMatch(s -> s.getTitle().equals(extra.getTitle()))) {
                            extra.setId("s" + (distinct.size() + 1));
                            distinct.add(extra);
                        }
                    }
                }
                return new StrategyPlan(criteria, distinct, budget);
            } catch (Exception ignored) {
                // Fall through to deterministic strategies.
            }
        }
        return new StrategyPlan(defaultCriteria(task), fallback(task), budget);
    }

    private List<AgentRunService.Strategy> fallback(String task) {
        List<AgentRunService.Strategy> strategies = new ArrayList<>();
        strategies.add(strategy("s1", "直接工具路线", "直接使用脚本和现有工具完成：先写最小实现，再真实执行验证。",
                "依赖缺失或路径错误", "改用另一条实现路线"));
        strategies.add(strategy("s2", "拆分降级路线", "把任务拆成更小的可验证步骤，先交付最小可用结果，再逐步扩展。",
                "步骤过多导致收敛慢", "保留最小可用结果，说明未完成部分"));
        strategies.add(strategy("s3", "替代实现路线", "换一种工具或数据格式实现目标，例如 HTML/转换/纯文本替代。",
                "替代路线能力不足", "返回已获得的真实结果并请求用户选择"));
        return strategies;
    }

    private AgentRunService.Strategy strategy(String id, String title, String approach, String risk, String fallback) {
        AgentRunService.Strategy strategy = new AgentRunService.Strategy();
        strategy.setId(id);
        strategy.setTitle(title);
        strategy.setApproach(approach);
        strategy.setRisk(risk);
        strategy.setFallback(fallback);
        return strategy;
    }

    private List<AgentRunService.Strategy> distinct(List<AgentRunService.Strategy> strategies) {
        List<AgentRunService.Strategy> result = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        for (AgentRunService.Strategy strategy : strategies) {
            if (titles.add(strategy.getTitle().toLowerCase())) {
                strategy.setId("s" + (result.size() + 1));
                result.add(strategy);
            }
        }
        return result.isEmpty() ? fallback("") : result;
    }

    private String defaultCriteria(String task) {
        return "产物真实存在且可读取；执行类任务有真实命令输出；回答直接解决用户任务，而不是只声称完成。";
    }
}
