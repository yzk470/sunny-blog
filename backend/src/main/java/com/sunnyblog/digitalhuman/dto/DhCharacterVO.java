package com.sunnyblog.digitalhuman.dto;

public record DhCharacterVO(
        Long id,
        String code,
        String name,
        String avatarIcon,
        String modelPath,
        String greeting,
        String voice
) {
}
