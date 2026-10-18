package com.sunnyblog.common;

public final class BlogConstants {

    private BlogConstants() {
    }

    public static final int POST_DRAFT = 0;
    public static final int POST_PUBLISHED = 1;

    public static final int COMMENT_PENDING = 0;
    public static final int COMMENT_APPROVED = 1;
    public static final int COMMENT_REJECTED = 2;

    public static final String CACHE_SITE = "site";
    public static final String CACHE_CATEGORIES = "categories";
    public static final String CACHE_TAGS = "tags";
    public static final String CACHE_ARCHIVE = "archive";
    public static final String CACHE_POST_DETAIL = "postDetail";
    public static final String CACHE_STATS = "stats";

    public static final String REDIS_VIEW_HASH = "sunnyblog:views:pending";
}
