package com.sunnyblog.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminPostVO(
        Long id,
        String title,
        String slug,
        String summary,
        String content,
        String coverImage,
        Long categoryId,
        List<Long> tagIds,
        Integer status,
        Boolean isTop,
        Integer viewCount,
        Integer likeCount,
        Integer commentCount,
        Integer readingTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime publishedAt
) {
}
