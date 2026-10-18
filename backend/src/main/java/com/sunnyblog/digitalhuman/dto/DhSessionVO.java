package com.sunnyblog.digitalhuman.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DhSessionVO(
        Long sessionId,
        String characterCode,
        String characterName,
        String status,
        LocalDateTime createdAt,
        List<DhMessageVO> messages
) {
}
