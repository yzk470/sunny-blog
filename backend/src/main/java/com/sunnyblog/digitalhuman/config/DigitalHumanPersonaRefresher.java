package com.sunnyblog.digitalhuman.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sunnyblog.digitalhuman.mapper.DhCharacterMapper;
import com.sunnyblog.digitalhuman.model.DhCharacter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * One-time persona upgrade for already seeded characters. Only legacy prompts
 * are replaced; a prompt edited to a custom version is left untouched.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DigitalHumanPersonaRefresher implements ApplicationRunner {

    private final DhCharacterMapper characterMapper;

    @Override
    public void run(ApplicationArguments args) {
        refresh("YAOGUANG", DigitalHumanPersonas.YAOGUANG);
        refresh("JINGLIU", DigitalHumanPersonas.JINGLIU);
    }

    private void refresh(String code, String prompt) {
        DhCharacter character = characterMapper.selectOne(Wrappers.lambdaQuery(DhCharacter.class)
                .eq(DhCharacter::getCharacterCode, code));
        if (character == null || !DigitalHumanPersonas.isLegacy(character.getSystemPrompt())) {
            return;
        }
        character.setSystemPrompt(prompt);
        character.setUpdatedAt(LocalDateTime.now());
        characterMapper.updateById(character);
        log.info("Refreshed digital human persona: {} -> {}", code, DigitalHumanPersonas.VERSION);
    }
}
