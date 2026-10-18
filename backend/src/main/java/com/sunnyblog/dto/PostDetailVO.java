package com.sunnyblog.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailVO(
        Long id,
        String title,
        String slug,
        String summary,
        String content,
        String coverImage,
        CategoryVO category,
        List<TagVO> tags,
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
