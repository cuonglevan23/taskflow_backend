package com.example.taskmanagement_backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisClearConfig {
    private final StringRedisTemplate redisTemplate;

    public RedisClearConfig(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void clearCacheOnStartup() {
        System.out.println("⚠️ Clearing Redis cache on startup...");
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
}
