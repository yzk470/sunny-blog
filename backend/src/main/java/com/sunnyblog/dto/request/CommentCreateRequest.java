package com.sunnyblog.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotBlank(message = "请留下你的昵称")
        @Size(max = 40, message = "昵称不能超过 40 个字符")
        String author,
        @Email(message = "邮箱格式不正确")
        @Size(max = 120)
        String email,
        @Size(max = 200, message = "网址过长")
        String website,
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 1000, message = "评论不能超过 1000 个字符")
        String content,
        Long parentId
) {
}
