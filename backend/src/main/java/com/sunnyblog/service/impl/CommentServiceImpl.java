package com.sunnyblog.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunnyblog.common.BlogConstants;
import com.sunnyblog.common.BusinessException;
import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.CommentVO;
import com.sunnyblog.dto.request.CommentCreateRequest;
import com.sunnyblog.entity.Comment;
import com.sunnyblog.entity.Post;
import com.sunnyblog.mapper.CommentMapper;
import com.sunnyblog.mapper.PostMapper;
import com.sunnyblog.service.CacheService;
import com.sunnyblog.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final CacheService cacheService;

    @Override
    public PageResult<CommentVO> listApprovedByPostSlug(String slug, int page, int size) {
        Post post = requirePublished(slug);
        IPage<Comment> result = commentMapper.selectPage(new Page<>(Math.max(1, page), clampSize(size)),
                Wrappers.lambdaQuery(Comment.class)
                        .eq(Comment::getPostId, post.getId())
                        .eq(Comment::getStatus, BlogConstants.COMMENT_APPROVED)
                        .orderByDesc(Comment::getCreatedAt));
        List<CommentVO> records = result.getRecords().stream()
                .map(c -> toVO(c, null, false))
                .toList();
        return PageResult.of(result, records);
    }

    @Override
    @Transactional
    public CommentVO create(String slug, CommentCreateRequest request) {
        Post post = requirePublished(slug);
        if (request.parentId() != null) {
            Comment parent = commentMapper.selectById(request.parentId());
            if (parent == null || !parent.getPostId().equals(post.getId())) {
                throw new BusinessException("回复的评论不存在");
            }
        }
        Comment comment = new Comment();
        comment.setPostId(post.getId());
        comment.setAuthor(request.author().trim());
        comment.setEmail(trimToNull(request.email()));
        comment.setWebsite(trimToNull(request.website()));
        comment.setContent(request.content().trim());
        comment.setStatus(BlogConstants.COMMENT_PENDING);
        comment.setIsAdmin(false);
        comment.setParentId(request.parentId());
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        return toVO(comment, post.getTitle(), true);
    }

    @Override
    public PageResult<CommentVO> adminList(Integer status, int page, int size) {
        var query = Wrappers.lambdaQuery(Comment.class);
        if (status != null) {
            query.eq(Comment::getStatus, status);
        }
        query.orderByDesc(Comment::getCreatedAt);
        IPage<Comment> result = commentMapper.selectPage(new Page<>(Math.max(1, page), clampSize(size)), query);
        List<Long> postIds = result.getRecords().stream().map(Comment::getPostId).distinct().toList();
        Map<Long, Post> posts = postIds.isEmpty() ? Map.of() : postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        List<CommentVO> records = result.getRecords().stream()
                .map(c -> toVO(c, posts.get(c.getPostId()) == null ? "已删除的文章" : posts.get(c.getPostId()).getTitle(), true))
                .toList();
        return PageResult.of(result, records);
    }

    @Override
    @Transactional
    public void moderate(Long id, int status) {
        Comment comment = requireComment(id);
        if (status < BlogConstants.COMMENT_PENDING || status > BlogConstants.COMMENT_REJECTED) {
            throw new BusinessException("评论状态不合法");
        }
        int oldStatus = comment.getStatus();
        if (oldStatus == status) {
            return;
        }
        comment.setStatus(status);
        commentMapper.updateById(comment);
        if (oldStatus != BlogConstants.COMMENT_APPROVED && status == BlogConstants.COMMENT_APPROVED) {
            postMapper.addCommentCount(comment.getPostId(), 1);
        } else if (oldStatus == BlogConstants.COMMENT_APPROVED) {
            postMapper.addCommentCount(comment.getPostId(), -1);
        }
        invalidate();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Comment comment = requireComment(id);
        if (comment.getStatus() == BlogConstants.COMMENT_APPROVED) {
            postMapper.addCommentCount(comment.getPostId(), -1);
        }
        commentMapper.deleteById(id);
        invalidate();
    }

    private Comment requireComment(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(404, "评论不存在");
        }
        return comment;
    }

    private Post requirePublished(String slug) {
        Post post = postMapper.selectOne(Wrappers.lambdaQuery(Post.class)
                .eq(Post::getSlug, slug)
                .eq(Post::getStatus, BlogConstants.POST_PUBLISHED));
        if (post == null) {
            throw new BusinessException(404, "这篇文章不存在");
        }
        return post;
    }

    private CommentVO toVO(Comment c, String postTitle, boolean adminView) {
        return new CommentVO(
                c.getId(),
                c.getPostId(),
                postTitle,
                c.getAuthor(),
                adminView ? c.getEmail() : null,
                c.getWebsite(),
                c.getContent(),
                adminView ? c.getStatus() : null,
                Boolean.TRUE.equals(c.getIsAdmin()),
                c.getParentId(),
                c.getCreatedAt()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(size, 50));
    }

    private void invalidate() {
        cacheService.bump(BlogConstants.CACHE_POST_DETAIL, BlogConstants.CACHE_STATS);
    }
}
