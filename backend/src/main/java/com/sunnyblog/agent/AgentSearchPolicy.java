package com.sunnyblog.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Search is deliberately gated. Most tasks use the cheap linear attempt loop;
 * only expensive, multi-step, strongly verifiable tasks get best-first search
 * and Best-of-N plan sampling.
 */
@Service
@RequiredArgsConstructor
public class AgentSearchPolicy {

    private static final Pattern HIGH_VALUE = Pattern.compile(
            "(?is).*(架构|审计|完整方案|端到端|高价值|关键任务|复杂|生产环境|迁移|安全评估|性能优化|系统设计|调研报告|完整项目|多步骤|多个文件|全流程).*");

    private final AgentProperties properties;

    public record Decision(boolean highValue, int sampleCount, int maxBranches,
                           int taskBudget, String reason) {
    }

    public Decision decide(String question, String mode) {
        boolean plan = "plan".equals(mode);
        boolean keyword = question != null && HIGH_VALUE.matcher(question).matches();
        boolean longPlan = plan && question != null && question.length() > 120;
        boolean high = properties.isHighValueSearchEnabled() && (keyword || longPlan);
        int budget = high ? properties.getHighValueTaskBudget() : (plan ? 60 : 36);
        int samples = high ? Math.max(2, properties.getHighValueSampleCount()) : 3;
        int branches = high ? Math.max(1, properties.getHighValueMaxBranches()) : 3;
        String reason = high
                ? (keyword ? "命中高价值/复杂任务关键词" : "长任务且需要显式规划")
                : "普通任务，使用线性尝试";
        return new Decision(high, samples, branches, budget, reason);
    }

    public int attemptBudget(String mode, int remaining, boolean highValue) {
        int max = highValue ? ("plan".equals(mode) ? 32 : 14)
                : ("plan".equals(mode) ? 24 : 12);
        return Math.max(2, Math.min(max, remaining));
    }
}
