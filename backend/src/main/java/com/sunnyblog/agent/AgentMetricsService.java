package com.sunnyblog.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics facade. It keeps a fast in-memory view and persists the durable
 * projection to the database when available.
 */
@Service
@RequiredArgsConstructor
public class AgentMetricsService {

    private final AgentMetricsRepository repository;

    private final AtomicLong tasks = new AtomicLong();
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong failedAttempts = new AtomicLong();
    private final AtomicLong steps = new AtomicLong();
    private final AtomicLong highValueTasks = new AtomicLong();
    private final AtomicLong highValueSuccesses = new AtomicLong();
    private final AtomicLong branches = new AtomicLong();
    private final Set<String> recorded = ConcurrentHashMap.newKeySet();

    public record Snapshot(long tasks, long successes, long failures, double successRate,
                           long attempts, long failedAttempts, double retryRate,
                           long steps, double averageSteps,
                           long highValueTasks, long highValueSuccesses,
                           long branchesExpanded,
                           long capabilityTasks, long capabilitySuccesses, double capabilitySuccessRate,
                           long securityTasks, long securityPasses, double securityPassRate,
                           long scopeViolations, long productionTasks) {
    }

    public void record(AgentRunService.AgentRun run) {
        if (run == null || !recorded.add(run.getId())) return;
        AgentRunService.Checkpoint checkpoint = run.getCheckpoint();
        int attemptCount = checkpoint == null || checkpoint.getAttempts() == null
                ? 0 : checkpoint.getAttempts().size();
        int failed = checkpoint == null || checkpoint.getAttempts() == null
                ? 0 : (int) checkpoint.getAttempts().stream()
                .filter(a -> "FAILED".equals(a.getStatus())).count();
        boolean success = checkpoint != null && checkpoint.getAttempts() != null
                && checkpoint.getAttempts().stream().anyMatch(a -> "SUCCESS".equals(a.getStatus()));
        tasks.incrementAndGet();
        if (success) successes.incrementAndGet();
        else failures.incrementAndGet();
        attempts.addAndGet(attemptCount);
        failedAttempts.addAndGet(failed);
        steps.addAndGet(checkpoint == null ? 0 : checkpoint.getTaskSpent());
        if (checkpoint != null && checkpoint.isHighValue()) {
            highValueTasks.incrementAndGet();
            if (success) highValueSuccesses.incrementAndGet();
            if (checkpoint.getSearch() != null) {
                branches.addAndGet(checkpoint.getSearch().getBranchesExpanded());
            }
        }
        repository.save(run);
    }

    public Snapshot snapshot() {
        return summary(3650);
    }

    public Snapshot summary(int days) {
        AgentMetricsRepository.Summary db = repository.summary(days);
        if (db.tasks() > 0) {
            return new Snapshot(db.tasks(), db.successes(), db.failures(), db.successRate(),
                    db.attempts(), db.failedAttempts(), db.retryRate(), db.steps(),
                    db.averageSteps(), db.highValueTasks(), db.highValueSuccesses(),
                    db.branchesExpanded(), db.capabilityTasks(), db.capabilitySuccesses(),
                    db.capabilitySuccessRate(), db.securityTasks(), db.securityPasses(),
                    db.securityPassRate(), db.scopeViolations(), db.productionTasks());
        }
        long t = tasks.get();
        long a = attempts.get();
        return new Snapshot(t, successes.get(), failures.get(),
                t == 0 ? 0 : (double) successes.get() / t,
                a, failedAttempts.get(), a == 0 ? 0 : (double) failedAttempts.get() / a,
                steps.get(), t == 0 ? 0 : (double) steps.get() / t,
                highValueTasks.get(), highValueSuccesses.get(), branches.get(),
                0, 0, 0, 0, 0, 0, 0, t);
    }

    public List<AgentMetricsRepository.DailyRow> trend(int days) {
        return repository.trend(days);
    }

    public List<Map<String, Object>> failures(int days) {
        return repository.failures(days);
    }

    public List<AgentMetricsRepository.TaskRow> recentTasks(int limit) {
        return repository.recentTasks(limit);
    }

    public List<AgentMetricsRepository.TaskRow> recentTasks(int limit, String failureType, String testGroup) {
        return repository.recentTasks(limit, failureType, testGroup);
    }
}
