package com.sunnyblog.digitalhuman.config;

import com.sunnyblog.digitalhuman.mapper.DhCharacterMapper;
import com.sunnyblog.digitalhuman.model.DhCharacter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DigitalHumanInitializer implements ApplicationRunner {

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
                        .systemPrompt(DigitalHumanPersonas.YAOGUANG)
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
                        .systemPrompt(DigitalHumanPersonas.JINGLIU)
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
