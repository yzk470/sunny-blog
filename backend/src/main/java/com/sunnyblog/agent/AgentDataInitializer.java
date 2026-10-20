package com.sunnyblog.agent;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class AgentDataInitializer {

    private final AgentProperties properties;
    private final AgentSkillsService skills;

    @PostConstruct
    public void initialize() {
        try {
            Path root = Path.of(properties.getDir()).toAbsolutePath().normalize();
            Files.createDirectories(root.resolve("workspace/scripts"));
            Files.createDirectories(root.resolve("skills"));
            Files.createDirectories(root.resolve("sessions"));
            Files.createDirectories(root.resolve("runs"));
            if (skills.list().isEmpty()) {
                skills.save("general", "通用任务",
                        "适用于分析、规划、写作和需要拆解步骤的通用任务。",
                        "先确认目标和输入，再拆解步骤。需要计算或生成文件时，把脚本写到 workspace/scripts 下执行。");
                skills.save("coding", "代码与脚本",
                        "写 Python/JavaScript 脚本、执行并检查输出。",
                        "脚本放在 workspace/scripts。运行失败时读取 ERROR，修改脚本后重试，完成后可删除临时脚本。");
            }
        } catch (Exception ignored) {
            // The agent endpoints will surface directory errors when used.
        }
    }
}
