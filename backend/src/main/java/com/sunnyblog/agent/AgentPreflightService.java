package com.sunnyblog.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Cheap deterministic checks before spending model/tool budget. This is not a
 * guarantee, but it avoids burning an attempt on an obviously broken setup.
 */
@Service
@RequiredArgsConstructor
public class AgentPreflightService {

    private static final Pattern NEEDS_EXECUTION = Pattern.compile(
            "(?s).*(脚本|运行|执行|python|node|shell|命令|转换|生成文件|pptx|docx|xlsx|pdf|html|图片|爬取|抓取).*",
            Pattern.CASE_INSENSITIVE);

    private final AgentProperties properties;
    private final AgentFileTools files;
    private final AgentToolExecutor tools;

    public record Preflight(boolean ok, List<String> blockers, List<String> warnings, String summary) {
    }

    public Preflight check(String task) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            Path workspace = files.workspaceRoot();
            Files.createDirectories(workspace);
            if (!Files.isWritable(workspace)) blockers.add("workspace 不可写");
        } catch (Exception ex) {
            blockers.add("workspace 初始化失败: " + ex.getMessage());
        }
        try {
            Path artifacts = files.artifactsRoot();
            Files.createDirectories(artifacts);
            if (!Files.isWritable(artifacts)) blockers.add("artifacts 不可写");
        } catch (Exception ex) {
            blockers.add("artifacts 初始化失败: " + ex.getMessage());
        }
        boolean needsExecution = task != null && NEEDS_EXECUTION.matcher(task).matches();
        if (needsExecution && !properties.isExecutionEnabled()) {
            blockers.add("任务看起来需要脚本/命令执行，但 AGENT_EXECUTION_ENABLED=false");
        }
        String catalog = tools.catalog();
        if (catalog == null || catalog.isBlank()) blockers.add("工具目录为空");
        if (needsExecution && !catalog.contains("run_script")) {
            blockers.add("当前工具目录没有 run_script");
        }
        if (needsExecution && !catalog.contains("write_file")) {
            warnings.add("当前工具目录没有 write_file，可能无法准备脚本");
        }
        String summary = blockers.isEmpty()
                ? (warnings.isEmpty() ? "预检通过" : "预检通过，但有警告：" + String.join("；", warnings))
                : "预检失败：" + String.join("；", blockers);
        return new Preflight(blockers.isEmpty(), blockers, warnings, summary);
    }
}
