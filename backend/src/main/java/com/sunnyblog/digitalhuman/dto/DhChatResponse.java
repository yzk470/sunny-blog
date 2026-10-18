package com.sunnyblog.digitalhuman.dto;

public record DhChatResponse(
        Long messageId,
        Long sessionId,
        String reply,
        String emotion,
        String audioUrl,
        String ttsError
) {
}
