package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Durable-ish async metrics buffer. Producers enqueue a completed run; a
 * scheduled consumer batches the projection into MySQL. Redis keeps the
 * request path off the database write critical path during parallel runs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMetricsAsyncQueue {

    private static final String QUEUE_KEY = "agent:metrics:queue";
    private static final int BATCH_SIZE = 50;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AgentMetricsRepository repository;
    private final AtomicBoolean redisAvailable = new AtomicBoolean(true);

    @Value("${app.agent.metrics.async-enabled:true}")
    private boolean enabled = true;

    /** @return true when the run was accepted by Redis. */
    public boolean enqueue(AgentRunService.AgentRun run) {
        if (!enabled || !redisAvailable.get()) return false;
        try {
            redis.opsForList().leftPush(QUEUE_KEY, objectMapper.writeValueAsString(run));
            return true;
        } catch (Exception ex) {
            redisAvailable.set(false);
            log.warn("Agent metrics Redis unavailable, falling back to direct writes: {}", ex.getMessage());
            return false;
        }
    }

    @Scheduled(fixedDelayString = "${app.agent.metrics.flush-ms:1000}", initialDelay = 1000)
    public void drain() {
        if (!enabled || !redisAvailable.get()) return;
        List<AgentRunService.AgentRun> batch = new ArrayList<>(BATCH_SIZE);
        try {
            for (int i = 0; i < BATCH_SIZE; i++) {
                String payload = redis.opsForList().rightPop(QUEUE_KEY);
                if (payload == null) break;
                try {
                    batch.add(objectMapper.readValue(payload, AgentRunService.AgentRun.class));
                } catch (Exception ex) {
                    log.warn("Discarding malformed metrics queue message: {}", ex.getMessage());
                }
            }
            if (!batch.isEmpty()) {
                repository.saveAll(batch);
                log.debug("Flushed {} agent metrics rows", batch.size());
            }
        } catch (Exception ex) {
            redisAvailable.set(false);
            log.warn("Agent metrics queue drain failed: {}", ex.getMessage());
        }
    }

    // Do not drain from @PreDestroy: Spring stops Redis before this bean.
    // Any remaining messages stay in Redis and are consumed after restart.
}
