package com.example.taskmanagement_backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis service for chat-related caching and real-time status management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserService userService;

    // Redis key patterns
    private static final String USER_ONLINE_KEY = "chat:online:%d";
    private static final String USER_SESSIONS_KEY = "chat:sessions:%d";
    private static final String TYPING_KEY = "chat:typing:%d";
    private static final String UNREAD_COUNT_KEY = "chat:unread:%d:%d";
    private static final String CONVERSATION_MEMBERS_KEY = "chat:members:%d";

    // TTL constants
    private static final Duration ONLINE_STATUS_TTL = Duration.ofMinutes(30);
    private static final Duration TYPING_STATUS_TTL = Duration.ofSeconds(10);
    private static final Duration UNREAD_COUNT_TTL = Duration.ofDays(7);
    private static final Duration MEMBERS_CACHE_TTL = Duration.ofHours(1);

    /**
     * Set user as online with server instance
     */
    public void setUserOnline(Long userId, String serverInstance) {
        try {
            String key = String.format(USER_ONLINE_KEY, userId);
            redisTemplate.opsForValue().set(key, serverInstance, ONLINE_STATUS_TTL);
            log.debug("Set user {} online on server {}", userId, serverInstance);
        } catch (Exception e) {
            log.error("Error setting user {} online: {}", userId, e.getMessage());
        }
    }

    /**
     * Mark user as offline
     */
    public void setUserOffline(Long userId) {
        try {
            String onlineKey = String.format(USER_ONLINE_KEY, userId);
            String sessionsKey = String.format(USER_SESSIONS_KEY, userId);
            
            redisTemplate.delete(onlineKey);
            redisTemplate.delete(sessionsKey);

            log.debug("Set user {} offline", userId);
        } catch (Exception e) {
            log.error("Error setting user {} offline: {}", userId, e.getMessage());
        }
    }

    /**
     * Check if user is online
     */
    public boolean isUserOnline(Long userId) {
        try {
            String key = String.format(USER_ONLINE_KEY, userId);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Error checking if user {} is online: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get server instance where user is online
     */
    public String getUserServerInstance(Long userId) {
        try {
            String key = String.format(USER_ONLINE_KEY, userId);
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.error("Error getting server instance for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Add user session
     */
    public void addUserSession(Long userId, String sessionId) {
        try {
            String key = String.format(USER_SESSIONS_KEY, userId);
            redisTemplate.opsForSet().add(key, sessionId);
            redisTemplate.expire(key, ONLINE_STATUS_TTL);
            log.debug("Added session {} for user {}", sessionId, userId);
        } catch (Exception e) {
            log.error("Error adding session {} for user {}: {}", sessionId, userId, e.getMessage());
        }
    }

    /**
     * Remove user session
     */
    public void removeUserSession(Long userId, String sessionId) {
        try {
            String key = String.format(USER_SESSIONS_KEY, userId);
            redisTemplate.opsForSet().remove(key, sessionId);
            log.debug("Removed session {} for user {}", sessionId, userId);
        } catch (Exception e) {
            log.error("Error removing session {} for user {}: {}", sessionId, userId, e.getMessage());
        }
    }

    /**
     * Check if user has active sessions - THIS IS THE MISSING METHOD
     */
    public boolean hasActiveSessions(Long userId) {
        try {
            String key = String.format(USER_SESSIONS_KEY, userId);
            Long sessionCount = redisTemplate.opsForSet().size(key);
            boolean hasActiveSessions = sessionCount != null && sessionCount > 0;
            log.debug("User {} has {} active sessions", userId, sessionCount);
            return hasActiveSessions;
        } catch (Exception e) {
            log.error("Error checking active sessions for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get all active sessions for user
     */
    public Set<Object> getUserActiveSessions(Long userId) {
        try {
            String key = String.format(USER_SESSIONS_KEY, userId);
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.error("Error getting active sessions for user {}: {}", userId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Set typing status for conversation
     */
    public void setTyping(Long conversationId, Long userId, String userName, boolean isTyping) {
        try {
            String key = String.format(TYPING_KEY, conversationId);

            if (isTyping) {
                redisTemplate.opsForHash().put(key, userId.toString(), userName);
                redisTemplate.expire(key, TYPING_STATUS_TTL);
            } else {
                redisTemplate.opsForHash().delete(key, userId.toString());
            }

            log.debug("Set typing status for user {} in conversation {}: {}", userId, conversationId, isTyping);
        } catch (Exception e) {
            log.error("Error setting typing status for user {} in conversation {}: {}", userId, conversationId, e.getMessage());
        }
    }

    /**
     * Get typing users in conversation
     */
    public Set<Object> getTypingUsers(Long conversationId) {
        try {
            String key = String.format(TYPING_KEY, conversationId);
            return redisTemplate.opsForHash().values(key).stream().collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            log.error("Error getting typing users for conversation {}: {}", conversationId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Increment unread count for user in conversation
     */
    public void incrementUnreadCount(Long userId, Long conversationId) {
        try {
            String key = String.format(UNREAD_COUNT_KEY, userId, conversationId);
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, UNREAD_COUNT_TTL);
            log.debug("Incremented unread count for user {} in conversation {}", userId, conversationId);
        } catch (Exception e) {
            log.error("Error incrementing unread count for user {} in conversation {}: {}", userId, conversationId, e.getMessage());
        }
    }

    /**
     * Reset unread count for user in conversation
     */
    public void resetUnreadCount(Long userId, Long conversationId) {
        try {
            String key = String.format(UNREAD_COUNT_KEY, userId, conversationId);
            redisTemplate.delete(key);
            log.debug("Reset unread count for user {} in conversation {}", userId, conversationId);
        } catch (Exception e) {
            log.error("Error resetting unread count for user {} in conversation {}: {}", userId, conversationId, e.getMessage());
        }
    }

    /**
     * Get unread count for user in conversation
     */
    public Long getUnreadCount(Long userId, Long conversationId) {
        try {
            String key = String.format(UNREAD_COUNT_KEY, userId, conversationId);
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.valueOf(value.toString()) : 0L;
        } catch (Exception e) {
            log.error("Error getting unread count for user {} in conversation {}: {}", userId, conversationId, e.getMessage());
            return 0L;
        }
    }

    /**
     * Cache conversation members
     */
    public void cacheConversationMembers(Long conversationId, Set<Long> memberIds) {
        try {
            String key = String.format(CONVERSATION_MEMBERS_KEY, conversationId);
            redisTemplate.delete(key); // Clear existing

            if (!memberIds.isEmpty()) {
                redisTemplate.opsForSet().add(key, memberIds.toArray());
                redisTemplate.expire(key, MEMBERS_CACHE_TTL);
            }

            log.debug("Cached {} members for conversation {}", memberIds.size(), conversationId);
        } catch (Exception e) {
            log.error("Error caching members for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * Get cached conversation members
     */
    public Set<Object> getCachedConversationMembers(Long conversationId) {
        try {
            String key = String.format(CONVERSATION_MEMBERS_KEY, conversationId);
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.error("Error getting cached members for conversation {}: {}", conversationId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Update unread count for multiple users (batch operation)
     */
    public void updateUnreadCountBatch(Long conversationId, Set<Long> memberIds, Long senderId) {
        try {
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                for (Long memberId : memberIds) {
                    if (!memberId.equals(senderId)) {
                        String key = String.format(UNREAD_COUNT_KEY, memberId, conversationId);
                        connection.incr(key.getBytes());
                        connection.expire(key.getBytes(), UNREAD_COUNT_TTL.getSeconds());
                    }
                }
                return null;
            });

            log.debug("Updated unread counts for {} members in conversation {}", memberIds.size() - 1, conversationId);
        } catch (Exception e) {
            log.error("Error updating unread counts batch for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * Update unread count for multiple users (wrapper method for ChatService)
     */
    public void updateUnreadCount(Long conversationId, List<Long> memberUserIds, Long senderId) {
        try {
            Set<Long> memberIdsSet = new HashSet<>(memberUserIds);
            updateUnreadCountBatch(conversationId, memberIdsSet, senderId);
        } catch (Exception e) {
            log.error("Error updating unread count for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * Get all online users
     */
    public Set<String> getAllOnlineUsers() {
        try {
            Set<String> keys = redisTemplate.keys("chat:online:*");
            return keys != null ? keys.stream()
                    .map(key -> key.substring(key.lastIndexOf(':') + 1))
                    .collect(java.util.stream.Collectors.toSet()) : Set.of();
        } catch (Exception e) {
            log.error("Error getting all online users: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * Cleanup expired data (utility method)
     */
    public void cleanupExpiredData() {
        try {
            // This would typically be called by a scheduled task
            // For now, Redis handles expiration automatically
            log.debug("Cleanup expired data - Redis handles TTL automatically");
        } catch (Exception e) {
            log.error("Error during cleanup: {}", e.getMessage());
        }
    }

    /**
     * Health check for Redis connection
     */
    public boolean isHealthy() {
        try {
            redisTemplate.opsForValue().set("chat:health:check", "ok", Duration.ofSeconds(10));
            String result = (String) redisTemplate.opsForValue().get("chat:health:check");
            redisTemplate.delete("chat:health:check");
            return "ok".equals(result);
        } catch (Exception e) {
            log.error("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ========== EMAIL-BASED WRAPPER METHODS FOR SPRING SECURITY PRINCIPAL ==========

    /**
     * Set user online by email (wrapper for Spring Security Principal)
     */
    public void setUserOnlineByEmail(String userEmail, String serverInstance) {
        try {
            Long userId = userService.getUserIdByEmail(userEmail);
            if (userId != null) {
                setUserOnline(userId, serverInstance);
            } else {
                log.warn("Cannot set user online - userId not found for email: {}", userEmail);
            }
        } catch (Exception e) {
            log.error("Error setting user online by email {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Set user offline by email (wrapper for Spring Security Principal)
     */
    public void setUserOfflineByEmail(String userEmail) {
        try {
            Long userId = userService.getUserIdByEmail(userEmail);
            if (userId != null) {
                setUserOffline(userId);
            } else {
                log.warn("Cannot set user offline - userId not found for email: {}", userEmail);
            }
        } catch (Exception e) {
            log.error("Error setting user offline by email {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Add user session by email (wrapper for Spring Security Principal)
     */
    public void addUserSessionByEmail(String userEmail, String sessionId) {
        try {
            Long userId = userService.getUserIdByEmail(userEmail);
            if (userId != null) {
                addUserSession(userId, sessionId);
            } else {
                log.warn("Cannot add user session - userId not found for email: {}", userEmail);
            }
        } catch (Exception e) {
            log.error("Error adding user session by email {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Remove user session by email (wrapper for Spring Security Principal)
     */
    public void removeUserSessionByEmail(String userEmail, String sessionId) {
        try {
            Long userId = userService.getUserIdByEmail(userEmail);
            if (userId != null) {
                removeUserSession(userId, sessionId);
            } else {
                log.warn("Cannot remove user session - userId not found for email: {}", userEmail);
            }
        } catch (Exception e) {
            log.error("Error removing user session by email {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Check if user has active sessions by email (wrapper for Spring Security Principal)
     */
    public boolean hasActiveSessionsByEmail(String userEmail) {
        try {
            Long userId = userService.getUserIdByEmail(userEmail);
            if (userId != null) {
                return hasActiveSessions(userId);
            } else {
                log.warn("Cannot check active sessions - userId not found for email: {}", userEmail);
                return false;
            }
        } catch (Exception e) {
            log.error("Error checking active sessions by email {}: {}", userEmail, e.getMessage());
            return false;
        }
    }

    /**
     * Extend user session TTL by email (wrapper for Spring Security Principal)
     */
    public void extendUserSessionByEmail(String userEmail) {
        try {
            Long userId = userService.getUserIdByEmail(userEmail);
            if (userId != null) {
                String key = String.format(USER_SESSIONS_KEY, userId);
                redisTemplate.expire(key, ONLINE_STATUS_TTL);
                log.debug("Extended session TTL for user {}", userEmail);
            } else {
                log.warn("Cannot extend user session - userId not found for email: {}", userEmail);
            }
        } catch (Exception e) {
            log.error("Error extending user session by email {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Set user typing status
     */
    public void setUserTyping(Long conversationId, Long userId, String userName) {
        setTyping(conversationId, userId, userName, true);
    }

    /**
     * Remove user typing status
     */
    public void removeUserTyping(Long conversationId, Long userId) {
        setTyping(conversationId, userId, null, false);
    }
}
