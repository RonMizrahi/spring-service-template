package com.example.template.cache;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over Redis value operations. Active only when {@code app.cache.enabled=true}.
 * For method-level caching prefer Spring's {@code @Cacheable}; this service is for explicit
 * get/set access.
 */
@Component
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** Store with a time-to-live. Prefer this over {@link #set(String, Object)} so entries expire. */
    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
