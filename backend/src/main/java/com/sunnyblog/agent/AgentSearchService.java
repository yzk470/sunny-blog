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
