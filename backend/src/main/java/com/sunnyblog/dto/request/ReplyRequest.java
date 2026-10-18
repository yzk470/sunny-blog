package com.sunnyblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplyRequest(
        @NotBlank(message = "回复内容不能为空")
        @Size(max = 1000, message = "回复不能超过 1000 个字符")
        String reply
) {
}
