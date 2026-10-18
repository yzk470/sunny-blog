package com.sunnyblog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 轻量 Redis 缓存门面：
 * 1. 每个命名空间带一个版本号，数据变更时只递增版本号即可让旧缓存自然失效，避免 KEYS 扫描；
 * 2. Redis 不可用时静默降级为直接查库，不影响博客主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private static final String PREFIX = "sunnyblog:";
    private static final String VERSION_PREFIX = PREFIX + "ver:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public <T> T get(String namespace, String key, TypeReference<T> type) {
        try {
            String version = currentVersion(namespace);
            String json = redis.opsForValue().get(dataKey(namespace, version, key));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            log.warn("Redis cache read skipped [{}:{}]: {}", namespace, key, ex.getMessage());
            return null;
        }
    }

    public void put(String namespace, String key, Object value, Duration ttl) {
        try {
            String version = currentVersion(namespace);
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(dataKey(namespace, version, key), json, ttl);
        } catch (Exception ex) {
            log.warn("Redis cache write skipped [{}:{}]: {}", namespace, key, ex.getMessage());
        }
    }

    public void bump(String... namespaces) {
        for (String namespace : namespaces) {
            try {
                redis.opsForValue().increment(VERSION_PREFIX + namespace);
            } catch (Exception ex) {
                log.warn("Redis cache version bump skipped [{}]: {}", namespace, ex.getMessage());
            }
        }
    }

    private String currentVersion(String namespace) {
        String version = redis.opsForValue().get(VERSION_PREFIX + namespace);
        return version == null ? "0" : version;
    }

    private String dataKey(String namespace, String version, String key) {
        return PREFIX + namespace + ":" + version + ":" + key;
    }
}
