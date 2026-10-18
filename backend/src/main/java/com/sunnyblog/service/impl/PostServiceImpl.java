package com.sunnyblog.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sunnyblog.common.BlogConstants;
import com.sunnyblog.common.BusinessException;
import com.sunnyblog.common.PageResult;
import com.sunnyblog.common.SlugUtils;
import com.sunnyblog.dto.AdminPostVO;
import com.sunnyblog.dto.ArchiveItemVO;
import com.sunnyblog.dto.PostDetailVO;
import com.sunnyblog.dto.PostSummaryVO;
import com.sunnyblog.dto.request.PostSaveRequest;
import com.sunnyblog.entity.Category;
import com.sunnyblog.entity.Comment;
import com.sunnyblog.entity.Post;
import com.sunnyblog.entity.PostTag;
import com.sunnyblog.entity.Tag;
import com.sunnyblog.mapper.CategoryMapper;
import com.sunnyblog.mapper.CommentMapper;
import com.sunnyblog.mapper.PostMapper;
import com.sunnyblog.mapper.PostTagMapper;
import com.sunnyblog.mapper.TagMapper;
import com.sunnyblog.service.CacheService;
import com.sunnyblog.service.PostAssembler;
import com.sunnyblog.service.PostService;
import com.sunnyblog.service.ViewCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private static final TypeReference<PostDetailVO> DETAIL_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ArchiveItemVO>> ARCHIVE_TYPE = new TypeReference<>() {
    };

    private final PostMapper postMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    private final CommentMapper commentMapper;
    private final PostAssembler assembler;
    private final CacheService cacheService;
    private final ViewCounterService viewCounterService;

    @Override
    public PageResult<PostSummaryVO> listPublished(int page, int size, String category, String tag,
                                                   String keyword, String sort) {
        int pageNum = Math.max(1, page);
        int pageSize = clampSize(size);
        var query = Wrappers.lambdaQuery(Post.class).eq(Post::getStatus, BlogConstants.POST_PUBLISHED);

        if (category != null && !category.isBlank()) {
            Category found = categoryMapper.selectOne(Wrappers.lambdaQuery(Category.class)
                    .eq(Category::getSlug, category.trim()));
            if (found == null) {
                return PageResult.empty(pageNum, pageSize);
            }
            query.eq(Post::getCategoryId, found.getId());
        }
        if (tag != null && !tag.isBlank()) {
            Tag found = tagMapper.selectOne(Wrappers.lambdaQuery(Tag.class).eq(Tag::getSlug, tag.trim()));
            if (found == null) {
                return PageResult.empty(pageNum, pageSize);
            }
            query.inSql(Post::getId, "select post_id from blog_post_tag where tag_id = " + found.getId());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            query.and(q -> q.like(Post::getTitle, kw)
                    .or().like(Post::getSummary, kw)
                    .or().like(Post::getContent, kw));
        }
        if ("popular".equalsIgnoreCase(sort)) {
            query.orderByDesc(Post::getViewCount).orderByDesc(Post::getPublishedAt);
        } else {
            query.orderByDesc(Post::getPublishedAt).orderByDesc(Post::getId);
        }

        IPage<Post> result = postMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return PageResult.of(result, assembler.toSummaries(result.getRecords()));
    }

    @Override
    public List<PostSummaryVO> latest(int limit) {
        return publishedPage(limit, false);
    }

    @Override
    public List<PostSummaryVO> featured(int limit) {
        return publishedPage(limit, true);
    }

    @Override
    public PostDetailVO getPublishedBySlug(String slug) {
        PostDetailVO cached = cacheService.get(BlogConstants.CACHE_POST_DETAIL, slug, DETAIL_TYPE);
        if (cached != null) {
            return withLiveViews(cached);
        }
        Post post = requirePublished(slug);
        PostDetailVO detail = assembler.toDetail(post);
        cacheService.put(BlogConstants.CACHE_POST_DETAIL, slug, detail, Duration.ofMinutes(10));
        return withLiveViews(detail);
    }

    @Override
    public List<ArchiveItemVO> archive() {
        List<ArchiveItemVO> cached = cacheService.get(BlogConstants.CACHE_ARCHIVE, "all", ARCHIVE_TYPE);
        if (cached != null) {
            return cached;
        }
        List<ArchiveItemVO> result = postMapper.selectList(Wrappers.lambdaQuery(Post.class)
                        .select(Post::getId, Post::getTitle, Post::getSlug, Post::getPublishedAt)
                        .eq(Post::getStatus, BlogConstants.POST_PUBLISHED)
                        .isNotNull(Post::getPublishedAt)
                        .orderByDesc(Post::getPublishedAt))
                .stream()
                .map(p -> new ArchiveItemVO(p.getId(), p.getTitle(), p.getSlug(), p.getPublishedAt()))
                .toList();
        cacheService.put(BlogConstants.CACHE_ARCHIVE, "all", result, Duration.ofMinutes(10));
        return result;
    }

    @Override
    public long recordView(String slug) {
        Post post = requirePublished(slug);
        long pending = viewCounterService.increment(post.getId());
        if (pending < 0) {
            Post fresh = postMapper.selectById(post.getId());
            return fresh == null ? 0 : fresh.getViewCount();
        }
        int base = post.getViewCount() == null ? 0 : post.getViewCount();
        return base + pending;
    }

    @Override
    public long like(String slug) {
        Post post = requirePublished(slug);
        postMapper.incrementLikes(post.getId());
        cacheService.bump(BlogConstants.CACHE_POST_DETAIL, BlogConstants.CACHE_STATS);
        Post fresh = postMapper.selectById(post.getId());
        return fresh == null || fresh.getLikeCount() == null ? 0 : fresh.getLikeCount();
    }

    @Override
    public PageResult<PostSummaryVO> adminList(int page, int size, Integer status, String keyword) {
        int pageNum = Math.max(1, page);
        var query = Wrappers.lambdaQuery(Post.class);
        if (status != null) {
            query.eq(Post::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            query.and(q -> q.like(Post::getTitle, kw).or().like(Post::getSummary, kw));
        }
        query.orderByDesc(Post::getUpdatedAt).orderByDesc(Post::getId);
        IPage<Post> result = postMapper.selectPage(new Page<>(pageNum, clampSize(size)), query);
        return PageResult.of(result, assembler.toSummaries(result.getRecords()));
    }

    @Override
    public AdminPostVO getAdmin(Long id) {
        return assembler.toAdmin(requirePost(id));
    }

    @Override
    @Transactional
    public AdminPostVO create(PostSaveRequest request) {
        Category category = requireCategory(request.categoryId());
        int status = normalizeStatus(request.status());
        Post post = new Post();
        applyRequest(post, request, category, status, true);
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        post.setPublishedAt(status == BlogConstants.POST_PUBLISHED ? LocalDateTime.now() : null);
        postMapper.insert(post);
        replaceTags(post.getId(), request.tagIds());
        invalidatePostCaches();
        return assembler.toAdmin(post);
    }

    @Override
    @Transactional
    public AdminPostVO update(Long id, PostSaveRequest request) {
        Post post = requirePost(id);
        Category category = requireCategory(request.categoryId());
        int status = normalizeStatus(request.status());
        applyRequest(post, request, category, status, false);
        post.setUpdatedAt(LocalDateTime.now());
        if (status == BlogConstants.POST_PUBLISHED && post.getPublishedAt() == null) {
            post.setPublishedAt(LocalDateTime.now());
        }
        postMapper.updateById(post);
        replaceTags(post.getId(), request.tagIds());
        invalidatePostCaches();
        return assembler.toAdmin(post);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        Post post = requirePost(id);
        int normalized = normalizeStatus(status);
        post.setStatus(normalized);
        if (normalized == BlogConstants.POST_PUBLISHED && post.getPublishedAt() == null) {
            post.setPublishedAt(LocalDateTime.now());
        }
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(post);
        invalidatePostCaches();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requirePost(id);
        postTagMapper.delete(Wrappers.lambdaQuery(PostTag.class).eq(PostTag::getPostId, id));
        commentMapper.delete(Wrappers.lambdaQuery(Comment.class).eq(Comment::getPostId, id));
        postMapper.deleteById(id);
        invalidatePostCaches();
    }

    private List<PostSummaryVO> publishedPage(int limit, boolean onlyFeatured) {
        int size = Math.max(1, Math.min(limit, 20));
        var query = Wrappers.lambdaQuery(Post.class)
                .eq(Post::getStatus, BlogConstants.POST_PUBLISHED);
        if (onlyFeatured) {
            query.eq(Post::getIsTop, true);
        }
        query.orderByDesc(Post::getPublishedAt).orderByDesc(Post::getId);
        IPage<Post> result = postMapper.selectPage(new Page<>(1, size), query);
        return assembler.toSummaries(result.getRecords());
    }

    private PostDetailVO withLiveViews(PostDetailVO detail) {
        long extra = viewCounterService.pending(detail.id());
        if (extra <= 0) {
            return detail;
        }
        int base = detail.viewCount() == null ? 0 : detail.viewCount();
        return new PostDetailVO(
                detail.id(), detail.title(), detail.slug(), detail.summary(), detail.content(),
                detail.coverImage(), detail.category(), detail.tags(), detail.isTop(),
                (int) (base + extra), detail.likeCount(), detail.commentCount(), detail.readingTime(),
                detail.createdAt(), detail.updatedAt(), detail.publishedAt()
        );
    }

    private Post requirePublished(String slug) {
        Post post = postMapper.selectOne(Wrappers.lambdaQuery(Post.class)
                .eq(Post::getSlug, slug)
                .eq(Post::getStatus, BlogConstants.POST_PUBLISHED));
        if (post == null) {
            throw new BusinessException(404, "这篇文章不存在，也许它被云朵藏起来了");
        }
        return post;
    }

    private Post requirePost(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(404, "文章不存在");
        }
        return post;
    }

    private Category requireCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("请选择一个有效的分类");
        }
        return category;
    }

    private void applyRequest(Post post, PostSaveRequest request, Category category, int status, boolean creating) {
        post.setTitle(request.title().trim());
        post.setSlug(uniqueSlug(request.slug(), request.title(), creating ? null : post.getId()));
        post.setSummary(buildSummary(request.summary(), request.content()));
        post.setContent(request.content());
        post.setCoverImage(request.coverImage());
        post.setCategoryId(category.getId());
        post.setStatus(status);
        post.setIsTop(Boolean.TRUE.equals(request.isTop()));
        post.setReadingTime(estimateReadingTime(request.content()));
    }

    private String uniqueSlug(String provided, String title, Long excludeId) {
        String base = SlugUtils.base(provided == null || provided.isBlank() ? title : provided, "post");
        String candidate = base;
        int suffix = 2;
        while (postMapper.selectCount(Wrappers.lambdaQuery(Post.class)
                .eq(Post::getSlug, candidate)
                .ne(excludeId != null, Post::getId, excludeId)) > 0) {
            candidate = base + "-" + suffix++;
            if (suffix > 100) {
                candidate = base + "-" + System.currentTimeMillis();
                break;
            }
        }
        return candidate;
    }

    private void replaceTags(Long postId, List<Long> tagIds) {
        postTagMapper.delete(Wrappers.lambdaQuery(PostTag.class).eq(PostTag::getPostId, postId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        List<Long> valid = tagMapper.selectBatchIds(tagIds.stream().distinct().toList())
                .stream().map(Tag::getId).toList();
        for (Long tagId : valid) {
            postTagMapper.insert(new PostTag(postId, tagId));
        }
    }

    private int normalizeStatus(Integer status) {
        return status != null && status == BlogConstants.POST_PUBLISHED
                ? BlogConstants.POST_PUBLISHED : BlogConstants.POST_DRAFT;
    }

    private int estimateReadingTime(String content) {
        int chars = content == null ? 0 : content.replaceAll("\\s+", "").length();
        return Math.max(1, (int) Math.ceil(chars / 400.0));
    }

    private String buildSummary(String summary, String content) {
        if (summary != null && !summary.isBlank()) {
            return summary.trim();
        }
        String plain = content == null ? "" : content
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("[#>*_`~|-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.length() > 150) {
            return plain.substring(0, 150) + "…";
        }
        return plain;
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(size, 50));
    }

    private void invalidatePostCaches() {
        cacheService.bump(BlogConstants.CACHE_POST_DETAIL, BlogConstants.CACHE_ARCHIVE, BlogConstants.CACHE_STATS);
    }
}
