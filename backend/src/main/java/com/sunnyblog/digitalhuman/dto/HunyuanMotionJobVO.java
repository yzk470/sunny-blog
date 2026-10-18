package com.sunnyblog.digitalhuman.dto;

public record HunyuanMotionJobVO(
        String id,
        String status,
        String prompt,
        String fileUrl,
        String remoteUrl,
        String error,
        boolean configured
) {
}
