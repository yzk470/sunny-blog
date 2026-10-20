package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentToolExecutor {

    private final AgentFileTools fileTools;
    private final AgentSkillsService skillsService;
    private final AgentArtifactService artifacts;

    public AgentFileTools.ToolResult execute(String action, JsonNode input) {
        try {
            return switch (action == null ? "" : action) {
                case "list_skills" -> AgentFileTools.ToolResult.ok(skillsService.catalog());
                case "read_skill" -> {
                    String name = text(input, "name");
                    yield skillsService.find(name)
                            .map(skill -> AgentFileTools.ToolResult.ok(
                                    "技能：" + skill.name() + "\n"
                                            + "技能根：skills/" + skill.slug()
                                            + "（read_file/run_script 时 scope=skills，path 使用 "
                                            + skill.slug() + "/文件名）\n"
                                            + "资源文件：\n"
                                            + (skill.resources().isEmpty() ? "（无）"
                                            : String.join("\n", skill.resources().stream()
                                                    .map(r -> "- " + skill.slug() + "/" + r).toList()))
                                            + "\n\n" + skill.body()))
                            .orElseGet(() -> AgentFileTools.ToolResult.fail("技能不存在: " + name));
                }
                case "read_file" -> fileTools.read(text(input, "scope"), text(input, "path"));
                case "write_file" -> fileTools.write(text(input, "scope"), text(input, "path"), text(input, "content"));
                case "list_files" -> fileTools.list(text(input, "scope"), text(input, "path"));
                case "delete_file" -> fileTools.delete(text(input, "scope"), text(input, "path"));
                case "inspect_artifact" -> AgentFileTools.ToolResult.ok(artifacts.inspect(text(input, "path")));
                case "run_script" -> fileTools.runScript(text(input, "scope"), text(input, "path"), text(input, "args"));
                case "run_command" -> fileTools.runCommand(text(input, "scope"), text(input, "command"));
                default -> AgentFileTools.ToolResult.fail("未知工具: " + action);
            };
        } catch (Exception ex) {
            return AgentFileTools.ToolResult.fail("工具执行异常: " + ex.getMessage());
        }
    }

    private String text(JsonNode input, String field) {
        if (input == null) return "";
        JsonNode node = input.get(field);
        return node == null || node.isNull() ? "" : node.asText();
    }

    public String catalog() {
        return """
                - list_skills: 列出已加载的 skills
                - read_skill: 读取一个 skill 的完整说明。输入 {"name":"skill名"}
                - read_file: 读取文件。输入 {"scope":"workspace|skills|artifacts","path":"相对路径"}
                - write_file: 写入文件。输入 {"scope":"workspace|skills|artifacts","path":"相对路径","content":"内容"}
                - list_files: 列出目录。输入 {"scope":"workspace|skills|artifacts","path":"相对路径"}
                - delete_file: 删除文件。输入 {"scope":"workspace|skills|artifacts","path":"相对路径"}
                - inspect_artifact: 检查产物结构（PPT 页数/文字、Word 文字、图片尺寸、HTML 预览）。输入 {"path":"artifacts相对的路径"}
                - run_script: 执行脚本。输入 {"scope":"workspace|skills","path":"scripts/task.py","args":"可选参数"}
                - run_command: 仅用于简单只读检查（python3/node/ls/cat/pwd/find/head/tail/wc），禁止管道、重定向和 -c/-e；业务逻辑请写 scripts/ 后使用 run_script。
                产物（pptx/docx/xlsx/pdf/html/图片）必须写到 scope=artifacts；脚本运行时环境变量 AGENT_ARTIFACTS_DIR 指向产物目录。
                """;
    }
}
