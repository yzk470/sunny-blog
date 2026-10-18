package com.sunnyblog.controller;

import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.AdminPostVO;
import com.sunnyblog.dto.PostSummaryVO;
import com.sunnyblog.dto.request.PostSaveRequest;
import com.sunnyblog.dto.request.StatusRequest;
import com.sunnyblog.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台 · 文章管理")
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;

    @Operation(summary = "后台文章列表")
    @GetMapping
    public ApiResponse<PageResult<PostSummaryVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(postService.adminList(page, size, status, keyword));
    }

    @Operation(summary = "后台文章详情")
    @GetMapping("/{id}")
    public ApiResponse<AdminPostVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(postService.getAdmin(id));
    }

    @Operation(summary = "新建文章")
    @PostMapping
    public ApiResponse<AdminPostVO> create(@Valid @RequestBody PostSaveRequest request) {
        return ApiResponse.ok(postService.create(request));
    }

    @Operation(summary = "更新文章")
    @PutMapping("/{id}")
    public ApiResponse<AdminPostVO> update(@PathVariable Long id,
                                           @Valid @RequestBody PostSaveRequest request) {
        return ApiResponse.ok(postService.update(id, request));
    }

    @Operation(summary = "发布/撤回文章")
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id,
                                          @Valid @RequestBody StatusRequest request) {
        postService.changeStatus(id, request.status());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除文章")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ApiResponse.ok();
    }
}
