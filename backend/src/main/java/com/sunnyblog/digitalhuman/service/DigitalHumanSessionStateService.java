package com.sunnyblog.digitalhuman.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Per-session canonical-context state: article context, incremental summary and
 * the message id covered by that summary. Keeping these outside the message
 * table means history stays append-only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalHumanSessionStateService {

    private final JdbcTemplate jdbc;

    public record State(Long sessionId, String summary, long summaryUptoId,
                        String contextKey, String contextText) {
    }

    @PostConstruct
    public void init() {
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS dh_session_state (
                      session_id BIGINT PRIMARY KEY,
                      summary TEXT,
                      summary_upto_id BIGINT DEFAULT 0,
                      context_key VARCHAR(255),
                      context_text TEXT,
                      updated_at DATETIME
                    )
                    """);
        } catch (Exception ex) {
            log.warn("Digital human session state table unavailable: {}", ex.getMessage());
        }
    }

    public State get(Long sessionId) {
        try {
            return jdbc.query("""
                            SELECT session_id, summary, summary_upto_id, context_key, context_text
                            FROM dh_session_state WHERE session_id = ?
                            """,
                    rs -> rs.next() ? new State(
                            rs.getLong("session_id"),
                            rs.getString("summary"),
                            rs.getLong("summary_upto_id"),
                            rs.getString("context_key"),
                            rs.getString("context_text")) : new State(sessionId, "", 0, "", ""),
                    sessionId);
        } catch (Exception ex) {
            log.warn("Digital human session state read skipped: {}", ex.getMessage());
            return new State(sessionId, "", 0, "", "");
        }
    }

    public void updateContext(Long sessionId, String key, String text) {
        try {
            jdbc.update("""
                    INSERT INTO dh_session_state
                    (session_id, context_key, context_text, summary, summary_upto_id, updated_at)
                    VALUES (?, ?, ?, '', 0, ?)
                    ON DUPLICATE KEY UPDATE
                      context_key = VALUES(context_key),
                      context_text = VALUES(context_text),
                      updated_at = VALUES(updated_at)
                    """, sessionId, key, text, LocalDateTime.now());
        } catch (Exception ex) {
            log.warn("Digital human context write skipped: {}", ex.getMessage());
        }
    }

    public void updateSummary(Long sessionId, String summary, long uptoId) {
        try {
            jdbc.update("""
                    INSERT INTO dh_session_state
                    (session_id, summary, summary_upto_id, context_key, context_text, updated_at)
                    VALUES (?, ?, ?, '', '', ?)
                    ON DUPLICATE KEY UPDATE
                      summary = VALUES(summary),
                      summary_upto_id = VALUES(summary_upto_id),
                      updated_at = VALUES(updated_at)
                    """, sessionId, summary, uptoId, LocalDateTime.now());
        } catch (Exception ex) {
            log.warn("Digital human summary write skipped: {}", ex.getMessage());
        }
    }
}
