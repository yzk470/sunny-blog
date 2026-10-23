package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A small judge that ranks candidate strategies before spending budget.
 * This is the "best-first" part of the simplified LATS search.
 */
@Service
@RequiredArgsConstructor
public class AgentSearchService {

    private final AgentLlmService llm;

    public record Scored(AgentRunService.Strategy strategy, int score) {
    }

    public List<Scored> rank(String task, List<AgentRunService.Strategy> strategies, String memoryContext) {
        List<Scored> result = new ArrayList<>();
        if (strategies.size() <= 1) {
            for (AgentRunService.Strategy strategy : strategies) result.add(new Scored(strategy, 50));
            return result;
        }
        if (llm.isConfigured()) {
            try {
                String system = """
                        你是搜索调度器。给每条候选路线打 0～100 分，预测它在当前任务上的成功率与成本。
                        评分考虑：可行性、验证难度、依赖风险、失败后是否容易回退。
                        不要执行任务，只评分。只输出 JSON：
                        {"scores":[{"id":"s1","score":88,"reason":"一句理由"}]}
                        """;
                String user = "任务：\n" + task + "\n\n历史：\n" + memoryContext + "\n\n候选：\n"
                        + render(strategies);
                JsonNode json = llm.completeJson(system, user, 40);
                JsonNode array = json.path("scores");
                List<Scored> scored = new ArrayList<>();
                if (array.isArray()) {
                    for (JsonNode node : array) {
                        String id = node.path("id").asText("");
                        int score = Math.max(0, Math.min(100, node.path("score").asInt(50)));
                        for (AgentRunService.Strategy strategy : strategies) {
                            if (strategy.getId().equals(id)) scored.add(new Scored(strategy, score));
                        }
                    }
                }
                if (scored.size() == strategies.size()) {
                    scored.sort(Comparator.comparingInt(Scored::score).reversed());
                    return scored;
                }
            } catch (Exception ignored) {
                // Deterministic fallback below.
            }
        }
        for (AgentRunService.Strategy strategy : strategies) {
            int score = 50;
            String approach = strategy.getApproach() == null ? "" : strategy.getApproach();
            if (approach.contains("直接") || approach.contains("最小")) score += 10;
            if (approach.contains("依赖") || approach.contains("转换")) score -= 5;
            if (strategy.getRisk() != null && strategy.getRisk().length() > 30) score -= 5;
            result.add(new Scored(strategy, Math.max(1, Math.min(95, score))));
        }
        result.sort(Comparator.comparingInt(Scored::score).reversed());
        return result;
    }

    /** Generate repair branches from the structured reflection, not from a fixed menu. */
    public List<AgentRunService.Strategy> expand(String task, AgentRunService.Strategy failed,
                                                 AgentFailureService.Reflection reflection,
                                                 int existingCount, int maxChildren) {
        List<AgentRunService.Strategy> children = new ArrayList<>();
        if (reflection == null || reflection.repairOperators() == null) return children;
        for (String operator : reflection.repairOperators()) {
            if (children.size() >= Math.max(1, maxChildren)) break;
            if (operator == null || operator.isBlank()) continue;
            String type = operator;
            String description = operator;
            int colon = operator.indexOf(':');
            if (colon > 0) {
                type = operator.substring(0, colon).trim();
                description = operator.substring(colon + 1).trim();
            }
            AgentRunService.Strategy strategy = new AgentRunService.Strategy();
            strategy.setId("s" + (existingCount + children.size() + 1));
            strategy.setTitle("修复分支：" + shortTitle(description, type));
            strategy.setApproach("基于失败反思生成（" + type + "）：" + description
                    + "。必须遵守全局约束：" + safe(reflection.globalConstraint()));
            strategy.setRisk("修复方向可能仍然不成立，需要预检");
            strategy.setFallback("保留已获得的部分结果，并换下一条分支");
            children.add(strategy);
        }
        return children;
    }

    /** Re-score pending branches with the failure evidence and global constraints. */
    public List<Scored> rescore(String task, List<AgentRunService.Strategy> pending,
                                AgentFailureService.Reflection reflection) {
        if (pending.isEmpty()) return List.of();
        List<Scored> base = rank(task, pending, "失败反思：" + safe(reflection.summary())
                + "\n全局约束：" + safe(reflection.globalConstraint()));
        List<Scored> adjusted = new ArrayList<>();
        for (Scored scored : base) {
            int score = scored.score();
            String text = scored.strategy().getTitle() + " " + scored.strategy().getApproach();
            if (reflection.invalidatesStrategyIds() != null
                    && reflection.invalidatesStrategyIds().contains(scored.strategy().getId())) {
                score = 0;
            }
            if (reflection.globalConstraint() != null && !reflection.globalConstraint().isBlank()) {
                score += 2;
            }
            if (text.contains("修复")) score += 8;
            adjusted.add(new Scored(scored.strategy(), Math.max(0, Math.min(100, score))));
        }
        adjusted.sort(Comparator.comparingInt(Scored::score).reversed());
        return adjusted;
    }

    private String shortTitle(String description, String type) {
        String value = description == null ? type : description;
        return value.length() <= 28 ? value : value.substring(0, 28) + "…";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String render(List<AgentRunService.Strategy> strategies) {
        StringBuilder sb = new StringBuilder();
        for (AgentRunService.Strategy strategy : strategies) {
            sb.append(strategy.getId()).append(" | ").append(strategy.getTitle())
                    .append(" | ").append(strategy.getApproach())
                    .append(" | risk: ").append(strategy.getRisk()).append("\n");
        }
        return sb.toString();
    }
}
