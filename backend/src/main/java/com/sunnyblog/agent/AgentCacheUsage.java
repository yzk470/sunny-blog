package com.sunnyblog.agent;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Provider-side prompt-cache telemetry. DeepSeek returns
 * prompt_cache_hit_tokens / prompt_cache_miss_tokens (also exposed through
 * OpenAiUsage.promptTokensDetails.cachedTokens in Spring AI).
 */
@Service
public class AgentCacheUsage {

    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong cachedTokens = new AtomicLong();
    private final AtomicLong missTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();

    public record Snapshot(long calls, long promptTokens, long cachedTokens, long missTokens,
                           long completionTokens, double hitRate) {
    }

    public void record(Number prompt, Number cached, Number completion) {
        long p = prompt == null ? 0 : Math.max(0, prompt.longValue());
        long c = cached == null ? 0 : Math.max(0, cached.longValue());
        if (c > p) c = p;
        calls.incrementAndGet();
        promptTokens.addAndGet(p);
        cachedTokens.addAndGet(c);
        missTokens.addAndGet(p - c);
        completionTokens.addAndGet(completion == null ? 0 : Math.max(0, completion.longValue()));
    }

    public Snapshot snapshot() {
        long prompt = promptTokens.get();
        long cached = cachedTokens.get();
        return new Snapshot(calls.get(), prompt, cached, missTokens.get(), completionTokens.get(),
                prompt == 0 ? 0.0 : (double) cached / prompt);
    }

    public Snapshot reset() {
        Snapshot before = snapshot();
        calls.set(0);
        promptTokens.set(0);
        cachedTokens.set(0);
        missTokens.set(0);
        completionTokens.set(0);
        return before;
    }
}
