package com.sunnyblog.digitalhuman.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sunnyblog.common.BlogConstants;
import com.sunnyblog.entity.Post;
import com.sunnyblog.mapper.PostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Functions exposed to the digital human LLM so it can look up published
 * articles instead of guessing from titles or a truncated prompt context.
 */
@Slf4j
@Service
public class DigitalHumanArticleTools {

    private static final int MAX_ARTICLE_CHARS = 8000;

    private final PostMapper postMapper;
    private final FunctionCallback searchCallback;
    private final FunctionCallback readCallback;

    public DigitalHumanArticleTools(PostMapper postMapper) {
        this.postMapper = postMapper;
        this.searchCallback = FunctionCallback.builder()
                .function("search_articles", (SearchRequest request) -> search(request.keyword()))
                .description("搜索博客文章。用户提到某篇文章、主题或关键词时，先调用它找到文章；只返回已发布文章。")
                .inputType(SearchRequest.class)
                .build();
        this.readCallback = FunctionCallback.builder()
                .function("read_article", (ReadArticleRequest request) -> read(request.slugOrTitle()))
                .description("读取一篇已发布博客文章的完整正文。先搜索找到 slug，再读取正文，然后用自己的话讲解，不要逐字念全文。")
                .inputType(ReadArticleRequest.class)
                .build();
    }

    public FunctionCallback searchCallback() {
        return searchCallback;
    }

    public FunctionCallback readCallback() {
        return readCallback;
    }

    private String search(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        if (value.isBlank()) {
            return "请提供搜索关键词。";
        }
        log.info("Digital human article search: keyword={}", value);
        List<Post> posts = postMapper.selectList(Wrappers.lambdaQuery(Post.class)
                .eq(Post::getStatus, BlogConstants.POST_PUBLISHED)
                .and(query -> query.like(Post::getTitle, value)
                        .or().like(Post::getSummary, value)
                        .or().like(Post::getContent, value))
                .orderByDesc(Post::getPublishedAt)
                .last("limit 6"));
        if (posts.isEmpty()) {
            return "没有找到与「" + value + "」相关的已发布文章。";
        }
        StringBuilder result = new StringBuilder("找到以下文章：\n");
        for (Post post : posts) {
            result.append("- 《").append(post.getTitle()).append("》")
                    .append(" | slug: ").append(post.getSlug())
                    .append(" | 摘要：").append(truncate(post.getSummary(), 160))
                    .append('\n');
        }
        return result.toString();
    }

    private String read(String slugOrTitle) {
        String value = slugOrTitle == null ? "" : slugOrTitle.trim();
        if (value.isBlank()) {
            return "请提供文章 slug 或标题。";
        }
        log.info("Digital human article read: query={}", value);
        Post post = postMapper.selectOne(Wrappers.lambdaQuery(Post.class)
                .eq(Post::getStatus, BlogConstants.POST_PUBLISHED)
                .and(query -> query.eq(Post::getSlug, value).or().eq(Post::getTitle, value))
                .last("limit 1"));
        if (post == null) {
            post = postMapper.selectOne(Wrappers.lambdaQuery(Post.class)
                    .eq(Post::getStatus, BlogConstants.POST_PUBLISHED)
                    .like(Post::getTitle, value)
                    .orderByDesc(Post::getPublishedAt)
                    .last("limit 1"));
        }
        if (post == null) {
            return "没有找到已发布文章「" + value + "」。可以先调用 search_articles 搜索。";
        }
        return "《" + post.getTitle() + "》\n"
                + "slug: " + post.getSlug() + "\n"
                + "摘要：" + truncate(post.getSummary(), 300) + "\n\n"
                + "正文：\n" + truncate(post.getContent(), MAX_ARTICLE_CHARS);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    public record SearchRequest(String keyword) {
    }

    public record ReadArticleRequest(String slugOrTitle) {
    }
}
