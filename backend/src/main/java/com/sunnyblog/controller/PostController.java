package com.sunnyblog.controller;

import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.ArchiveItemVO;
import com.sunnyblog.dto.PostDetailVO;
import com.sunnyblog.dto.PostSummaryVO;
import com.sunnyblog.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "前台 · 文章")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "文章分页列表")
    @GetMapping
    public ApiResponse<PageResult<PostSummaryVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "6") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort) {
        return ApiResponse.ok(postService.listPublished(page, size, category, tag, keyword, sort));
    }

    @Operation(summary = "最新文章")
    @GetMapping("/latest")
    public ApiResponse<List<PostSummaryVO>> latest(@RequestParam(defaultValue = "4") int limit) {
        return ApiResponse.ok(postService.latest(limit));
    }

    @Operation(summary = "置顶推荐文章")
    @GetMapping("/featured")
    public ApiResponse<List<PostSummaryVO>> featured(@RequestParam(defaultValue = "3") int limit) {
        return ApiResponse.ok(postService.featured(limit));
    }

    @Operation(summary = "归档列表")
    @GetMapping("/archive")
    public ApiResponse<List<ArchiveItemVO>> archive() {
        return ApiResponse.ok(postService.archive());
    }

    @Operation(summary = "文章详情")
    @GetMapping("/{slug}")
    public ApiResponse<PostDetailVO> detail(@PathVariable String slug) {
        return ApiResponse.ok(postService.getPublishedBySlug(slug));
    }

    @Operation(summary = "记录阅读量（Redis 计数）")
    @PostMapping("/{slug}/view")
    public ApiResponse<Map<String, Long>> view(@PathVariable String slug) {
        return ApiResponse.ok(Map.of("viewCount", postService.recordView(slug)));
    }

    @Operation(summary = "点赞")
    @PostMapping("/{slug}/like")
    public ApiResponse<Map<String, Long>> like(@PathVariable String slug) {
        return ApiResponse.ok(Map.of("likeCount", postService.like(slug)));
    }
}
