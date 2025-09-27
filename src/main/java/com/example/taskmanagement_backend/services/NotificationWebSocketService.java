package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.NotificationDto.NotificationCountDto;
import com.example.taskmanagement_backend.dtos.NotificationDto.NotificationResponseDto;
import com.example.taskmanagement_backend.dtos.NotificationDto.WebSocketNotificationEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * WebSocket service for real-time notification delivery
 * Handles sending notifications through WebSocket channels to online users
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRedisService redisService;
    private final ObjectMapper objectMapper;

    /**
     * Send real-time notification to a specific user
     */
    public void sendNotificationToUser(Long userId, NotificationResponseDto notification) {
        try {
            // Check if user is online
            if (!redisService.isUserOnline(userId)) {
                log.debug("User {} is offline, queuing notification for later delivery", userId);
                redisService.queueNotificationForOfflineUser(userId, notification);
                return;
            }

            // Create WebSocket event
            WebSocketNotificationEventDto event = WebSocketNotificationEventDto.builder()
                    .eventType("NOTIFICATION")
                    .notification(notification)
                    .userId(userId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Send to user's notification channel
            String destination = "/queue/notifications-" + userId;
            messagingTemplate.convertAndSend(destination, event);

            log.debug("Sent real-time notification to user {} on channel {}", userId, destination);

        } catch (Exception e) {
            log.error("Error sending notification to user {}: {}", userId, e.getMessage());
            // Fallback: queue for offline delivery
            redisService.queueNotificationForOfflineUser(userId, notification);
        }
    }

    /**
     * Send unread count update to a specific user
     */
    public void sendUnreadCountUpdate(Long userId, NotificationCountDto unreadCount) {
        try {
            if (!redisService.isUserOnline(userId)) {
                log.debug("User {} is offline, skipping unread count update", userId);
                return;
            }

            WebSocketNotificationEventDto event = WebSocketNotificationEventDto.builder()
                    .eventType("UNREAD_COUNT_UPDATE")
                    .unreadCount(unreadCount)
                    .userId(userId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            String destination = "/queue/notifications-" + userId;
            messagingTemplate.convertAndSend(destination, event);

            log.debug("Sent unread count update to user {}: {} unread", userId, unreadCount.getUnreadCount());

        } catch (Exception e) {
            log.error("Error sending unread count update to user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Broadcast presence update to all online users
     */
    public void broadcastPresenceUpdate(Long userId, boolean isOnline) {
        try {
            WebSocketNotificationEventDto event = WebSocketNotificationEventDto.builder()
                    .eventType("PRESENCE_UPDATE")
                    .userId(userId)
                    .isOnline(isOnline)
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Broadcast to presence topic
            messagingTemplate.convertAndSend("/topic/presence", event);

            log.debug("Broadcasted presence update: user {} is {}", userId, isOnline ? "online" : "offline");

        } catch (Exception e) {
            log.error("Error broadcasting presence update for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Send batch notifications to a user (for sync when they come online)
     */
    public void sendBatchNotifications(Long userId, java.util.List<NotificationResponseDto> notifications) {
        try {
            if (!redisService.isUserOnline(userId) || notifications.isEmpty()) {
                return;
            }

            WebSocketNotificationEventDto event = WebSocketNotificationEventDto.builder()
                    .eventType("BATCH_NOTIFICATIONS")
                    .notifications(notifications)
                    .userId(userId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            String destination = "/queue/notifications-" + userId;
            messagingTemplate.convertAndSend(destination, event);

            log.debug("Sent {} batch notifications to user {}", notifications.size(), userId);

        } catch (Exception e) {
            log.error("Error sending batch notifications to user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Send notification to multiple users (for group notifications)
     */
    public void sendNotificationToUsers(java.util.List<Long> userIds, NotificationResponseDto notification) {
        userIds.forEach(userId -> sendNotificationToUser(userId, notification));
    }

    /**
     * Get online users count
     */
    public long getOnlineUsersCount() {
        // This is a simplified implementation
        // In a production system, you might want to track this more efficiently
        return 0; // Placeholder - implement based on your needs
    }

    /**
     * Check if specific user is online
     */
    public boolean isUserOnline(Long userId) {
        return redisService.isUserOnline(userId);
    }

    /**
     * Get all active sessions for a user
     */
    public Set<Object> getUserSessions(Long userId) {
        return redisService.getUserSessions(userId);
    }
}
