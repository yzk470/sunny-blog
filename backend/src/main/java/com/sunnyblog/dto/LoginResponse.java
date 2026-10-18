package com.sunnyblog.dto;

public record LoginResponse(String token, long expiresIn, AuthUserVO user) {
}
