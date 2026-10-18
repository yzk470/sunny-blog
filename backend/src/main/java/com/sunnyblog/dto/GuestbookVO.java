package com.sunnyblog.dto;

import java.time.LocalDateTime;

public record GuestbookVO(
        Long id,
        String author,
        String email,
        String content,
        String reply,
        Integer status,
        LocalDateTime createdAt
) {
}
