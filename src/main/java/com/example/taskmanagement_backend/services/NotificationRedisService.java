package com.example.taskmanagement_backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based service for managing notification counters and user sessions
 * Handles unread counts, user presence, and session tracking for real-time notifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis key patterns
    private static final String UNREAD_COUNT_KEY = "user:%d:unread_count";
    private static final String USER_SESSIONS_KEY = "user:%d:sessions";
    private static final String SESSION_USER_KEY = "session:%s:user";
    private static final String NOTIFICATION_QUEUE_KEY = "user:%d:notification_queue";

    /**
     * Get unread notification count for a user
     */
    public Long getUnreadCount(Long userId) {
        try {
            String key = String.format(UNREAD_COUNT_KEY, userId);
            Object count = redisTemplate.opsForValue().get(key);
            return count != null ? Long.valueOf(count.toString()) : 0L;
        } catch (Exception e) {
            log.error("Error getting unread count for user {}: {}", userId, e.getMessage());
            return 0L;
        }
    }

    /**
     * Increment unread notification count for a user
     */
    public Long incrementUnreadCount(Long userId) {
        try {
            String key = String.format(UNREAD_COUNT_KEY, userId);
            return redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.error("Error incrementing unread count for user {}: {}", userId, e.getMessage());
            return 0L;
        }
    }

    /**
     * Decrement unread notification count for a user
     */
    public Long decrementUnreadCount(Long userId, long count) {
        try {
            String key = String.format(UNREAD_COUNT_KEY, userId);
            Long currentCount = getUnreadCount(userId);
            long newCount = Math.max(0, currentCount - count);
            redisTemplate.opsForValue().set(key, newCount);
            return newCount;
        } catch (Exception e) {
            log.error("Error decrementing unread count for user {}: {}", userId, e.getMessage());
            return 0L;
        }
    }

    /**
     * Reset unread notification count for a user
     */
    public void resetUnreadCount(Long userId) {
        try {
            String key = String.format(UNREAD_COUNT_KEY, userId);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Error resetting unread count for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Add a session for a user (when they connect via WebSocket)
     */
    public void addUserSession(Long userId, String sessionId) {
        try {
            String userSessionsKey = String.format(USER_SESSIONS_KEY, userId);
            String sessionUserKey = String.format(SESSION_USER_KEY, sessionId);

            // Add session to user's session set
            redisTemplate.opsForSet().add(userSessionsKey, sessionId);
            redisTemplate.expire(userSessionsKey, 24, TimeUnit.HOURS);

            // Map session to user
            redisTemplate.opsForValue().set(sessionUserKey, userId, 24, TimeUnit.HOURS);

            log.debug("Added session {} for user {}", sessionId, userId);
        } catch (Exception e) {
            log.error("Error adding session for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Remove a session for a user (when they disconnect)
     */
    public void removeUserSession(Long userId, String sessionId) {
        try {
            String userSessionsKey = String.format(USER_SESSIONS_KEY, userId);
            String sessionUserKey = String.format(SESSION_USER_KEY, sessionId);

            redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
            redisTemplate.delete(sessionUserKey);

            log.debug("Removed session {} for user {}", sessionId, userId);
        } catch (Exception e) {
            log.error("Error removing session for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get all active sessions for a user
     */
    public Set<Object> getUserSessions(Long userId) {
        try {
            String key = String.format(USER_SESSIONS_KEY, userId);
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.error("Error getting sessions for user {}: {}", userId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Check if a user is online (has active sessions)
     */
    public boolean isUserOnline(Long userId) {
        try {
            String key = String.format(USER_SESSIONS_KEY, userId);
            Long sessionCount = redisTemplate.opsForSet().size(key);
            return sessionCount != null && sessionCount > 0;
        } catch (Exception e) {
            log.error("Error checking online status for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get user ID from session ID
     */
    public Long getUserFromSession(String sessionId) {
        try {
            String key = String.format(SESSION_USER_KEY, sessionId);
            Object userId = redisTemplate.opsForValue().get(key);
            return userId != null ? Long.valueOf(userId.toString()) : null;
        } catch (Exception e) {
            log.error("Error getting user from session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * Queue notification for offline user (for later push notification)
     */
    public void queueNotificationForOfflineUser(Long userId, Object notification) {
        try {
            String key = String.format(NOTIFICATION_QUEUE_KEY, userId);
            redisTemplate.opsForList().leftPush(key, notification);
            redisTemplate.expire(key, 7, TimeUnit.DAYS); // Keep for 7 days
        } catch (Exception e) {
            log.error("Error queuing notification for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get queued notifications for a user (when they come online)
     */
    public java.util.List<Object> getQueuedNotifications(Long userId) {
        try {
            String key = String.format(NOTIFICATION_QUEUE_KEY, userId);
            java.util.List<Object> notifications = redisTemplate.opsForList().range(key, 0, -1);
            redisTemplate.delete(key); // Clear queue after retrieving
            return notifications != null ? notifications : java.util.List.of();
        } catch (Exception e) {
            log.error("Error getting queued notifications for user {}: {}", userId, e.getMessage());
            return java.util.List.of();
        }
    }
}
