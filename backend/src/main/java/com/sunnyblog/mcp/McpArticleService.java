package com.sunnyblog.mcp;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunnyblog.common.BlogConstants;
import com.sunnyblog.common.BusinessException;
import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.AdminPostVO;
import com.sunnyblog.dto.CategoryVO;
import com.sunnyblog.dto.PostSummaryVO;
import com.sunnyblog.dto.TagVO;
import com.sunnyblog.dto.request.PostSaveRequest;
import com.sunnyblog.dto.request.TagRequest;
import com.sunnyblog.entity.Post;
import com.sunnyblog.entity.Tag;
import com.sunnyblog.mapper.PostMapper;
import com.sunnyblog.mapper.TagMapper;
import com.sunnyblog.service.CategoryService;
import com.sunnyblog.service.PostService;
import com.sunnyblog.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpArticleService {

    private final PostService postService;
    private final PostMapper postMapper;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final TagMapper tagMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.mcp.public-site-url:https://www.bluish-white.top}")
    private String publicSiteUrl;

    public ToolResult call(String toolName, JsonNode args) throws Exception {
        JsonNode safeArgs = args == null || args.isNull() ? objectMapper.createObjectNode() : args;
        return switch (toolName) {
            case "list_articles" -> listArticles(safeArgs);
            case "get_article" -> getArticle(safeArgs);
            case "list_taxonomy" -> listTaxonomy();
            case "create_article" -> createArticle(safeArgs);
            case "update_article" -> updateArticle(safeArgs);
            case "publish_article" -> publishArticle(safeArgs);
            case "delete_article" -> deleteArticle(safeArgs);
            default -> throw new BusinessException("未知的 MCP 工具：" + toolName);
        };
    }

    private ToolResult listArticles(JsonNode args) throws Exception {
        int page = Math.max(1, intValue(args, "page", 1));
        int size = Math.max(1, Math.min(intValue(args, "size", 10), 50));
        Integer status = parseStatus(text(args, "status"), null);
        String keyword = text(args, "keyword");
        PageResult<PostSummaryVO> result = postService.adminList(page, size, status, keyword);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", result.page());
        payload.put("size", result.size());
        payload.put("total", result.total());
        payload.put("pages", result.pages());
        payload.put("items", result.records().stream().map(this::summaryView).toList());
        return ToolResult.ok(pretty(payload));
    }

    private ToolResult getArticle(JsonNode args) throws Exception {
        AdminPostVO post = getAdmin(args);
        Map<String, Object> payload = postView(post);
        payload.put("url", articleUrl(post.slug()));
        return ToolResult.ok(pretty(payload));
    }

    private ToolResult listTaxonomy() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("categories", categoryService.list());
        payload.put("tags", tagService.list());
        return ToolResult.ok(pretty(payload));
    }

    private ToolResult createArticle(JsonNode args) throws Exception {
        String title = requiredText(args, "title", "标题");
        String content = requiredText(args, "content", "正文");
        validateArticle(title, content, text(args, "summary"), text(args, "coverImage"));
        CategoryVO category = resolveCategory(text(args, "category"));
        List<Long> tagIds = resolveTags(args.get("tags"));
        Integer status = parseStatus(text(args, "status"), BlogConstants.POST_DRAFT);
        boolean isTop = args.path("isTop").asBoolean(false);
        PostSaveRequest request = new PostSaveRequest(
                title,
                text(args, "slug"),
                text(args, "summary"),
                content,
                text(args, "coverImage"),
                category.id(),
                tagIds,
                status,
                isTop
        );
        AdminPostVO created = postService.create(request);
        return ToolResult.ok("文章已创建：" + messageSummary(created) + "\n" + articleUrl(created.slug()));
    }

    private ToolResult updateArticle(JsonNode args) throws Exception {
        AdminPostVO existing = getAdmin(args);
        String title = has(args, "title") ? requiredText(args, "title", "标题") : existing.title();
        String content = has(args, "content") ? requiredText(args, "content", "正文") : existing.content();
        String summary = has(args, "summary") ? text(args, "summary") : existing.summary();
        String coverImage = has(args, "coverImage") ? text(args, "coverImage") : existing.coverImage();
        validateArticle(title, content, summary, coverImage);

        CategoryVO category = has(args, "category")
                ? resolveCategory(text(args, "category"))
                : resolveCategory(String.valueOf(existing.categoryId()));
        List<Long> tagIds = has(args, "tags") ? resolveTags(args.get("tags")) : existing.tagIds();
        Integer status = has(args, "status") ? parseStatus(text(args, "status"), existing.status()) : existing.status();
        boolean isTop = has(args, "isTop") ? args.path("isTop").asBoolean(false) : Boolean.TRUE.equals(existing.isTop());
        String newSlug = has(args, "newSlug") ? text(args, "newSlug") : existing.slug();

        PostSaveRequest request = new PostSaveRequest(
                title,
                newSlug,
                summary,
                content,
                coverImage,
                category.id(),
                tagIds,
                status,
                isTop
        );
        AdminPostVO updated = postService.update(existing.id(), request);
        return ToolResult.ok("文章已更新：" + messageSummary(updated) + "\n" + articleUrl(updated.slug()));
    }

    private ToolResult publishArticle(JsonNode args) throws Exception {
        AdminPostVO post = getAdmin(args);
        Integer status = parseStatus(text(args, "status"), BlogConstants.POST_PUBLISHED);
        postService.changeStatus(post.id(), status);
        AdminPostVO updated = postService.getAdmin(post.id());
        String state = updated.status() != null && updated.status() == BlogConstants.POST_PUBLISHED ? "已发布" : "已撤回为草稿";
        return ToolResult.ok("文章" + state + "：" + messageSummary(updated) + "\n" + articleUrl(updated.slug()));
    }

    private ToolResult deleteArticle(JsonNode args) throws Exception {
        AdminPostVO post = getAdmin(args);
        if (!args.path("confirm").asBoolean(false)) {
            throw new BusinessException("删除文章需要显式传 confirm=true");
        }
        postService.delete(post.id());
        return ToolResult.ok("文章已删除：id=" + post.id() + "，slug=" + post.slug());
    }

    private AdminPostVO getAdmin(JsonNode args) {
        Post post = findPost(args);
        return postService.getAdmin(post.getId());
    }

    private Post findPost(JsonNode args) {
        Long id = longValue(args, "id");
        if (id != null) {
            Post post = postMapper.selectById(id);
            if (post == null) {
                throw new BusinessException(404, "文章不存在：id=" + id);
            }
            return post;
        }
        String slug = text(args, "slug");
        if (slug == null || slug.isBlank()) {
            throw new BusinessException("请提供文章 id 或 slug");
        }
        Post post = postMapper.selectOne(Wrappers.lambdaQuery(Post.class).eq(Post::getSlug, slug.trim()));
        if (post == null) {
            throw new BusinessException(404, "文章不存在：slug=" + slug);
        }
        return post;
    }

    private CategoryVO resolveCategory(String input) {
        List<CategoryVO> categories = categoryService.list();
        if (categories.isEmpty()) {
            throw new BusinessException("博客还没有分类，请先在后台创建分类");
        }
        String value = input == null ? "" : input.trim();
        if (value.isBlank()) {
            return categories.stream()
                    .filter(c -> "笔记".equals(c.name()) || "notes".equalsIgnoreCase(c.slug())
                            || "note".equalsIgnoreCase(c.slug()))
                    .findFirst()
                    .orElse(categories.get(0));
        }
        Long id = parseLong(value);
        return categories.stream()
                .filter(c -> (id != null && id.equals(c.id()))
                        || c.name().equalsIgnoreCase(value)
                        || c.slug().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException("分类不存在：" + value + "；可用分类：" +
                        categories.stream().map(CategoryVO::name).toList()));
    }

    private List<Long> resolveTags(JsonNode tagsNode) {
        if (tagsNode == null || tagsNode.isNull()) {
            return List.of();
        }
        if (!tagsNode.isArray()) {
            throw new BusinessException("tags 必须是字符串数组");
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : tagsNode) {
            String name = item.asText("").trim();
            if (name.isBlank()) {
                continue;
            }
            if (name.length() > 30) {
                throw new BusinessException("标签名称不能超过 30 个字符：" + name);
            }
            Tag tag = tagMapper.selectOne(Wrappers.lambdaQuery(Tag.class)
                    .eq(Tag::getName, name)
                    .or()
                    .eq(Tag::getSlug, name));
            if (tag == null) {
                TagVO created = tagService.create(new TagRequest(name, null, null));
                ids.add(created.id());
            } else {
                ids.add(tag.getId());
            }
        }
        return ids.stream().distinct().toList();
    }

    private void validateArticle(String title, String content, String summary, String coverImage) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("标题不能为空");
        }
        if (title.length() > 120) {
            throw new BusinessException("标题不能超过 120 个字符");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("正文不能为空");
        }
        if (summary != null && summary.length() > 500) {
            throw new BusinessException("摘要不能超过 500 个字符");
        }
        if (coverImage != null && coverImage.length() > 500) {
            throw new BusinessException("封面 URL 不能超过 500 个字符");
        }
    }

    private Map<String, Object> summaryView(PostSummaryVO post) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", post.id());
        item.put("title", post.title());
        item.put("slug", post.slug());
        item.put("status", post.status() != null && post.status() == BlogConstants.POST_PUBLISHED ? "published" : "draft");
        item.put("category", post.category() == null ? null : post.category().name());
        item.put("tags", post.tags() == null ? List.of() : post.tags().stream().map(TagVO::name).toList());
        item.put("isTop", post.isTop());
        item.put("updatedAt", post.updatedAt());
        item.put("publishedAt", post.publishedAt());
        item.put("url", articleUrl(post.slug()));
        return item;
    }

    private Map<String, Object> postView(AdminPostVO post) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", post.id());
        item.put("title", post.title());
        item.put("slug", post.slug());
        item.put("summary", post.summary());
        item.put("content", post.content());
        item.put("coverImage", post.coverImage());
        item.put("categoryId", post.categoryId());
        item.put("tagIds", post.tagIds());
        item.put("status", post.status() != null && post.status() == BlogConstants.POST_PUBLISHED ? "published" : "draft");
        item.put("isTop", post.isTop());
        item.put("createdAt", post.createdAt());
        item.put("updatedAt", post.updatedAt());
        item.put("publishedAt", post.publishedAt());
        return item;
    }

    private String messageSummary(AdminPostVO post) {
        String state = post.status() != null && post.status() == BlogConstants.POST_PUBLISHED ? "已发布" : "草稿";
        return "id=" + post.id() + "，slug=" + post.slug() + "，标题=《" + post.title() + "》，状态=" + state;
    }

    private String articleUrl(String slug) {
        return publicSiteUrl.replaceAll("/+$", "") + "/posts/" + slug;
    }

    private String pretty(Object value) throws Exception {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private Integer parseStatus(String value, Integer fallback) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return fallback;
        }
        return switch (value.trim().toLowerCase()) {
            case "draft", "0", "false" -> BlogConstants.POST_DRAFT;
            case "published", "publish", "1", "true" -> BlogConstants.POST_PUBLISHED;
            default -> throw new BusinessException("status 只能是 draft、published 或 all");
        };
    }

    private String requiredText(JsonNode args, String field, String label) {
        String value = text(args, field);
        if (value == null || value.isBlank()) {
            throw new BusinessException(label + "不能为空");
        }
        return value;
    }

    private String text(JsonNode args, String field) {
        JsonNode node = args.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText("");
        return value.isBlank() ? null : value.trim();
    }

    private boolean has(JsonNode args, String field) {
        return args.has(field);
    }

    private int intValue(JsonNode args, String field, int fallback) {
        JsonNode node = args.get(field);
        return node == null || !node.canConvertToInt() ? fallback : node.asInt(fallback);
    }

    private Long longValue(JsonNode args, String field) {
        JsonNode node = args.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        return parseLong(node.asText(""));
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record ToolResult(boolean error, String text) {
        public static ToolResult ok(String text) {
            return new ToolResult(false, text);
        }

        public static ToolResult error(String text) {
            return new ToolResult(true, text);
        }
    }
}
