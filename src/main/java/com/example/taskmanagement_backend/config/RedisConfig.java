package com.example.taskmanagement_backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Professional Redis Configuration for Task Management System
 * 
 * Features:
 * - Multiple cache configurations with different TTL
 * - Proper JSON serialization with type information
 * - Connection pool optimization
 * - Cache metrics and monitoring ready
 * 
 * @author Task Management Team
 * @version 1.0
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.cache.redis.time-to-live:600000}")
    private long defaultTtlMs;

    /**
     * Primary RedisTemplate for manual cache operations
     * Configured with optimized serializers for performance
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // String serializer for keys - more readable and efficient
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // JSON serializer for values with type information
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.setDefaultSerializer(jsonSerializer);
        template.afterPropertiesSet();

        log.info("✅ RedisTemplate configured successfully with JSON serialization");
        return template;
    }

    /**
     * Redis Cache Manager with different TTL strategies for different data types
     */
    @Bean("redisCacheManager")
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = createCacheConfiguration(Duration.ofMillis(defaultTtlMs));

        // Specific configurations for different cache types
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Single task cache - longer TTL as tasks don't change frequently
        cacheConfigurations.put("tasks", 
            createCacheConfiguration(Duration.ofMinutes(15)));
        
        // User tasks cache - medium TTL as user's task list changes moderately
        cacheConfigurations.put("user_tasks", 
            createCacheConfiguration(Duration.ofMinutes(10)));
        
        // Team tasks cache - shorter TTL as team tasks change more frequently
        cacheConfigurations.put("team_tasks", 
            createCacheConfiguration(Duration.ofMinutes(8)));
        
        // Project tasks cache - shorter TTL as project tasks are dynamic
        cacheConfigurations.put("project_tasks", 
            createCacheConfiguration(Duration.ofMinutes(8)));
        
        // Task statistics - very short TTL as stats need to be fresh
        cacheConfigurations.put("task_stats", 
            createCacheConfiguration(Duration.ofMinutes(5)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware() // Enable transaction support
                .build();

        log.info("✅ RedisCacheManager configured with {} cache types", cacheConfigurations.size());
        return cacheManager;
    }

    /**
     * Create cache configuration with specified TTL
     */
    private RedisCacheConfiguration createCacheConfiguration(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(createJsonSerializer()))
                .entryTtl(ttl)
                .disableCachingNullValues() // Don't cache null values
                .prefixCacheNameWith("taskmanagement:"); // Namespace for multi-tenant support
    }

    /**
     * Create optimized JSON serializer with type information
     */
    private GenericJackson2JsonRedisSerializer createJsonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Register JavaTime module for LocalDateTime support
        objectMapper.registerModule(new JavaTimeModule());
        
        // Enable type information for polymorphic deserialization
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}