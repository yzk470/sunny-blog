package com.sunnyblog.agent;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Durable daily rollup for provider prompt-cache usage. The in-memory
 * AgentCacheUsage counters reset on restart; this table is the long-term series
 * used by the execution dashboard.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AgentCacheMetricRepository {

    private final JdbcTemplate jdbc;

    public record DayRow(String day, long calls, long promptTokens, long cachedTokens,
                         long missTokens, long completionTokens, double hitRate) {
    }

    @PostConstruct
    public void init() {
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS agent_cache_daily (
                      day DATE PRIMARY KEY,
                      calls BIGINT DEFAULT 0,
                      prompt_tokens BIGINT DEFAULT 0,
                      cached_tokens BIGINT DEFAULT 0,
                      miss_tokens BIGINT DEFAULT 0,
                      completion_tokens BIGINT DEFAULT 0,
                      updated_at DATETIME
                    )
                    """);
        } catch (Exception ex) {
            log.warn("Agent cache metric table unavailable: {}", ex.getMessage());
        }
    }

    public synchronized void addDelta(LocalDate day, long calls, long promptTokens,
                                      long cachedTokens, long missTokens, long completionTokens) {
        if (calls <= 0 && promptTokens <= 0) return;
        try {
            jdbc.update("""
                    INSERT INTO agent_cache_daily
                    (day, calls, prompt_tokens, cached_tokens, miss_tokens, completion_tokens, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, NOW())
                    ON DUPLICATE KEY UPDATE
                      calls = calls + VALUES(calls),
                      prompt_tokens = prompt_tokens + VALUES(prompt_tokens),
                      cached_tokens = cached_tokens + VALUES(cached_tokens),
                      miss_tokens = miss_tokens + VALUES(miss_tokens),
                      completion_tokens = completion_tokens + VALUES(completion_tokens),
                      updated_at = NOW()
                    """, day, calls, promptTokens, cachedTokens, missTokens, completionTokens);
        } catch (Exception ex) {
            log.warn("Agent cache metric write skipped: {}", ex.getMessage());
        }
    }

    public List<DayRow> history(int days) {
        int safe = Math.max(1, Math.min(90, days));
        try {
            return jdbc.query("""
                            SELECT day, calls, prompt_tokens, cached_tokens, miss_tokens, completion_tokens
                            FROM agent_cache_daily
                            WHERE day >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                            ORDER BY day ASC
                            """,
                    (rs, rowNum) -> {
                        long prompt = rs.getLong("prompt_tokens");
                        long cached = rs.getLong("cached_tokens");
                        return new DayRow(
                                rs.getDate("day").toLocalDate().toString(),
                                rs.getLong("calls"), prompt, cached,
                                rs.getLong("miss_tokens"), rs.getLong("completion_tokens"),
                                prompt == 0 ? 0.0 : (double) cached / prompt);
                    }, safe);
        } catch (Exception ex) {
            log.warn("Agent cache history unavailable: {}", ex.getMessage());
            return List.of();
        }
    }
}
