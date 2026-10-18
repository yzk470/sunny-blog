package com.sunnyblog.dto;

import java.time.LocalDateTime;

public record ArchiveItemVO(Long id, String title, String slug, LocalDateTime publishedAt) {
}
