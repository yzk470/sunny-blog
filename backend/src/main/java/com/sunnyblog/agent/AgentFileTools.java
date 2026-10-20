package com.sunnyblog.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The only filesystem/process surface exposed to the model. Every path is
 * resolved below the dedicated workspace or skills roots.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentFileTools {

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "python", "python3", "node", "ls", "cat", "pwd", "find", "head", "tail", "wc"
    );

    private final AgentProperties properties;

    public record ToolResult(boolean success, String output) {
        public static ToolResult ok(String output) {
            return new ToolResult(true, output);
        }

        public static ToolResult fail(String output) {
            return new ToolResult(false, output);
        }
    }

    public Path workspaceRoot() {
        return root("workspace");
    }

    public Path skillsRoot() {
        return root("skills");
    }

    public Path artifactsRoot() {
        Path base = root("artifacts");
        String runId = AgentTaskContext.runId();
        if (runId == null || runId.isBlank()) return base;
        Path runRoot = base.resolve(runId).normalize();
        if (!runRoot.startsWith(base)) throw new IllegalStateException("非法任务产物路径");
        try {
            Files.createDirectories(runRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建任务产物目录", ex);
        }
        return runRoot;
    }

    private Path root(String name) {
        Path path = Path.of(properties.getDir()).toAbsolutePath().normalize().resolve(name);
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建 Agent 目录", ex);
        }
        return path;
    }

    public ToolResult read(String scope, String relative) {
        try {
            Path path = resolve(scope, relative);
            if (!Files.exists(path)) {
                String hint = "skills".equalsIgnoreCase(scope)
                        ? "；技能资源路径要包含技能目录，例如 pptx-skill/editing.md，请先 list_files"
                        : "";
                return ToolResult.fail("文件不存在: " + relative + hint);
            }
            if (Files.isDirectory(path)) return ToolResult.fail("这是一个目录，请使用 list_files");
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return ToolResult.ok(clip(content));
        } catch (Exception ex) {
            return ToolResult.fail(ex.getMessage());
        }
    }

    public ToolResult write(String scope, String relative, String content) {
        try {
            if (content == null) content = "";
            if (content.length() > 1_000_000) return ToolResult.fail("文件过大（上限 1MB）");
            Path path = resolve(scope, relative);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return ToolResult.ok("已写入 " + relative + "（" + content.length() + " 字符）");
        } catch (Exception ex) {
            return ToolResult.fail(ex.getMessage());
        }
    }

    public ToolResult list(String scope, String relative) {
        try {
            Path path = resolve(scope, relative == null ? "" : relative);
            if (!Files.exists(path)) return ToolResult.fail("目录不存在: " + relative);
            if (!Files.isDirectory(path)) return ToolResult.ok(path.getFileName().toString());
            try (Stream<Path> stream = Files.walk(path, 5)) {
                String listing = stream
                        .filter(p -> !p.equals(path))
                        .limit(300)
                        .map(p -> path.relativize(p).toString() + (Files.isDirectory(p) ? "/" : ""))
                        .sorted()
                        .collect(Collectors.joining("\n"));
                return ToolResult.ok(listing.isBlank() ? "（空目录）" : listing);
            }
        } catch (Exception ex) {
            return ToolResult.fail(ex.getMessage());
        }
    }

    public ToolResult delete(String scope, String relative) {
        try {
            Path path = resolve(scope, relative);
            if (!Files.exists(path)) return ToolResult.fail("文件不存在: " + relative);
            if (Files.isDirectory(path)) return ToolResult.fail("只能删除文件，不能删除目录");
            Files.delete(path);
            return ToolResult.ok("已删除 " + relative);
        } catch (Exception ex) {
            return ToolResult.fail(ex.getMessage());
        }
    }

    public ToolResult runScript(String scope, String relative, String extraArgs) {
        if (!properties.isExecutionEnabled()) {
            return ToolResult.fail("服务器已关闭脚本执行（AGENT_EXECUTION_ENABLED=false）");
        }
        try {
            Path root = rootFor(scope);
            Path path = resolve(scope, relative);
            if (!Files.exists(path) || Files.isDirectory(path)) return ToolResult.fail("脚本不存在: " + relative);
            String lower = path.getFileName().toString().toLowerCase();
            List<String> command = new ArrayList<>();
            if (lower.endsWith(".py")) command.add(python());
            else if (lower.endsWith(".js") || lower.endsWith(".mjs")) command.add("node");
            else return ToolResult.fail("只支持 .py / .js / .mjs 脚本");
            Path cwd;
            if ("skills".equalsIgnoreCase(scope)) {
                String relativePath = root.relativize(path).toString().replace('\\', '/');
                int slash = relativePath.indexOf('/');
                Path skillDir = slash > 0 ? root.resolve(relativePath.substring(0, slash)) : root;
                command.add(skillDir.relativize(path).toString().replace('\\', '/'));
                cwd = skillDir;
            } else if ("artifacts".equalsIgnoreCase(scope)) {
                return ToolResult.fail("产物目录不能作为脚本目录");
            } else {
                command.add(root.relativize(path).toString().replace('\\', '/'));
                cwd = root;
            }
            if (extraArgs != null && !extraArgs.isBlank()) {
                for (String arg : extraArgs.trim().split("\\s+")) {
                    if (arg.startsWith("/") || arg.contains("..")) {
                        return ToolResult.fail("脚本参数只允许使用相对路径");
                    }
                    command.add(arg);
                }
            }
            return execute(command, cwd);
        } catch (Exception ex) {
            return ToolResult.fail(ex.getMessage());
        }
    }

    public ToolResult runScript(String relative) {
        return runScript("workspace", relative, "");
    }

    public ToolResult runCommand(String scope, String rawCommand) {
        if (!properties.isExecutionEnabled()) {
            return ToolResult.fail("服务器已关闭命令执行（AGENT_EXECUTION_ENABLED=false）");
        }
        if (rawCommand == null || rawCommand.isBlank()) return ToolResult.fail("命令为空");
        if (rawCommand.length() > 500) return ToolResult.fail("命令过长");
        if (rawCommand.matches(".*[;&|><`$\\n\\r].*")) {
            return ToolResult.fail("不允许 shell 管道、重定向或命令串联。请把逻辑写成 scripts/ 下的脚本，再用 run_script 执行。");
        }
        String[] tokens = rawCommand.trim().split("\\s+");
        if (tokens.length == 0) return ToolResult.fail("命令为空");
        String executable = tokens[0];
        if (!ALLOWED_COMMANDS.contains(executable)) {
            return ToolResult.fail("命令不在白名单内，可用: " + String.join(", ", ALLOWED_COMMANDS));
        }
        List<String> command = new ArrayList<>();
        for (String token : tokens) {
            if (token.equals("-c") || token.equals("-e") || token.equals("--eval")) {
                return ToolResult.fail("不允许内联代码参数；请先写入 scripts/ 下的脚本，再使用 run_script / python3 scripts/x.py");
            }
            if (token.startsWith("/") || token.contains("..")) {
                return ToolResult.fail("只允许访问 workspace/skills 内的相对路径");
            }
            command.add(token);
        }
        return execute(command, rootFor(scope));
    }

    public ToolResult runCommand(String rawCommand) {
        return runCommand("workspace", rawCommand);
    }

    private ToolResult execute(List<String> command, Path cwd) {
        try {
            log.info("Agent executing {} in {} (systemd={})", command, cwd, properties.isSystemdRun());
            boolean isolated = properties.isSystemdRun();
            List<String> actualCommand = isolated ? systemdCommand(command, cwd) : command;
            ProcessBuilder builder = new ProcessBuilder(actualCommand);
            if (!isolated) builder.directory(cwd.toFile());
            builder.redirectErrorStream(true);
            builder.environment().remove("DEEPSEEK_API_KEY");
            builder.environment().remove("OPENAI_API_KEY");
            if (isolated) {
                builder.environment().put("XDG_RUNTIME_DIR", properties.getSystemdRuntimeDir());
                builder.environment().put("DBUS_SESSION_BUS_ADDRESS",
                        "unix:path=" + properties.getSystemdRuntimeDir() + "/bus");
            } else {
                builder.environment().put("AGENT_WORKSPACE_DIR", workspaceRoot().toString());
                builder.environment().put("AGENT_SKILLS_DIR", skillsRoot().toString());
                builder.environment().put("AGENT_ARTIFACTS_DIR", artifactsRoot().toString());
            }
            Process process = builder.start();
            process.getOutputStream().close();
            CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
                try (var in = process.getInputStream()) {
                    return in.readAllBytes();
                } catch (IOException ex) {
                    return new byte[0];
                }
            });
            boolean finished = process.waitFor(properties.getCommandTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.fail("执行超时（" + properties.getCommandTimeoutSeconds() + " 秒）");
            }
            byte[] bytes = outputFuture.get(5, TimeUnit.SECONDS);
            String output = new String(bytes, StandardCharsets.UTF_8);
            int code = process.exitValue();
            String text = "exit=" + code + "\n" + (output.isBlank() ? "（无输出）" : output);
            return code == 0 ? ToolResult.ok(clip(text)) : ToolResult.fail(clip(text));
        } catch (IOException ex) {
            return ToolResult.fail("无法执行命令: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ToolResult.fail("执行被中断");
        } catch (Exception ex) {
            return ToolResult.fail("读取命令输出失败: " + ex.getMessage());
        }
    }

    private Path resolve(String scope, String relative) {
        Path root = rootFor(scope);
        if (relative == null) relative = "";
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("非法路径: " + relative);
        return path;
    }

    private Path rootFor(String scope) {
        if ("skills".equalsIgnoreCase(scope)) return skillsRoot();
        if ("artifacts".equalsIgnoreCase(scope)) return artifactsRoot();
        return workspaceRoot();
    }

    private String python() {
        return Files.exists(Path.of("/usr/bin/python3")) ? "python3" : "python";
    }

    /**
     * Calls the root-owned wrapper, which launches a transient system service as
     * the dedicated unprivileged sunnyagent user. The backend itself never runs
     * model-controlled code and never gains root privileges.
     */
    private List<String> systemdCommand(List<String> original, Path cwd) {
        List<String> command = new ArrayList<>();
        command.add("/usr/bin/sudo");
        command.add("-n");
        command.add(properties.getSystemdRunner());
        command.add("--cwd");
        command.add(cwd.toString());
        command.add("--timeout");
        command.add(String.valueOf(properties.getCommandTimeoutSeconds()));
        command.add("--workspace");
        command.add(workspaceRoot().toString());
        command.add("--skills");
        command.add(skillsRoot().toString());
        command.add("--artifacts");
        command.add(artifactsRoot().toString());
        command.add("--");
        List<String> executable = new ArrayList<>(original);
        String exe = executable.get(0);
        if (!exe.startsWith("/")) {
            String absolute = switch (exe) {
                case "python", "python3" -> "/usr/bin/python3";
                case "node" -> "/usr/bin/node";
                case "ls", "cat", "pwd", "find", "head", "tail", "wc" -> "/usr/bin/" + exe;
                default -> exe;
            };
            executable.set(0, absolute);
        }
        command.addAll(executable);
        return command;
    }

    private String clip(String text) {
        if (text == null) return "";
        int max = properties.getMaxOutputChars();
        return text.length() <= max ? text : text.substring(0, max) + "\n...[输出已截断]";
    }
}
