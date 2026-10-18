package com.sunnyblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 40, message = "分类名称不能超过 40 个字符")
        String name,
        @Size(max = 80) String slug,
        @Size(max = 200) String description,
        @Size(max = 30) String color,
        Integer sortOrder
) {
}
