package com.sunnyblog.digitalhuman.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Durable trace of article-tool calls made during a digital-human turn. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalHumanToolCallService {

    private final JdbcTemplate jdbc;

    @PostConstruct
    public void init() {
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS dh_tool_call (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      session_id BIGINT,
                      tool_name VARCHAR(80),
                      arguments TEXT,
                      result TEXT,
                      created_at DATETIME
                    )
                    """);
        } catch (Exception ex) {
            log.warn("Digital human tool-call table unavailable: {}", ex.getMessage());
        }
    }

    public void record(Long sessionId, String toolName, String arguments, String result) {
        try {
            jdbc.update("""
                    INSERT INTO dh_tool_call (session_id, tool_name, arguments, result, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, sessionId, toolName, clip(arguments, 4000), clip(result, 12000),
                    LocalDateTime.now());
        } catch (Exception ex) {
            log.warn("Digital human tool-call write skipped: {}", ex.getMessage());
        }
    }

    private String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
