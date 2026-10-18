package com.sunnyblog.controller;

import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.dto.CategoryVO;
import com.sunnyblog.dto.TagVO;
import com.sunnyblog.service.CategoryService;
import com.sunnyblog.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "前台 · 分类与标签")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaxonomyController {

    private final CategoryService categoryService;
    private final TagService tagService;

    @Operation(summary = "分类列表")
    @GetMapping("/categories")
    public ApiResponse<List<CategoryVO>> categories() {
        return ApiResponse.ok(categoryService.list());
    }

    @Operation(summary = "标签列表")
    @GetMapping("/tags")
    public ApiResponse<List<TagVO>> tags() {
        return ApiResponse.ok(tagService.list());
    }
}
