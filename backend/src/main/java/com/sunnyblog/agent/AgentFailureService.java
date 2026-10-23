package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
        if (lower.contains("policy_denied") || lower.contains("非法路径")
                || lower.contains("outside allowed") || lower.contains("越权")
                || lower.contains("read-only file system") || lower.contains("只读文件系统")) {
            return "POLICY";
        }
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

    public record Reflection(String failureType, String failedAssumption, String evidence,
                             List<String> forbiddenActions, List<String> requiredPreconditions,
                             List<String> invalidatesStrategyIds, List<String> repairOperators,
                             String globalConstraint, String summary) {
    }

    public Reflection reflectStructured(String task, AgentRunService.AttemptRecord attempt,
                                        String failureType, String failureReason,
                                        AgentRunService.Strategy strategy) {
        List<String> forbidden = new ArrayList<>();
        List<String> preconditions = new ArrayList<>();
        List<String> invalidates = new ArrayList<>();
        List<String> repairs = new ArrayList<>();
        String assumption = "当前路线的关键假设未验证";
        String evidence = failureReason == null ? "" : failureReason;
        String global = "";
        if ("POLICY".equals(failureType)) {
            assumption = "目标路径在允许目录内";
            forbidden.add("不要写 workspace、artifacts、skills 之外的路径");
            preconditions.add("所有路径必须先确认位于 workspace/artifacts/skills");
            repairs.add("scope_path:改用 workspace/scripts 或 artifacts 下的相对路径");
            repairs.add("ask_user:说明目录限制并请求允许的落盘位置");
            global = "硬约束：只能修改 workspace、artifacts、skills 三个目录；越权时明确说明限制。";
        } else if ("ENVIRONMENT".equals(failureType)) {
            assumption = "环境/依赖可用";
            preconditions.add("先检查工具、依赖、路径和权限");
            repairs.add("switch_tool:换一种不依赖当前环境的实现");
            repairs.add("add_preflight:先做依赖和路径预检再重试");
        } else if ("TRANSIENT".equals(failureType)) {
            assumption = "外部调用稳定";
            preconditions.add("瞬时故障最多重试 1 次");
            repairs.add("retry:短暂等待后重试当前步骤");
        } else if ("FORMAT".equals(failureType)) {
            assumption = "模型输出格式符合协议";
            forbidden.add("不要输出 XML/DSML 或非 JSON 工具调用");
            repairs.add("format_repair:严格按 JSON 协议重新输出");
        } else if ("BUDGET".equals(failureType)) {
            assumption = "当前范围可在预算内完成";
            repairs.add("reduce_scope:缩小到最小可交付结果");
        } else if ("UNSAT".equals(failureType)) {
            assumption = "当前能力可以完成该路线";
            repairs.add("ask_user:说明限制并请求替代输入");
        } else if ("TOOL".equals(failureType)) {
            assumption = "工具参数和调用方式正确";
            preconditions.add("先读取错误信息，避免重复相同工具调用");
            repairs.add("change_arguments:修改工具参数或工具");
        } else {
            assumption = "当前路线的逻辑假设成立";
            repairs.add("decompose:拆成更小的可验证步骤");
            repairs.add("switch_strategy:换一种实现思路");
        }
        if (strategy != null && ("POLICY".equals(failureType) || "ENVIRONMENT".equals(failureType)
                || "UNSAT".equals(failureType) || "LOGIC".equals(failureType))) {
            invalidates.add(strategy.getId());
        }
        if (!llm.isConfigured()) {
            return new Reflection(failureType, assumption, evidence, forbidden, preconditions,
                    invalidates, repairs, global, fallbackSummary(failureType));
        }
        try {
            String system = """
                    你是失败复盘器。把失败转成结构化约束，后续搜索必须使用这些约束。
                    只输出 JSON：
                    {"failedAssumption":"被证伪的假设","evidence":"关键错误证据",
                     "forbiddenActions":["后续不能做什么"],
                     "requiredPreconditions":["后续必须先检查什么"],
                     "invalidatesStrategyIds":["必须剪掉的路线 id"],
                     "repairOperators":["switch_tool: ...","decompose: ..."],
                     "globalConstraint":"一句对所有后续路线生效的约束",
                     "summary":"一到两句中文复盘"}
                    """;
            String user = "任务：\n" + task
                    + "\n\n失败路线：" + attempt.getStrategyTitle() + " / " + attempt.getApproach()
                    + "\n路线 id：" + (strategy == null ? "" : strategy.getId())
                    + "\n失败类型：" + failureType
                    + "\n失败信息：" + clip(failureReason, 2500)
                    + "\n候选回答：" + clip(attempt.getAnswer(), 2000);
            JsonNode json = llm.completeJson(system, user, 45);
            List<String> f = strings(json.path("forbiddenActions"));
            List<String> p = strings(json.path("requiredPreconditions"));
            List<String> i = strings(json.path("invalidatesStrategyIds"));
            List<String> r = strings(json.path("repairOperators"));
            String a = json.path("failedAssumption").asText(assumption);
            String e = json.path("evidence").asText(evidence);
            String g = json.path("globalConstraint").asText(global);
            String s = json.path("summary").asText(fallbackSummary(failureType));
            return new Reflection(failureType, a, e,
                    f.isEmpty() ? forbidden : f,
                    p.isEmpty() ? preconditions : p,
                    i.isEmpty() ? invalidates : i,
                    r.isEmpty() ? repairs : r,
                    g, s);
        } catch (Exception ex) {
            return new Reflection(failureType, assumption, evidence, forbidden, preconditions,
                    invalidates, repairs, global, fallbackSummary(failureType));
        }
    }

    public String reflect(String task, AgentRunService.AttemptRecord attempt,
                          String failureType, String failureReason) {
        return reflectStructured(task, attempt, failureType, failureReason, null).summary();
    }

    private List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isBlank()) result.add(value);
            }
        }
        return result;
    }

    private String fallbackSummary(String failureType) {
        return switch (failureType) {
            case "POLICY" -> "这次失败是目录越权；下一次只能写 workspace、artifacts、skills，越权时明确说明限制。";
            case "ENVIRONMENT" -> "这次失败是环境/依赖问题；下一次先预检路径、依赖和权限，再换实现路线。";
            case "TRANSIENT" -> "这次失败可能是瞬时故障；下一次先重试一次，仍失败再换路线。";
            case "FORMAT" -> "这次失败是输出格式问题；下一次严格按要求格式输出。";
            case "BUDGET" -> "这次失败是预算耗尽；下一次缩小范围，先交付最小可用结果。";
            case "UNSAT" -> "当前路线能力不足；下一次改用替代工具或明确说明限制。";
            case "TOOL" -> "这次工具链失败；下一次避免重复相同动作，先读取错误再换参数或工具。";
            default -> "这次路线假设不成立；下一次换一条不同思路，并先验证最小步骤。";
        };
    }

    private String clip(String value, int max) {
        if (value == null) return "无";
        return value.length() <= max ? value : value.substring(0, max) + "\n...[已截断]";
    }
}
