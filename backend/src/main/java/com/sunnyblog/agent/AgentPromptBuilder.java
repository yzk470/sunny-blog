package com.sunnyblog.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cache-oriented prompt assembly for the ReAct loop.
 *
 * <p>The legacy user prompt puts changing observations before the task and
 * budget, so almost every step invalidates the prefix after the system
 * message. The cache-friendly layout keeps memory, task and permissions first,
 * appends observations, and leaves the changing budget/format at the tail.
 * The system prompt keeps a stable version header so calls from different
 * tasks can share the fixed prefix.</p>
 */
@Component
@RequiredArgsConstructor
public class AgentPromptBuilder {

    public static final String VERSION = "agent-prompt-cache-v1";

    private static final String PERMISSIONS = """
            ## 目录权限（硬约束）
            只允许修改以下三个根目录：
            - workspace：脚本、输入、中间文件（脚本放 workspace/scripts）
            - artifacts：最终产物
            - skills：技能资源
            禁止写 /tmp、/etc、/opt 或任何其他目录；不允许用 ../ 跳出根目录。
            任务要求越权目录时，必须明确说明限制，不能伪造成功。
            """;

    private final AgentPromptMode mode;

    public record Prompt(String system, String user) {
    }

    /** Stable version header shared by Plan/Strategy/Search/Validation. */
    public static String withHeader(String system) {
        return "[AGENT_PROMPT_VERSION] " + VERSION + "\n" + system;
    }

    public Prompt react(String baseSystem, String legacyUser,
                        String memoryContext, String task, String observations,
                        int step, int limit, int remaining,
                        boolean executionSucceeded, String lastExecutionOutput) {
        if (!mode.cacheFriendly()) {
            return new Prompt(baseSystem, legacyUser);
        }
        String system = withHeader(baseSystem);
        StringBuilder user = new StringBuilder();
        user.append("## 历史记忆\n")
                .append(memoryContext == null || memoryContext.isBlank() ? "（暂无历史记忆）" : memoryContext)
                .append("\n\n## 当前任务\n")
                .append(task)
                .append("\n\n")
                .append(PERMISSIONS)
                .append("\n## 已执行动作与观察\n")
                .append(observations == null || observations.isBlank()
                        ? "（还没有执行任何工具）" : observations)
                .append("\n\n## 执行预算\n")
                .append("这是第 ").append(step).append(" / ").append(limit)
                .append(" 步，剩余 ").append(remaining).append(" 步。\n")
                .append(remaining == 0
                        ? "这是最后一步，必须 action=final，不能再调用任何工具。请直接总结已完成的内容；如果信息不足就说明不足。"
                        : remaining <= 2
                        ? "只剩很少步数，必须开始收敛：最多再调用一次必要工具，然后立即 final。"
                        : "请控制步数，不要重复探索；完成目标后尽早 final。")
                .append("\n");
        if (executionSucceeded) {
            user.append("\n## 已真实执行成功\n真实输出：\n")
                    .append(clip(lastExecutionOutput, 1500))
                    .append("\n如果当前路线已经满足任务成功标准，立即 final；否则只做必要的只读验证，不要重复已经成功的写文件/运行动作。\n");
        }
        user.append("""
                ## 输出格式
                只输出一个 JSON 对象：
                {"thought":"你在想什么","action":"工具名或 final","input":{...}}
                thought 不超过 60 个中文字符；final 只写结论、产物路径和必要验证，不复述过程。
                如果任务已经完成，action 使用 final，input 为 {"answer":"给用户的最终回答"}。
                """);
        return new Prompt(system, user.toString());
    }

    private String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
