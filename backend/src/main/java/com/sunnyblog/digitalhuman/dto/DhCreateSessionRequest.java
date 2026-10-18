package com.sunnyblog.digitalhuman.dto;

import jakarta.validation.constraints.NotBlank;

public record DhCreateSessionRequest(
        @NotBlank(message = "请选择数字人角色") String characterCode
) {
}
