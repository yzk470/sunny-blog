package com.sunnyblog.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Classifies why an attempt failed and writes one short lesson for the next
 * attempt. The full tool trace stays in the run; only the lesson is replayed.
 */
@Service
@RequiredArgsConstructor
public class AgentFailureService {

    private final AgentLlmService llm;

    public String classify(String answer, String failureOutput) {
        String text = (answer == null ? "" : answer) + "\n" + (failureOutput == null ? "" : failureOutput);
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("module not found") || lower.contains("modulenotfounderror")
                || lower.contains("no such file") || lower.contains("not found")
                || lower.contains("permission") || lower.contains("invalid agent directory")
                || lower.contains("command not found") || lower.contains("exit=127")
                || lower.contains("exit=64") || lower.contains("缺少")) {
            return "ENVIRONMENT";
        }
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("network")
                || lower.contains("502") || lower.contains("503") || lower.contains("rate limit")) {
            return "TRANSIENT";
        }
        if (lower.contains("json") || lower.contains("格式") || lower.contains("parse")) {
            return "FORMAT";
        }
        if (lower.contains("步数") || lower.contains("预算") || lower.contains("budget")) {
            return "BUDGET";
        }
        if (lower.contains("无法") || lower.contains("不支持") || lower.contains("不可用")) {
            return "UNSAT";
        }
        if (lower.contains("error") || lower.contains("失败")) {
            return "TOOL";
        }
        return "LOGIC";
    }

    public String reflect(String task, AgentRunService.AttemptRecord attempt,
                          String failureType, String failureReason) {
        String fallback = switch (failureType) {
            case "ENVIRONMENT" -> "这次失败是环境/依赖问题；下一次先预检路径、依赖和权限，再换实现路线。";
            case "TRANSIENT" -> "这次失败可能是瞬时故障；下一次先重试一次，仍失败再换路线。";
            case "FORMAT" -> "这次失败是输出格式问题；下一次严格按要求格式输出。";
            case "BUDGET" -> "这次失败是预算耗尽；下一次缩小范围，先交付最小可用结果。";
            case "UNSAT" -> "当前路线能力不足；下一次改用替代工具或明确说明限制。";
            case "TOOL" -> "这次工具链失败；下一次避免重复相同动作，先读取错误再换参数或工具。";
            default -> "这次路线假设不成立；下一次换一条不同思路，并先验证最小步骤。";
        };
        if (!llm.isConfigured()) return fallback;
        try {
            String system = """
                    你是失败复盘器。只输出一到两句中文经验，供下一次尝试使用。
                    必须指出：这次哪个假设/动作失败，下一次具体不要重复什么、应该换什么。
                    不要复述整段日志，不要输出工具调用。
                    """;
            String user = "任务：\n" + task
                    + "\n\n失败路线：" + attempt.getStrategyTitle() + " / " + attempt.getApproach()
                    + "\n失败类型：" + failureType
                    + "\n失败信息：" + clip(failureReason, 2000)
                    + "\n候选回答：" + clip(attempt.getAnswer(), 2000);
            String result = llm.complete(system, user, 40);
            return result == null || result.isBlank() ? fallback : result.trim();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String clip(String value, int max) {
        if (value == null) return "无";
        return value.length() <= max ? value : value.substring(0, max) + "\n...[已截断]";
    }
}
