package com.sunnyblog.dto;

import java.time.LocalDateTime;

public record SiteProfileVO(
        Long id,
        String nickname,
        String avatar,
        String bio,
        String slogan,
        String location,
        String email,
        String github,
        String aboutContent,
        String footerText,
        LocalDateTime updatedAt
) {
}
