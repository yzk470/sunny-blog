package com.sunnyblog.service;

import com.sunnyblog.dto.AdminPostVO;
import com.sunnyblog.dto.CategoryVO;
import com.sunnyblog.dto.PostDetailVO;
import com.sunnyblog.dto.PostSummaryVO;
import com.sunnyblog.dto.PostTagRow;
import com.sunnyblog.dto.TagVO;
import com.sunnyblog.entity.Category;
import com.sunnyblog.entity.Post;
import com.sunnyblog.entity.PostTag;
import com.sunnyblog.entity.Tag;
import com.sunnyblog.mapper.CategoryMapper;
import com.sunnyblog.mapper.PostMapper;
import com.sunnyblog.mapper.PostTagMapper;
import com.sunnyblog.mapper.TagMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 负责把实体组装成前端视图对象，并批量查询分类/标签，避免列表接口的 N+1 查询。
 */
@Component
@RequiredArgsConstructor
public class PostAssembler {

    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    private final PostMapper postMapper;

    public List<PostSummaryVO> toSummaries(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        List<Long> categoryIds = posts.stream().map(Post::getCategoryId).filter(Objects::nonNull).distinct().toList();

        Map<Long, Category> categories = categoryIds.isEmpty()
                ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        Map<Long, List<TagVO>> tagsByPost = new HashMap<>();
        List<PostTagRow> rows = postMapper.selectTagsByPostIds(postIds);
        for (PostTagRow row : rows) {
            tagsByPost.computeIfAbsent(row.getPostId(), k -> new ArrayList<>())
                    .add(new TagVO(row.getId(), row.getName(), row.getSlug(), row.getColor()));
        }

        List<PostSummaryVO> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            result.add(toSummary(post, categories.get(post.getCategoryId()),
                    tagsByPost.getOrDefault(post.getId(), List.of())));
        }
        return result;
    }

    public PostSummaryVO toSummary(Post post, Category category, List<TagVO> tags) {
        return new PostSummaryVO(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getSummary(),
                post.getCoverImage(),
                toCategoryVO(category),
                tags == null ? List.of() : tags,
                Boolean.TRUE.equals(post.getIsTop()),
                post.getStatus(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getReadingTime(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getPublishedAt()
        );
    }

    public PostDetailVO toDetail(Post post) {
        Category category = post.getCategoryId() == null ? null : categoryMapper.selectById(post.getCategoryId());
        List<TagVO> tags = postMapper.selectTagsByPostIds(List.of(post.getId())).stream()
                .map(row -> new TagVO(row.getId(), row.getName(), row.getSlug(), row.getColor()))
                .toList();
        return new PostDetailVO(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getSummary(),
                post.getContent(),
                post.getCoverImage(),
                toCategoryVO(category),
                tags,
                Boolean.TRUE.equals(post.getIsTop()),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getReadingTime(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getPublishedAt()
        );
    }

    public AdminPostVO toAdmin(Post post) {
        List<Long> tagIds = postTagMapper.selectList(Wrappers.lambdaQuery(PostTag.class)
                        .eq(PostTag::getPostId, post.getId()))
                .stream().map(PostTag::getTagId).toList();
        return new AdminPostVO(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getSummary(),
                post.getContent(),
                post.getCoverImage(),
                post.getCategoryId(),
                tagIds,
                post.getStatus(),
                Boolean.TRUE.equals(post.getIsTop()),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getReadingTime(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getPublishedAt()
        );
    }

    public CategoryVO toCategoryVO(Category category) {
        if (category == null) {
            return null;
        }
        return new CategoryVO(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.getColor());
    }

    public List<TagVO> allTags(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Tag> tags = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
        return tagIds.stream()
                .map(tags::get)
                .filter(Objects::nonNull)
                .map(t -> new TagVO(t.getId(), t.getName(), t.getSlug(), t.getColor()))
                .toList();
    }

    public Map<Long, List<TagVO>> tagsByPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<TagVO>> tagsByPost = new LinkedHashMap<>();
        for (PostTagRow row : postMapper.selectTagsByPostIds(postIds)) {
            tagsByPost.computeIfAbsent(row.getPostId(), k -> new ArrayList<>())
                    .add(new TagVO(row.getId(), row.getName(), row.getSlug(), row.getColor()));
        }
        return tagsByPost;
    }
}
