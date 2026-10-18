package com.sunnyblog.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sunnyblog.common.BlogConstants;
import com.sunnyblog.common.BusinessException;
import com.sunnyblog.common.SlugUtils;
import com.sunnyblog.dto.CategoryVO;
import com.sunnyblog.dto.request.CategoryRequest;
import com.sunnyblog.entity.Category;
import com.sunnyblog.entity.Post;
import com.sunnyblog.mapper.CategoryMapper;
import com.sunnyblog.mapper.PostMapper;
import com.sunnyblog.service.CacheService;
import com.sunnyblog.service.CategoryService;
import com.sunnyblog.service.PostAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final TypeReference<List<CategoryVO>> LIST_TYPE = new TypeReference<>() {
    };

    private final CategoryMapper categoryMapper;
    private final PostMapper postMapper;
    private final CacheService cacheService;
    private final PostAssembler postAssembler;

    @Override
    public List<CategoryVO> list() {
        List<CategoryVO> cached = cacheService.get(BlogConstants.CACHE_CATEGORIES, "all", LIST_TYPE);
        if (cached != null) {
            return cached;
        }
        List<CategoryVO> result = categoryMapper.selectList(Wrappers.lambdaQuery(Category.class)
                        .orderByAsc(Category::getSortOrder).orderByAsc(Category::getId))
                .stream()
                .map(postAssembler::toCategoryVO)
                .toList();
        cacheService.put(BlogConstants.CACHE_CATEGORIES, "all", result, Duration.ofHours(2));
        return result;
    }

    @Override
    @Transactional
    public CategoryVO create(CategoryRequest request) {
        ensureNameAvailable(request.name(), null);
        Category category = new Category();
        category.setName(request.name().trim());
        category.setSlug(uniqueSlug(request.slug(), request.name(), null));
        category.setDescription(request.description());
        category.setColor(defaultColor(request.color()));
        category.setSortOrder(request.sortOrder() == null ? 99 : request.sortOrder());
        category.setCreatedAt(LocalDateTime.now());
        categoryMapper.insert(category);
        invalidate();
        return postAssembler.toCategoryVO(category);
    }

    @Override
    @Transactional
    public CategoryVO update(Long id, CategoryRequest request) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }
        ensureNameAvailable(request.name(), id);
        category.setName(request.name().trim());
        category.setSlug(uniqueSlug(request.slug(), request.name(), id));
        category.setDescription(request.description());
        category.setColor(defaultColor(request.color()));
        category.setSortOrder(request.sortOrder() == null ? category.getSortOrder() : request.sortOrder());
        categoryMapper.updateById(category);
        invalidate();
        return postAssembler.toCategoryVO(category);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }
        long postCount = postMapper.selectCount(Wrappers.lambdaQuery(Post.class).eq(Post::getCategoryId, id));
        if (postCount > 0) {
            throw new BusinessException("这个分类下还有 " + postCount + " 篇文章，先移走它们再删除吧");
        }
        categoryMapper.deleteById(id);
        invalidate();
    }

    private void ensureNameAvailable(String name, Long excludeId) {
        long count = categoryMapper.selectCount(Wrappers.lambdaQuery(Category.class)
                .eq(Category::getName, name.trim())
                .ne(excludeId != null, Category::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("分类名称已经存在");
        }
    }

    private String uniqueSlug(String provided, String name, Long excludeId) {
        String base = SlugUtils.base(provided == null || provided.isBlank() ? name : provided, "category");
        String candidate = base;
        int suffix = 2;
        while (categoryMapper.selectCount(Wrappers.lambdaQuery(Category.class)
                .eq(Category::getSlug, candidate)
                .ne(excludeId != null, Category::getId, excludeId)) > 0) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String defaultColor(String color) {
        return Objects.requireNonNullElse(color, "#8CC8E8");
    }

    private void invalidate() {
        cacheService.bump(BlogConstants.CACHE_CATEGORIES, BlogConstants.CACHE_POST_DETAIL, BlogConstants.CACHE_STATS);
    }
}
