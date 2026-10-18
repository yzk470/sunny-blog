package com.sunnyblog.dto;

/**
 * Result returned after an admin image upload.
 */
public record UploadResult(String url, String name, long size) {
}
