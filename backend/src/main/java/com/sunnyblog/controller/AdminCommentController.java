package com.sunnyblog.controller;

import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.CommentVO;
import com.sunnyblog.dto.request.StatusRequest;
import com.sunnyblog.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台 · 评论管理")
@RestController
@RequestMapping("/api/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final CommentService commentService;

    @Operation(summary = "评论列表")
    @GetMapping
    public ApiResponse<PageResult<CommentVO>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(commentService.adminList(status, page, size));
    }

    @Operation(summary = "审核评论")
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> moderate(@PathVariable Long id,
                                      @Valid @RequestBody StatusRequest request) {
        commentService.moderate(id, request.status());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ApiResponse.ok();
    }
}
