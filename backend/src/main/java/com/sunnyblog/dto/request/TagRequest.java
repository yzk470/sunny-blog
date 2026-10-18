package com.sunnyblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagRequest(
        @NotBlank(message = "标签名称不能为空")
        @Size(max = 30, message = "标签名称不能超过 30 个字符")
        String name,
        @Size(max = 80) String slug,
        @Size(max = 30) String color
) {
}
