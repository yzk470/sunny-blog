package com.sunnyblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "请输入用户名") String username,
        @NotBlank(message = "请输入密码") String password
) {
}
