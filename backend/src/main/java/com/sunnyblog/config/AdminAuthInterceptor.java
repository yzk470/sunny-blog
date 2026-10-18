package com.sunnyblog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, "请先登录后台");
        }
        try {
            Claims claims = jwtService.parse(header.substring(7));
            request.setAttribute("adminUserId", Long.valueOf(claims.getSubject()));
            request.setAttribute("adminUsername", claims.get("username", String.class));
            return true;
        } catch (Exception ex) {
            log.debug("Invalid admin token: {}", ex.getMessage());
            return reject(response, "登录状态已过期，请重新登录");
        }
    }

    private boolean reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(401, message)));
        return false;
    }
}
