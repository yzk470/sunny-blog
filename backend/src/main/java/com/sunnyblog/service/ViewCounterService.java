package com.sunnyblog.service;

import com.sunnyblog.common.BlogConstants;
import com.sunnyblog.entity.Post;
import com.sunnyblog.mapper.PostMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 阅读量先写 Redis，再定时批量回写数据库，减少热门文章的写库压力。
 * Redis 不可用时自动退化为直接更新数据库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCounterService {

    private final StringRedisTemplate redis;
    private final PostMapper postMapper;

    /**
     * @return Redis 中的待回写增量；Redis 不可用时返回 -1，并已直接写入数据库。
     */
    public long increment(Long postId) {
        try {
            Long value = redis.opsForHash().increment(BlogConstants.REDIS_VIEW_HASH, String.valueOf(postId), 1);
            return value == null ? 0 : value;
        } catch (Exception ex) {
            log.warn("Redis view counter unavailable, writing to DB directly: {}", ex.getMessage());
            postMapper.incrementViews(postId, 1);
            return -1;
        }
    }

    public long pending(Long postId) {
        try {
            Object value = redis.opsForHash().get(BlogConstants.REDIS_VIEW_HASH, String.valueOf(postId));
            return value == null ? 0 : Long.parseLong(value.toString());
        } catch (Exception ex) {
            return 0;
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void scheduledFlush() {
        flush();
    }

    public synchronized void flush() {
        try {
            Map<Object, Object> pending = redis.opsForHash().entries(BlogConstants.REDIS_VIEW_HASH);
            if (pending.isEmpty()) {
                return;
            }
            int count = 0;
            for (Map.Entry<Object, Object> entry : pending.entrySet()) {
                Long postId = Long.valueOf(entry.getKey().toString());
                long delta = Long.parseLong(entry.getValue().toString());
                if (delta > 0 && postMapper.incrementViews(postId, delta) > 0) {
                    redis.opsForHash().delete(BlogConstants.REDIS_VIEW_HASH, entry.getKey());
                    count++;
                }
            }
            if (count > 0) {
                log.info("Flushed live view counts for {} posts into database", count);
            }
        } catch (Exception ex) {
            log.warn("Skipping view counter flush: {}", ex.getMessage());
        }
    }

    @PreDestroy
    public void onShutdown() {
        flush();
    }

    public long liveCount(Post post) {
        return (post.getViewCount() == null ? 0 : post.getViewCount()) + pending(post.getId());
    }
}
