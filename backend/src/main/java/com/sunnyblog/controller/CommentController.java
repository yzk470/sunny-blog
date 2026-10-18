package com.sunnyblog.controller;

import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.CommentVO;
import com.sunnyblog.dto.request.CommentCreateRequest;
import com.sunnyblog.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "前台 · 评论")
@RestController
@RequestMapping("/api/posts/{slug}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "文章评论列表")
    @GetMapping
    public ApiResponse<PageResult<CommentVO>> list(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(commentService.listApprovedByPostSlug(slug, page, size));
    }

    @Operation(summary = "发表评论（需审核后展示）")
    @PostMapping
    public ApiResponse<CommentVO> create(@PathVariable String slug,
                                         @Valid @RequestBody CommentCreateRequest request) {
        return ApiResponse.ok(commentService.create(slug, request));
    }
}
