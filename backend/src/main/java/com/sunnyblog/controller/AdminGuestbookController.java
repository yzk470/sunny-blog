package com.sunnyblog.controller;

import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.GuestbookVO;
import com.sunnyblog.dto.request.ReplyRequest;
import com.sunnyblog.dto.request.StatusRequest;
import com.sunnyblog.service.GuestbookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台 · 留言管理")
@RestController
@RequestMapping("/api/admin/guestbook")
@RequiredArgsConstructor
public class AdminGuestbookController {

    private final GuestbookService guestbookService;

    @Operation(summary = "留言列表")
    @GetMapping
    public ApiResponse<PageResult<GuestbookVO>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(guestbookService.adminList(status, page, size));
    }

    @Operation(summary = "审核留言")
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> moderate(@PathVariable Long id,
                                      @Valid @RequestBody StatusRequest request) {
        guestbookService.moderate(id, request.status());
        return ApiResponse.ok();
    }

    @Operation(summary = "回复留言")
    @PutMapping("/{id}/reply")
    public ApiResponse<Void> reply(@PathVariable Long id, @Valid @RequestBody ReplyRequest request) {
        guestbookService.reply(id, request.reply());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除留言")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        guestbookService.delete(id);
        return ApiResponse.ok();
    }
}
