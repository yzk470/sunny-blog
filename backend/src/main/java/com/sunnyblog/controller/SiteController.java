package com.sunnyblog.controller;

import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.GuestbookVO;
import com.sunnyblog.dto.SiteProfileVO;
import com.sunnyblog.dto.SiteStatsVO;
import com.sunnyblog.dto.request.GuestbookCreateRequest;
import com.sunnyblog.service.GuestbookService;
import com.sunnyblog.service.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "前台 · 站点")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;
    private final GuestbookService guestbookService;

    @Operation(summary = "站点资料")
    @GetMapping("/site")
    public ApiResponse<SiteProfileVO> profile() {
        return ApiResponse.ok(siteService.getProfile());
    }

    @Operation(summary = "站点统计")
    @GetMapping("/stats")
    public ApiResponse<SiteStatsVO> stats() {
        return ApiResponse.ok(siteService.getStats());
    }

    @Operation(summary = "留言列表")
    @GetMapping("/guestbook")
    public ApiResponse<PageResult<GuestbookVO>> guestbook(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(guestbookService.listApproved(page, size));
    }

    @Operation(summary = "写下留言（需审核后展示）")
    @PostMapping("/guestbook")
    public ApiResponse<GuestbookVO> leaveMessage(@Valid @RequestBody GuestbookCreateRequest request) {
        return ApiResponse.ok(guestbookService.create(request));
    }
}
