package com.sunnyblog.agent;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Durable metrics projection. It is intentionally append/replace only:
 * one task row and one attempt row per attempt id, so SSE retries and
 * checkpoint resumes do not double count.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AgentMetricsRepository {

    private final JdbcTemplate jdbc;

    public record TaskRow(String runId, String sessionId, String mode, boolean highValue,
                          String status, int taskBudget, int taskSpent, int attemptCount,
                          int failedAttemptCount, int branchExpanded, boolean success,
                          long durationMs, String createdAt) {
    }

    public record AttemptRow(String attemptId, String runId, String strategyId, String searchNodeId,
                             String status, String failureType, int budgetAllocated,
                             int budgetSpent, int searchScore, int depth, String createdAt) {
    }

    public record DailyRow(String day, long tasks, long successes, long attempts,
                           long failedAttempts, long steps, long highValueTasks,
                           long highValueSuccesses, long branches) {
    }

    public record Summary(long tasks, long successes, long failures, double successRate,
                          long attempts, long failedAttempts, double retryRate,
                          long steps, double averageSteps, long highValueTasks,
                          long highValueSuccesses, long branchesExpanded) {
    }

    @PostConstruct
    public void init() {
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS agent_task_metric (
                      run_id VARCHAR(64) PRIMARY KEY,
                      session_id VARCHAR(64),
                      mode VARCHAR(16),
                      high_value BOOLEAN,
                      status VARCHAR(24),
                      task_budget INT,
                      task_spent INT,
                      attempt_count INT,
                      failed_attempt_count INT,
                      branch_expanded INT,
                      success BOOLEAN,
                      duration_ms BIGINT,
                      created_at DATETIME
                    )
                    """);
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS agent_attempt_metric (
                      attempt_id VARCHAR(64) PRIMARY KEY,
                      run_id VARCHAR(64),
                      strategy_id VARCHAR(64),
                      search_node_id VARCHAR(64),
                      status VARCHAR(24),
                      failure_type VARCHAR(32),
                      budget_allocated INT,
                      budget_spent INT,
                      search_score INT,
                      depth INT,
                      created_at DATETIME
                    )
                    """);
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS agent_metric_daily (
                      day DATE PRIMARY KEY,
                      tasks BIGINT,
                      successes BIGINT,
                      attempts BIGINT,
                      failed_attempts BIGINT,
                      steps BIGINT,
                      high_value_tasks BIGINT,
                      high_value_successes BIGINT,
                      branch_expansions BIGINT,
                      updated_at DATETIME
                    )
                    """);
        } catch (Exception ex) {
            log.warn("Agent metrics tables unavailable: {}", ex.getMessage());
        }
    }

    public synchronized void save(AgentRunService.AgentRun run) {
        try {
            AgentRunService.Checkpoint cp = run.getCheckpoint();
            if (cp == null) return;
            List<AgentRunService.AttemptRecord> attempts = cp.getAttempts() == null
                    ? List.of() : cp.getAttempts();
            int failed = (int) attempts.stream().filter(a -> "FAILED".equals(a.getStatus())).count();
            boolean success = attempts.stream().anyMatch(a -> "SUCCESS".equals(a.getStatus()));
            Timestamp created = timestamp(run.getCreatedAt());
            Timestamp updated = timestamp(run.getUpdatedAt());
            long duration = Math.max(0, updated.getTime() - created.getTime());
            jdbc.update("DELETE FROM agent_task_metric WHERE run_id = ?", run.getId());
            jdbc.update("""
                            INSERT INTO agent_task_metric
                            (run_id, session_id, mode, high_value, status, task_budget, task_spent,
                             attempt_count, failed_attempt_count, branch_expanded, success, duration_ms, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    run.getId(), run.getSessionId(), run.getMode(), cp.isHighValue(), run.getStatus(),
                    cp.getTaskBudget(), cp.getTaskSpent(), attempts.size(), failed,
                    cp.getSearch() == null ? 0 : cp.getSearch().getBranchesExpanded(),
                    success, duration, created);
            jdbc.update("DELETE FROM agent_attempt_metric WHERE run_id = ?", run.getId());
            for (AgentRunService.AttemptRecord attempt : attempts) {
                int depth = 0;
                if (cp.getSearch() != null && cp.getSearch().getNodes() != null) {
                    for (AgentRunService.SearchNode node : cp.getSearch().getNodes()) {
                        if (attempt.getSearchNodeId() != null
                                && attempt.getSearchNodeId().equals(node.getId())) {
                            depth = node.getDepth();
                            break;
                        }
                    }
                }
                jdbc.update("""
                                INSERT INTO agent_attempt_metric
                                (attempt_id, run_id, strategy_id, search_node_id, status, failure_type,
                                 budget_allocated, budget_spent, search_score, depth, created_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        attempt.getId(), run.getId(), attempt.getStrategyId(), attempt.getSearchNodeId(),
                        attempt.getStatus(), attempt.getFailureType(), attempt.getBudgetAllocated(),
                        attempt.getBudgetSpent(), attempt.getSearchScore(), depth,
                        timestamp(attempt.getStartedAt()));
            }
            rollup(LocalDate.now());
        } catch (Exception ex) {
            log.warn("Agent metrics save failed for {}: {}", run.getId(), ex.getMessage());
        }
    }

    public Summary summary(int days) {
        try {
            Timestamp since = Timestamp.from(Instant.now().minusSeconds(Math.max(1, days) * 86400L));
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT COUNT(*) AS tasks,
                           COALESCE(SUM(CASE WHEN success THEN 1 ELSE 0 END),0) AS successes,
                           COALESCE(SUM(CASE WHEN success THEN 0 ELSE 1 END),0) AS failures,
                           COALESCE(SUM(attempt_count),0) AS attempts,
                           COALESCE(SUM(failed_attempt_count),0) AS failed_attempts,
                           COALESCE(SUM(task_spent),0) AS steps,
                           COALESCE(SUM(CASE WHEN high_value THEN 1 ELSE 0 END),0) AS hv_tasks,
                           COALESCE(SUM(CASE WHEN high_value AND success THEN 1 ELSE 0 END),0) AS hv_success,
                           COALESCE(SUM(branch_expanded),0) AS branches
                    FROM agent_task_metric WHERE created_at >= ?
                    """, since);
            long tasks = number(row.get("tasks"));
            long successes = number(row.get("successes"));
            long attempts = number(row.get("attempts"));
            long failedAttempts = number(row.get("failed_attempts"));
            long steps = number(row.get("steps"));
            return new Summary(tasks, successes, number(row.get("failures")),
                    tasks == 0 ? 0 : (double) successes / tasks,
                    attempts, failedAttempts,
                    attempts == 0 ? 0 : (double) failedAttempts / attempts,
                    steps, tasks == 0 ? 0 : (double) steps / tasks,
                    number(row.get("hv_tasks")), number(row.get("hv_success")),
                    number(row.get("branches")));
        } catch (Exception ex) {
            return new Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public List<DailyRow> trend(int days) {
        List<DailyRow> result = new ArrayList<>();
        try {
            Timestamp since = Timestamp.from(Instant.now().minusSeconds(Math.max(1, days) * 86400L));
            jdbc.query("""
                    SELECT DATE(created_at) AS day,
                           COUNT(*) AS tasks,
                           COALESCE(SUM(CASE WHEN success THEN 1 ELSE 0 END),0) AS successes,
                           COALESCE(SUM(attempt_count),0) AS attempts,
                           COALESCE(SUM(failed_attempt_count),0) AS failed_attempts,
                           COALESCE(SUM(task_spent),0) AS steps,
                           COALESCE(SUM(CASE WHEN high_value THEN 1 ELSE 0 END),0) AS hv_tasks,
                           COALESCE(SUM(CASE WHEN high_value AND success THEN 1 ELSE 0 END),0) AS hv_success,
                           COALESCE(SUM(branch_expanded),0) AS branches
                    FROM agent_task_metric WHERE created_at >= ?
                    GROUP BY DATE(created_at) ORDER BY day
                    """, (rs, rowNum) -> new DailyRow(String.valueOf(rs.getDate("day")),
                    rs.getLong("tasks"), rs.getLong("successes"), rs.getLong("attempts"),
                    rs.getLong("failed_attempts"), rs.getLong("steps"),
                    rs.getLong("hv_tasks"), rs.getLong("hv_success"), rs.getLong("branches")), since);
        } catch (Exception ignored) {
        }
        return result;
    }

    public List<Map<String, Object>> failures(int days) {
        try {
            Timestamp since = Timestamp.from(Instant.now().minusSeconds(Math.max(1, days) * 86400L));
            return jdbc.queryForList("""
                    SELECT failure_type, COUNT(*) AS count
                    FROM agent_attempt_metric
                    WHERE failure_type IS NOT NULL AND failure_type <> '' AND created_at >= ?
                    GROUP BY failure_type ORDER BY count DESC
                    """, since);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<TaskRow> recentTasks(int limit) {
        try {
            return jdbc.query("""
                    SELECT run_id, session_id, mode, high_value, status, task_budget, task_spent,
                           attempt_count, failed_attempt_count, branch_expanded, success, duration_ms, created_at
                    FROM agent_task_metric ORDER BY created_at DESC LIMIT ?
                    """, (rs, rowNum) -> new TaskRow(
                    rs.getString("run_id"), rs.getString("session_id"), rs.getString("mode"),
                    rs.getBoolean("high_value"), rs.getString("status"), rs.getInt("task_budget"),
                    rs.getInt("task_spent"), rs.getInt("attempt_count"),
                    rs.getInt("failed_attempt_count"), rs.getInt("branch_expanded"),
                    rs.getBoolean("success"), rs.getLong("duration_ms"),
                    String.valueOf(rs.getTimestamp("created_at"))), Math.max(1, limit));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void rollup(LocalDate day) {
        // Keep the daily aggregate table useful without a separate scheduler.
        try {
            jdbc.update("DELETE FROM agent_metric_daily WHERE day = ?", day);
            jdbc.update("""
                            INSERT INTO agent_metric_daily
                            (day, tasks, successes, attempts, failed_attempts, steps,
                             high_value_tasks, high_value_successes, branch_expansions, updated_at)
                            SELECT ?, COUNT(*),
                                   COALESCE(SUM(CASE WHEN success THEN 1 ELSE 0 END),0),
                                   COALESCE(SUM(attempt_count),0),
                                   COALESCE(SUM(failed_attempt_count),0),
                                   COALESCE(SUM(task_spent),0),
                                   COALESCE(SUM(CASE WHEN high_value THEN 1 ELSE 0 END),0),
                                   COALESCE(SUM(CASE WHEN high_value AND success THEN 1 ELSE 0 END),0),
                                   COALESCE(SUM(branch_expanded),0), NOW()
                            FROM agent_task_metric WHERE DATE(created_at) = ?
                            """, day, day);
        } catch (Exception ignored) {
        }
    }

    private Timestamp timestamp(String value) {
        try {
            return value == null ? Timestamp.from(Instant.now()) : Timestamp.from(Instant.parse(value));
        } catch (Exception ex) {
            return Timestamp.from(Instant.now());
        }
    }

    private long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
