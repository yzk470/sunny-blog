package com.sunnyblog.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sunnyblog.common.BlogConstants;
import com.sunnyblog.common.BusinessException;
import com.sunnyblog.common.SlugUtils;
import com.sunnyblog.dto.TagVO;
import com.sunnyblog.dto.request.TagRequest;
import com.sunnyblog.entity.PostTag;
import com.sunnyblog.entity.Tag;
import com.sunnyblog.mapper.PostTagMapper;
import com.sunnyblog.mapper.TagMapper;
import com.sunnyblog.service.CacheService;
import com.sunnyblog.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private static final TypeReference<List<TagVO>> LIST_TYPE = new TypeReference<>() {
    };

    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    private final CacheService cacheService;

    @Override
    public List<TagVO> list() {
        List<TagVO> cached = cacheService.get(BlogConstants.CACHE_TAGS, "all", LIST_TYPE);
        if (cached != null) {
            return cached;
        }
        List<TagVO> result = tagMapper.selectList(Wrappers.lambdaQuery(Tag.class).orderByAsc(Tag::getId))
                .stream()
                .map(this::toVO)
                .toList();
        cacheService.put(BlogConstants.CACHE_TAGS, "all", result, Duration.ofHours(2));
        return result;
    }

    @Override
    @Transactional
    public TagVO create(TagRequest request) {
        ensureNameAvailable(request.name(), null);
        Tag tag = new Tag();
        tag.setName(request.name().trim());
        tag.setSlug(uniqueSlug(request.slug(), request.name(), null));
        tag.setColor(Objects.requireNonNullElse(request.color(), "#F7B7C8"));
        tag.setCreatedAt(LocalDateTime.now());
        tagMapper.insert(tag);
        invalidate();
        return toVO(tag);
    }

    @Override
    @Transactional
    public TagVO update(Long id, TagRequest request) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(404, "标签不存在");
        }
        ensureNameAvailable(request.name(), id);
        tag.setName(request.name().trim());
        tag.setSlug(uniqueSlug(request.slug(), request.name(), id));
        tag.setColor(Objects.requireNonNullElse(request.color(), tag.getColor()));
        tagMapper.updateById(tag);
        invalidate();
        return toVO(tag);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(404, "标签不存在");
        }
        postTagMapper.delete(Wrappers.lambdaQuery(PostTag.class).eq(PostTag::getTagId, id));
        tagMapper.deleteById(id);
        invalidate();
    }

    private void ensureNameAvailable(String name, Long excludeId) {
        long count = tagMapper.selectCount(Wrappers.lambdaQuery(Tag.class)
                .eq(Tag::getName, name.trim())
                .ne(excludeId != null, Tag::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("标签名称已经存在");
        }
    }

    private String uniqueSlug(String provided, String name, Long excludeId) {
        String base = SlugUtils.base(provided == null || provided.isBlank() ? name : provided, "tag");
        String candidate = base;
        int suffix = 2;
        while (tagMapper.selectCount(Wrappers.lambdaQuery(Tag.class)
                .eq(Tag::getSlug, candidate)
                .ne(excludeId != null, Tag::getId, excludeId)) > 0) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private TagVO toVO(Tag tag) {
        return new TagVO(tag.getId(), tag.getName(), tag.getSlug(), tag.getColor());
    }

    private void invalidate() {
        cacheService.bump(BlogConstants.CACHE_TAGS, BlogConstants.CACHE_POST_DETAIL, BlogConstants.CACHE_STATS);
    }
}
