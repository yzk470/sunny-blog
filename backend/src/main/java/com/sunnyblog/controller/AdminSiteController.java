package com.sunnyblog.controller;

import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.dto.DashboardVO;
import com.sunnyblog.dto.SiteProfileVO;
import com.sunnyblog.dto.request.SiteProfileRequest;
import com.sunnyblog.service.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台 · 站点设置")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSiteController {

    private final SiteService siteService;

    @Operation(summary = "数据概览")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardVO> dashboard() {
        return ApiResponse.ok(siteService.getDashboard());
    }

    @Operation(summary = "站点资料")
    @GetMapping("/site")
    public ApiResponse<SiteProfileVO> profile() {
        return ApiResponse.ok(siteService.getProfile());
    }

    @Operation(summary = "更新站点资料")
    @PutMapping("/site")
    public ApiResponse<SiteProfileVO> update(@Valid @RequestBody SiteProfileRequest request) {
        return ApiResponse.ok(siteService.updateProfile(request));
    }
}
