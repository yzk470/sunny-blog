package com.sunnyblog.dto;

import java.time.LocalDateTime;

public record CommentVO(
        Long id,
        Long postId,
        String postTitle,
        String author,
        String email,
        String website,
        String content,
        Integer status,
        Boolean isAdmin,
        Long parentId,
        LocalDateTime createdAt
) {
}
