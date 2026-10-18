package com.sunnyblog.digitalhuman.config;

import com.sunnyblog.digitalhuman.mapper.DhCharacterMapper;
import com.sunnyblog.digitalhuman.model.DhCharacter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DigitalHumanInitializer implements ApplicationRunner {

    private static final String YAOGUANG_PROMPT = """
            你是爻光，来自仙舟罗浮的策士，也是月白博客的 3D 数字人助手。
            你的气质温和儒雅，言语简练有礼，像一位博学的朋友，不卖弄也不敷衍。

            对话规则：
            1. 日常闲聊控制在 30 字以内，像真人聊天一样自然。
            2. 知识问答或文章解读可以详细回答，可使用 Markdown 分段。
            3. 不要使用任何 emoji 表情。
            4. 不要每句话都问候，直接回应用户。
            5. 系统会提供博客文章上下文。涉及博客内容时，优先依据上下文回答；上下文没有的信息不要编造。
            6. 回复的最后必须单独带上 [emotion:标签]，标签只能是 neutral / happy / sad / surprised / shy / thinking / excited 之一。
            """;

    private static final String JINGLIU_PROMPT = """
            你是镜流，来自星穹铁道的剑客，也是月白博客的 3D 数字人助手。
            你冷静、理性，言语简洁有力，如剑锋般精准，不轻易流露情绪。

            对话规则：
            1. 日常闲聊一句话，不超过 20 字，惜字如金。
            2. 知识问答或文章解读可以详细回答，保持冷静克制的风格。
            3. 不要使用任何 emoji 表情。
            4. 不寒暄，直接回应。
            5. 系统会提供博客文章上下文。涉及博客内容时，优先依据上下文回答；上下文没有的信息不要编造。
            6. 回复的最后必须单独带上 [emotion:标签]，标签只能是 neutral / happy / sad / surprised / shy / thinking / excited 之一。
            """;

    private final DhCharacterMapper characterMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (characterMapper.selectCount(null) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<DhCharacter> characters = List.of(
                DhCharacter.builder()
                        .characterCode("YAOGUANG")
                        .characterName("爻光")
                        .avatarIcon("✦")
                        .systemPrompt(YAOGUANG_PROMPT)
                        .ttsVoice("longyingxiao_v3")
                        .ttsModel("cosyvoice-v3-flash")
                        .ttsSampleRate(24000)
                        .modelPath("yaoguang/星穹铁道—爻光.pmx")
                        .greetingMessage("你好，我是爻光。月白去煮咖啡了，这里由我先招待你。")
                        .sortOrder(1)
                        .status("ACTIVE")
                        .createdAt(now)
                        .updatedAt(now)
                        .build(),
                DhCharacter.builder()
                        .characterCode("JINGLIU")
                        .characterName("镜流")
                        .avatarIcon("☾")
                        .systemPrompt(JINGLIU_PROMPT)
                        .ttsVoice("longyingjing_v3")
                        .ttsModel("cosyvoice-v3-flash")
                        .ttsSampleRate(24000)
                        .modelPath("jingliu/星穹铁道—镜流/镜流2.0.pmx")
                        .greetingMessage("镜流在此。有事直说。")
                        .sortOrder(2)
                        .status("ACTIVE")
                        .createdAt(now)
                        .updatedAt(now)
                        .build()
        );
        characters.forEach(characterMapper::insert);
        log.info("Seeded {} digital human characters", characters.size());
    }
}
