package com.sunnyblog.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provider-side prompt-cache telemetry. The in-memory snapshot is the
 * per-process view; a scheduled delta flush persists the long-term daily
 * series used by the execution dashboard.
 */
@Service
@RequiredArgsConstructor
public class AgentCacheUsage {

    private static final int MIN_PEAK_PROMPT = 500;

    private final AgentCacheMetricRepository history;

    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong cachedTokens = new AtomicLong();
    private final AtomicLong missTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();

    private final AtomicLong pendingCalls = new AtomicLong();
    private final AtomicLong pendingPrompt = new AtomicLong();
    private final AtomicLong pendingCached = new AtomicLong();
    private final AtomicLong pendingMiss = new AtomicLong();
    private final AtomicLong pendingCompletion = new AtomicLong();
    private final AtomicReference<PeakCall> peak = new AtomicReference<>(new PeakCall(0, 0, 0));

    public record PeakCall(double hitRate, long promptTokens, long cachedTokens) {
    }

    public record Snapshot(long calls, long promptTokens, long cachedTokens, long missTokens,
                           long completionTokens, double hitRate,
                           double peakHitRate, long peakPromptTokens, long peakCachedTokens) {
    }

    public void record(Number prompt, Number cached, Number completion) {
        long p = prompt == null ? 0 : Math.max(0, prompt.longValue());
        long c = cached == null ? 0 : Math.max(0, cached.longValue());
        if (c > p) c = p;
        long miss = p - c;
        long out = completion == null ? 0 : Math.max(0, completion.longValue());
        calls.incrementAndGet();
        promptTokens.addAndGet(p);
        cachedTokens.addAndGet(c);
        missTokens.addAndGet(miss);
        completionTokens.addAndGet(out);
        pendingCalls.incrementAndGet();
        pendingPrompt.addAndGet(p);
        pendingCached.addAndGet(c);
        pendingMiss.addAndGet(miss);
        pendingCompletion.addAndGet(out);
        if (p >= MIN_PEAK_PROMPT) {
            final long peakCached = c;
            double rate = (double) peakCached / p;
            peak.updateAndGet(old -> rate > old.hitRate()
                    ? new PeakCall(rate, p, peakCached) : old);
        }
    }

    public Snapshot snapshot() {
        long prompt = promptTokens.get();
        long cached = cachedTokens.get();
        PeakCall max = peak.get();
        return new Snapshot(calls.get(), prompt, cached, missTokens.get(), completionTokens.get(),
                prompt == 0 ? 0.0 : (double) cached / prompt,
                max.hitRate(), max.promptTokens(), max.cachedTokens());
    }

    public Snapshot reset() {
        Snapshot before = snapshot();
        calls.set(0);
        promptTokens.set(0);
        cachedTokens.set(0);
        missTokens.set(0);
        completionTokens.set(0);
        pendingCalls.set(0);
        pendingPrompt.set(0);
        pendingCached.set(0);
        pendingMiss.set(0);
        pendingCompletion.set(0);
        peak.set(new PeakCall(0, 0, 0));
        return before;
    }

    /** Persist the current process delta; safe to call from tests or shutdown. */
    @Scheduled(fixedDelayString = "${app.agent.cache.flush-ms:10000}", initialDelay = 5000)
    public void flush() {
        long c = pendingCalls.getAndSet(0);
        long p = pendingPrompt.getAndSet(0);
        long hit = pendingCached.getAndSet(0);
        long miss = pendingMiss.getAndSet(0);
        long out = pendingCompletion.getAndSet(0);
        if (c == 0 && p == 0) return;
        history.addDelta(LocalDate.now(), c, p, hit, miss, out);
    }

    public List<AgentCacheMetricRepository.DayRow> history(int days) {
        return history.history(days);
    }
}

