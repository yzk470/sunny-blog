package com.sunnyblog.controller;

import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.dto.CategoryVO;
import com.sunnyblog.dto.TagVO;
import com.sunnyblog.dto.request.CategoryRequest;
import com.sunnyblog.dto.request.TagRequest;
import com.sunnyblog.service.CategoryService;
import com.sunnyblog.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "后台 · 分类与标签")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTaxonomyController {

    private final CategoryService categoryService;
    private final TagService tagService;

    @Operation(summary = "后台分类列表")
    @GetMapping("/categories")
    public ApiResponse<List<CategoryVO>> categories() {
        return ApiResponse.ok(categoryService.list());
    }

    @Operation(summary = "新建分类")
    @PostMapping("/categories")
    public ApiResponse<CategoryVO> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryService.create(request));
    }

    @Operation(summary = "更新分类")
    @PutMapping("/categories/{id}")
    public ApiResponse<CategoryVO> updateCategory(@PathVariable Long id,
                                                  @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryService.update(id, request));
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "后台标签列表")
    @GetMapping("/tags")
    public ApiResponse<List<TagVO>> tags() {
        return ApiResponse.ok(tagService.list());
    }

    @Operation(summary = "新建标签")
    @PostMapping("/tags")
    public ApiResponse<TagVO> createTag(@Valid @RequestBody TagRequest request) {
        return ApiResponse.ok(tagService.create(request));
    }

    @Operation(summary = "更新标签")
    @PutMapping("/tags/{id}")
    public ApiResponse<TagVO> updateTag(@PathVariable Long id, @Valid @RequestBody TagRequest request) {
        return ApiResponse.ok(tagService.update(id, request));
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/tags/{id}")
    public ApiResponse<Void> deleteTag(@PathVariable Long id) {
        tagService.delete(id);
        return ApiResponse.ok();
    }
}
