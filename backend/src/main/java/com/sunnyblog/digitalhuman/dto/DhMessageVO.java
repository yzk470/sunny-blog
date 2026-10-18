package com.sunnyblog.digitalhuman.dto;

import java.time.LocalDateTime;

public record DhMessageVO(
        Long id,
        String role,
        String content,
        String emotion,
        String audioUrl,
        LocalDateTime createdAt
) {
}
