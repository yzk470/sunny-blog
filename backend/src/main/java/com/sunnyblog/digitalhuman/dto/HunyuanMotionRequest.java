package com.sunnyblog.digitalhuman.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HunyuanMotionRequest(
        @NotBlank(message = "请描述想要的动作")
        @Size(max = 128, message = "动作描述不能超过 128 个字符")
        String prompt,
        Integer duration
) {
}
