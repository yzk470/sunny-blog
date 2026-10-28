package com.sunnyblog.digitalhuman.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Durable trace of article-tool calls made during a digital-human turn. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalHumanToolCallService {

    private final JdbcTemplate jdbc;

    public record ToolCallRow(long id, Long sessionId, String toolCallId, String toolName,
                              String arguments, String result, LocalDateTime createdAt) {
    }

    @PostConstruct
    public void init() {
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS dh_tool_call (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      session_id BIGINT,
                      tool_call_id VARCHAR(80),
                      tool_name VARCHAR(80),
                      arguments TEXT,
                      result TEXT,
                      created_at DATETIME
                    )
                    """);
            try {
                jdbc.execute("ALTER TABLE dh_tool_call ADD COLUMN tool_call_id VARCHAR(80)");
            } catch (Exception ignored) {
                // Column already exists on an initialized database.
            }
        } catch (Exception ex) {
            log.warn("Digital human tool-call table unavailable: {}", ex.getMessage());
        }
    }

    public void record(Long sessionId, String toolName, String arguments, String result) {
        try {
            jdbc.update("""
                    INSERT INTO dh_tool_call
                    (session_id, tool_call_id, tool_name, arguments, result, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, sessionId, "call_" + UUID.randomUUID().toString().replace("-", ""),
                    toolName, clip(arguments, 4000), clip(result, 12000), LocalDateTime.now());
        } catch (Exception ex) {
            log.warn("Digital human tool-call write skipped: {}", ex.getMessage());
        }
    }

    public List<ToolCallRow> list(Long sessionId) {
        try {
            return jdbc.query("""
                            SELECT id, session_id, tool_call_id, tool_name, arguments, result, created_at
                            FROM dh_tool_call WHERE session_id = ? ORDER BY created_at ASC, id ASC
                            """,
                    (rs, rowNum) -> new ToolCallRow(
                            rs.getLong("id"),
                            rs.getLong("session_id"),
                            rs.getString("tool_call_id"),
                            rs.getString("tool_name"),
                            rs.getString("arguments"),
                            rs.getString("result"),
                            rs.getTimestamp("created_at").toLocalDateTime()),
                    sessionId);
        } catch (Exception ex) {
            log.warn("Digital human tool-call read skipped: {}", ex.getMessage());
            return List.of();
        }
    }

    private String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
