package com.sunnyblog.digitalhuman.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sunnyblog.digitalhuman.dto.DhCharacterVO;
import com.sunnyblog.digitalhuman.mapper.DhCharacterMapper;
import com.sunnyblog.digitalhuman.model.DhCharacter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DigitalHumanCharacterService {

    private final DhCharacterMapper characterMapper;

    public List<DhCharacterVO> listActive() {
        return characterMapper.selectList(Wrappers.lambdaQuery(DhCharacter.class)
                        .eq(DhCharacter::getStatus, "ACTIVE")
                        .orderByAsc(DhCharacter::getSortOrder)
                        .orderByAsc(DhCharacter::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    public DhCharacter getByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        DhCharacter character = characterMapper.selectOne(Wrappers.lambdaQuery(DhCharacter.class)
                .eq(DhCharacter::getCharacterCode, code.trim().toUpperCase()));
        return character != null && character.active() ? character : null;
    }

    public DhCharacter getById(Long id) {
        return id == null ? null : characterMapper.selectById(id);
    }

    public DhCharacterVO toVO(DhCharacter character) {
        return new DhCharacterVO(
                character.getId(),
                character.getCharacterCode(),
                character.getCharacterName(),
                character.getAvatarIcon(),
                character.fullModelPath(),
                character.getGreetingMessage(),
                character.getTtsVoice()
        );
    }
}
