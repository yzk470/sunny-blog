package com.sunnyblog.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SiteProfileRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 40)
        String nickname,
        @Size(max = 500) String avatar,
        @Size(max = 300) String bio,
        @Size(max = 200) String slogan,
        @Size(max = 80) String location,
        @Email(message = "邮箱格式不正确") @Size(max = 120) String email,
        @Size(max = 300) String github,
        @Size(max = 20000) String aboutContent,
        @Size(max = 200) String footerText
) {
}
