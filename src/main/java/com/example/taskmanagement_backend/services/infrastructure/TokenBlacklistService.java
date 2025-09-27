package com.example.taskmanagement_backend.services.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Professional Token Blacklist Service with Redis
 * 
 * Features:
 * - Blacklist JWT tokens on logout
 * - Automatic TTL based on token expiration
 * - Fast token validation for security
 * - Integration with NextAuth OAuth2 flow
 * 
 * @author Task Management Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    // Redis key prefix for blacklisted tokens
    private static final String BLACKLIST_KEY_PREFIX = "taskmanagement:blacklist:token:";
    
    /**
     * Add token to blacklist with TTL based on token expiration
     * 
     * @param token JWT token to blacklist
     * @param expirationTime Token expiration timestamp
     */
    public void blacklistToken(String token, Instant expirationTime) {
        try {
            String key = BLACKLIST_KEY_PREFIX + hashToken(token);
            
            // Calculate TTL - time remaining until token expires
            long ttlSeconds = Duration.between(Instant.now(), expirationTime).getSeconds();
            
            if (ttlSeconds > 0) {
                // Store token hash in blacklist with TTL
                redisTemplate.opsForValue().set(key, "BLACKLISTED", ttlSeconds, TimeUnit.SECONDS);
                log.info("✅ Token blacklisted successfully with TTL: {} seconds", ttlSeconds);
            } else {
                log.warn("⚠️ Token already expired, not adding to blacklist");
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to blacklist token", e);
            throw new RuntimeException("Failed to blacklist token", e);
        }
    }
    
    /**
     * Add token to blacklist with custom TTL (for cases where expiration is unknown)
     * 
     * @param token JWT token to blacklist
     * @param ttlMinutes TTL in minutes
     */
    public void blacklistToken(String token, long ttlMinutes) {
        try {
            String key = BLACKLIST_KEY_PREFIX + hashToken(token);
            redisTemplate.opsForValue().set(key, "BLACKLISTED", ttlMinutes, TimeUnit.MINUTES);
            log.info("✅ Token blacklisted with custom TTL: {} minutes", ttlMinutes);
        } catch (Exception e) {
            log.error("❌ Failed to blacklist token with custom TTL", e);
            throw new RuntimeException("Failed to blacklist token", e);
        }
    }
    
    /**
     * Check if token is blacklisted
     * 
     * @param token JWT token to check
     * @return true if token is blacklisted, false otherwise
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String key = BLACKLIST_KEY_PREFIX + hashToken(token);
            Boolean exists = redisTemplate.hasKey(key);
            
            if (Boolean.TRUE.equals(exists)) {
                log.debug("🚫 Token is blacklisted");
                return true;
            } else {
                log.debug("✅ Token is not blacklisted");
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to check token blacklist status", e);
            // Fail safe - if Redis is down, don't block valid tokens
            return false;
        }
    }
    
    /**
     * Remove token from blacklist (for testing or manual intervention)
     * 
     * @param token JWT token to remove from blacklist
     */
    public void removeTokenFromBlacklist(String token) {
        try {
            String key = BLACKLIST_KEY_PREFIX + hashToken(token);
            Boolean deleted = redisTemplate.delete(key);
            
            if (Boolean.TRUE.equals(deleted)) {
                log.info("✅ Token removed from blacklist");
            } else {
                log.warn("⚠️ Token was not in blacklist");
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to remove token from blacklist", e);
        }
    }
    
    /**
     * Get blacklist statistics
     * 
     * @return number of blacklisted tokens
     */
    public long getBlacklistSize() {
        try {
            return redisTemplate.keys(BLACKLIST_KEY_PREFIX + "*").size();
        } catch (Exception e) {
            log.error("❌ Failed to get blacklist size", e);
            return -1;
        }
    }
    
    /**
     * Clear all blacklisted tokens (for maintenance)
     */
    public void clearBlacklist() {
        try {
            var keys = redisTemplate.keys(BLACKLIST_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keys);
                log.info("✅ Cleared blacklist: {} tokens removed", deletedCount);
            } else {
                log.info("ℹ️ Blacklist is already empty");
            }
        } catch (Exception e) {
            log.error("❌ Failed to clear blacklist", e);
        }
    }
    
    /**
     * Hash token for storage (for security and key length optimization)
     * 
     * @param token Original JWT token
     * @return Hashed token for Redis key
     */
    private String hashToken(String token) {
        // Use simple hash for now - in production, consider SHA-256
        return String.valueOf(token.hashCode());
    }
    
    /**
     * Check if blacklist service is available
     * 
     * @return true if Redis is available, false otherwise
     */
    public boolean isServiceAvailable() {
        try {
            redisTemplate.opsForValue().get("health-check");
            return true;
        } catch (Exception e) {
            log.error("❌ Token blacklist service unavailable", e);
            return false;
        }
    }
}