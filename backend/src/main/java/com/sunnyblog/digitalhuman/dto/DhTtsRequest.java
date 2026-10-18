package com.sunnyblog.digitalhuman.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DhTtsRequest(
        @NotBlank String characterCode,
        @NotBlank @Size(max = 500) String text
) {
}
