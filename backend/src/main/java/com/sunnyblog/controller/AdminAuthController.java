package com.sunnyblog.controller;

import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.dto.AuthUserVO;
import com.sunnyblog.dto.LoginResponse;
import com.sunnyblog.dto.request.LoginRequest;
import com.sunnyblog.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台 · 认证")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "当前登录管理员")
    @GetMapping("/me")
    public ApiResponse<AuthUserVO> me(@RequestAttribute("adminUserId") Long adminUserId) {
        return ApiResponse.ok(authService.me(adminUserId));
    }
}
