package com.sunnyblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostSaveRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 120, message = "标题不能超过 120 个字符")
        String title,
        @Size(max = 160, message = "slug 不能超过 160 个字符")
        String slug,
        @Size(max = 500, message = "摘要不能超过 500 个字符")
        String summary,
        @NotBlank(message = "正文不能为空")
        String content,
        @Size(max = 500) String coverImage,
        @NotNull(message = "请选择分类") Long categoryId,
        List<Long> tagIds,
        Integer status,
        Boolean isTop
) {
}
