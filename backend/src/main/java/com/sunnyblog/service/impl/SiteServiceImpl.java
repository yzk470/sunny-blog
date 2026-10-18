package com.sunnyblog.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sunnyblog.common.BlogConstants;
import com.sunnyblog.common.BusinessException;
import com.sunnyblog.dto.CommentVO;
import com.sunnyblog.dto.DashboardVO;
import com.sunnyblog.dto.PostSummaryVO;
import com.sunnyblog.dto.SiteProfileVO;
import com.sunnyblog.dto.SiteStatsVO;
import com.sunnyblog.dto.request.SiteProfileRequest;
import com.sunnyblog.entity.Comment;
import com.sunnyblog.entity.Post;
import com.sunnyblog.entity.SiteProfile;
import com.sunnyblog.mapper.CategoryMapper;
import com.sunnyblog.mapper.CommentMapper;
import com.sunnyblog.mapper.GuestbookMapper;
import com.sunnyblog.mapper.PostMapper;
import com.sunnyblog.mapper.SiteProfileMapper;
import com.sunnyblog.mapper.TagMapper;
import com.sunnyblog.service.CacheService;
import com.sunnyblog.service.PostAssembler;
import com.sunnyblog.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SiteServiceImpl implements SiteService {

    private static final TypeReference<SiteProfileVO> PROFILE_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<SiteStatsVO> STATS_TYPE = new TypeReference<>() {
    };

    private final SiteProfileMapper siteProfileMapper;
    private final PostMapper postMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final CommentMapper commentMapper;
    private final GuestbookMapper guestbookMapper;
    private final CacheService cacheService;
    private final PostAssembler postAssembler;

    @Override
    public SiteProfileVO getProfile() {
        SiteProfileVO cached = cacheService.get(BlogConstants.CACHE_SITE, "profile", PROFILE_TYPE);
        if (cached != null) {
            return cached;
        }
        SiteProfile profile = loadProfile();
        SiteProfileVO vo = toVO(profile);
        cacheService.put(BlogConstants.CACHE_SITE, "profile", vo, Duration.ofMinutes(30));
        return vo;
    }

    @Override
    public SiteStatsVO getStats() {
        SiteStatsVO cached = cacheService.get(BlogConstants.CACHE_STATS, "summary", STATS_TYPE);
        if (cached != null) {
            return cached;
        }
        SiteStatsVO stats = new SiteStatsVO(
                postMapper.selectCount(Wrappers.lambdaQuery(Post.class)
                        .eq(Post::getStatus, BlogConstants.POST_PUBLISHED)),
                categoryMapper.selectCount(null),
                tagMapper.selectCount(null),
                commentMapper.selectCount(Wrappers.lambdaQuery(Comment.class)
                        .eq(Comment::getStatus, BlogConstants.COMMENT_APPROVED)),
                postMapper.sumViews()
        );
        cacheService.put(BlogConstants.CACHE_STATS, "summary", stats, Duration.ofMinutes(5));
        return stats;
    }

    @Override
    public DashboardVO getDashboard() {
        long postCount = postMapper.selectCount(null);
        long published = postMapper.selectCount(Wrappers.lambdaQuery(Post.class)
                .eq(Post::getStatus, BlogConstants.POST_PUBLISHED));
        long drafts = postMapper.selectCount(Wrappers.lambdaQuery(Post.class)
                .eq(Post::getStatus, BlogConstants.POST_DRAFT));
        long comments = commentMapper.selectCount(null);
        long pendingComments = commentMapper.selectCount(Wrappers.lambdaQuery(Comment.class)
                .eq(Comment::getStatus, BlogConstants.COMMENT_PENDING));
        long guestbookCount = guestbookMapper.selectCount(null);
        long categories = categoryMapper.selectCount(null);
        long tags = tagMapper.selectCount(null);
        long views = postMapper.sumViews();

        List<Comment> recent = commentMapper.selectPage(new Page<>(1, 5),
                Wrappers.lambdaQuery(Comment.class).orderByDesc(Comment::getCreatedAt)).getRecords();
        Map<Long, Post> recentPosts = recent.isEmpty() ? Map.of() : postMapper.selectBatchIds(
                        recent.stream().map(Comment::getPostId).distinct().toList())
                .stream().collect(Collectors.toMap(Post::getId, Function.identity()));
        List<CommentVO> recentComments = recent.stream()
                .map(c -> new CommentVO(c.getId(), c.getPostId(),
                        recentPosts.get(c.getPostId()) == null ? "已删除的文章" : recentPosts.get(c.getPostId()).getTitle(),
                        c.getAuthor(), c.getEmail(), c.getWebsite(), c.getContent(), c.getStatus(),
                        Boolean.TRUE.equals(c.getIsAdmin()), c.getParentId(), c.getCreatedAt()))
                .toList();

        List<Post> popular = postMapper.selectPage(new Page<>(1, 5),
                Wrappers.lambdaQuery(Post.class)
                        .eq(Post::getStatus, BlogConstants.POST_PUBLISHED)
                        .orderByDesc(Post::getViewCount)).getRecords();
        List<PostSummaryVO> popularPosts = postAssembler.toSummaries(popular);

        return new DashboardVO(postCount, published, drafts, comments, pendingComments, guestbookCount,
                categories, tags, views, recentComments, popularPosts);
    }

    @Override
    @Transactional
    public SiteProfileVO updateProfile(SiteProfileRequest request) {
        SiteProfile profile = loadProfile();
        profile.setNickname(request.nickname().trim());
        profile.setAvatar(request.avatar());
        profile.setBio(request.bio());
        profile.setSlogan(request.slogan());
        profile.setLocation(request.location());
        profile.setEmail(request.email());
        profile.setGithub(request.github());
        profile.setAboutContent(request.aboutContent());
        profile.setFooterText(request.footerText());
        profile.setUpdatedAt(LocalDateTime.now());
        siteProfileMapper.updateById(profile);
        cacheService.bump(BlogConstants.CACHE_SITE);
        return toVO(profile);
    }

    private SiteProfile loadProfile() {
        SiteProfile profile = siteProfileMapper.selectOne(Wrappers.lambdaQuery(SiteProfile.class)
                .orderByAsc(SiteProfile::getId).last("limit 1"));
        if (profile == null) {
            profile = createDefaultProfile();
        }
        return profile;
    }

    private SiteProfile createDefaultProfile() {
        SiteProfile profile = new SiteProfile();
        profile.setNickname("月白");
        profile.setAvatar("/images/avatar-yuebai.png");
        profile.setBio("在代码与云朵之间，收集每一个亮晶晶的日子。");
        profile.setSlogan("把日子过成一首明亮的诗");
        profile.setLocation("杭州");
        profile.setEmail("hello@sunny.blog");
        profile.setGithub("https://github.com/");
        profile.setAboutContent("""
                # 嗨，我是月白 👋

                白天写代码，傍晚追晚霞，偶尔在厨房里研究一杯不太成功的拿铁。

                这个小小的博客，用来存放生活里的光：一段散步、一篇文章、一次远行，
                以及那些突然想明白的小事。

                - 🌤 喜欢：晴天、薄荷汽水、干净的代码
                - 🎧 最近在听：City Pop
                - 📮 找我聊天：hello@sunny.blog
                """);
        profile.setFooterText("愿你的每一天，都有阳光落在肩上。");
        profile.setUpdatedAt(LocalDateTime.now());
        siteProfileMapper.insert(profile);
        return profile;
    }

    private SiteProfileVO toVO(SiteProfile p) {
        return new SiteProfileVO(p.getId(), p.getNickname(), p.getAvatar(), p.getBio(), p.getSlogan(),
                p.getLocation(), p.getEmail(), p.getGithub(), p.getAboutContent(), p.getFooterText(),
                p.getUpdatedAt());
    }
}
