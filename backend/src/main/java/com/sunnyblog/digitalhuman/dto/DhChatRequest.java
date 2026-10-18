package com.sunnyblog.digitalhuman.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DhChatRequest(
        @NotNull(message = "会话不存在") Long sessionId,
        @NotBlank(message = "请输入想说的话")
        @Size(max = 500, message = "单条消息不能超过 500 字")
        String content,
        @Size(max = 160) String articleSlug
) {
}
