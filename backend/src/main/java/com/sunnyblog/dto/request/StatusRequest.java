package com.sunnyblog.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态不合法")
        @Max(value = 2, message = "状态不合法")
        Integer status
) {
}
