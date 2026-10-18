package com.sunnyblog.dto;

import java.util.List;

public record DashboardVO(
        long postCount,
        long publishedCount,
        long draftCount,
        long commentCount,
        long pendingCommentCount,
        long guestbookCount,
        long categoryCount,
        long tagCount,
        long totalViews,
        List<CommentVO> recentComments,
        List<PostSummaryVO> popularPosts
) {
}
