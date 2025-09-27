package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.NotificationDto.CreateNotificationRequestDto;
import com.example.taskmanagement_backend.dtos.NotificationDto.NotificationCountDto;
import com.example.taskmanagement_backend.dtos.NotificationDto.NotificationResponseDto;
import com.example.taskmanagement_backend.entities.Notification;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.NotificationType;
import com.example.taskmanagement_backend.repositories.NotificationJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main notification service that orchestrates the entire real-time notification system
 * Handles persistence, Redis caching, WebSocket delivery, and push notifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationJpaRepository notificationRepository;
    private final UserJpaRepository userRepository;
    private final NotificationRedisService redisService;
    private final NotificationWebSocketService webSocketService;
    private final NotificationMapper notificationMapper;

    /**
     * Create and send a new notification
     * This is the main entry point for all notifications in the system
     */
    @Async
    public void createAndSendNotification(CreateNotificationRequestDto request) {
        try {
            // First, create the notification synchronously in a transaction
            NotificationResponseDto notificationDto = createNotificationInTransaction(request);

            if (notificationDto != null) {
                // Then handle async operations (WebSocket, push notifications) outside transaction
                handleAsyncNotificationDelivery(notificationDto, request);
            }
        } catch (Exception e) {
            log.error("Error creating notification for user {}: {}", request.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Create notification in a separate transaction (synchronous)
     */
    @Transactional
    public NotificationResponseDto createNotificationInTransaction(CreateNotificationRequestDto request) {
        try {
            // Fetch user
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));

            // Create notification entity
            Notification notification = Notification.builder()
                    .user(user)
                    .title(request.getTitle())
                    .content(request.getContent())
                    .type(request.getType())
                    .referenceId(request.getReferenceId())
                    .referenceType(request.getReferenceType())
                    .metadata(request.getMetadata())
                    .priority(request.getPriority() != null ? request.getPriority() : request.getType().getPriority())
                    .createdAt(LocalDateTime.now())
                    .expiresAt(request.getExpiresInHours() != null ?
                        LocalDateTime.now().plusHours(request.getExpiresInHours()) : null)
                    .build();

            // Save to database
            notification = notificationRepository.save(notification);

            // Update Redis counter
            redisService.incrementUnreadCount(user.getId());

            // Convert to DTO
            NotificationResponseDto notificationDto = notificationMapper.toResponseDto(notification);
            notificationDto.setSenderName(request.getSenderName());
            notificationDto.setAvatarUrl(request.getAvatarUrl());
            notificationDto.setActionUrl(request.getActionUrl());
            notificationDto.setIsRealTime(true);

            log.info("Created notification {} for user {}", notification.getId(), user.getId());
            return notificationDto;

        } catch (Exception e) {
            log.error("Error creating notification in transaction for user {}: {}", request.getUserId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Handle async notification delivery (WebSocket, push notifications)
     */
    private void handleAsyncNotificationDelivery(NotificationResponseDto notificationDto, CreateNotificationRequestDto request) {
        try {
            Long userId = request.getUserId();

            // Send via WebSocket if user is online
            if (webSocketService.isUserOnline(userId)) {
                webSocketService.sendNotificationToUser(userId, notificationDto);

                // Send updated unread count
                NotificationCountDto unreadCount = getUnreadCount(userId);
                webSocketService.sendUnreadCountUpdate(userId, unreadCount);
            } else {
                // Queue for offline delivery
                log.debug("User {} is offline, notification queued", userId);
            }

            // Send push notification for offline users or high priority notifications
            if (!webSocketService.isUserOnline(userId) || notificationDto.getPriority() > 0) {
                // Create a minimal notification object for push notification
                Notification notification = Notification.builder()
                        .id(notificationDto.getId())
                        .title(notificationDto.getTitle())
                        .content(notificationDto.getContent())
                        .type(notificationDto.getType())
                        .priority(notificationDto.getPriority())
                        .build();

                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    sendPushNotification(user, notification);
                }
            }

            log.info("Delivered notification {} to user {}", notificationDto.getId(), userId);

        } catch (Exception e) {
            log.error("Error delivering notification to user {}: {}", request.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Get paginated notifications for a user
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getNotifications(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Page<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return notifications.map(notificationMapper::toResponseDto);
    }

    /**
     * Get unread notifications for a user
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getUnreadNotifications(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Page<Notification> notifications = notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user, pageable);
        return notifications.map(notificationMapper::toResponseDto);
    }

    /**
     * Get unread notification count with breakdown by type
     */
    @Transactional(readOnly = true)
    public NotificationCountDto getUnreadCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Get total unread count from Redis (faster)
        Long totalUnread = redisService.getUnreadCount(userId);

        // Get breakdown by type from database
        List<Object[]> countByType = notificationRepository.getUnreadCountByType(user);
        Map<NotificationType, Long> typeCountMap = countByType.stream()
                .collect(Collectors.toMap(
                    row -> (NotificationType) row[0],
                    row -> (Long) row[1]
                ));

        return NotificationCountDto.builder()
                .userId(userId)
                .unreadCount(totalUnread)
                .unreadChatMessages(typeCountMap.getOrDefault(NotificationType.CHAT_MESSAGE, 0L))
                .unreadTaskNotifications(getTaskNotificationCount(typeCountMap))
                .unreadSystemNotifications(getSystemNotificationCount(typeCountMap))
                .unreadFriendRequests(typeCountMap.getOrDefault(NotificationType.FRIEND_REQUEST, 0L))
                .unreadMeetingInvitations(typeCountMap.getOrDefault(NotificationType.MEETING_INVITATION, 0L))
                .build();
    }

    /**
     * Mark notifications as read
     */
    @Transactional
    public void markNotificationsAsRead(Long userId, List<Long> notificationIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        int updatedCount = notificationRepository.markAsReadByIds(notificationIds, user, LocalDateTime.now());

        if (updatedCount > 0) {
            // Update Redis counter
            redisService.decrementUnreadCount(userId, updatedCount);

            // Send updated count via WebSocket
            if (webSocketService.isUserOnline(userId)) {
                NotificationCountDto unreadCount = getUnreadCount(userId);
                webSocketService.sendUnreadCountUpdate(userId, unreadCount);
            }

            log.info("Marked {} notifications as read for user {}", updatedCount, userId);
        }
    }

    /**
     * Mark all notifications as read for a user
     */
    @Transactional
    public void markAllNotificationsAsRead(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        int updatedCount = notificationRepository.markAllAsReadByUser(user, LocalDateTime.now());

        if (updatedCount > 0) {
            // Reset Redis counter
            redisService.resetUnreadCount(userId);

            // Send updated count via WebSocket
            if (webSocketService.isUserOnline(userId)) {
                NotificationCountDto unreadCount = NotificationCountDto.builder()
                        .userId(userId)
                        .unreadCount(0L)
                        .unreadChatMessages(0L)
                        .unreadTaskNotifications(0L)
                        .unreadSystemNotifications(0L)
                        .unreadFriendRequests(0L)
                        .unreadMeetingInvitations(0L)
                        .build();
                webSocketService.sendUnreadCountUpdate(userId, unreadCount);
            }

            log.info("Marked all {} notifications as read for user {}", updatedCount, userId);
        }
    }

    /**
     * Sync notifications when user logs in
     * Sends any queued notifications and ensures Redis counter is accurate
     */
    @Transactional
    public void syncNotificationsOnLogin(Long userId) {
        try {
            // Get queued notifications from Redis
            List<Object> queuedNotifications = redisService.getQueuedNotifications(userId);

            if (!queuedNotifications.isEmpty()) {
                // Send queued notifications via WebSocket
                List<NotificationResponseDto> notifications = queuedNotifications.stream()
                        .map(obj -> (NotificationResponseDto) obj)
                        .collect(Collectors.toList());

                webSocketService.sendBatchNotifications(userId, notifications);
                log.info("Sent {} queued notifications to user {} on login", notifications.size(), userId);
            }

            // Ensure Redis counter is accurate
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            Long actualUnreadCount = notificationRepository.countUnreadByUser(user);
            redisService.resetUnreadCount(userId);

            if (actualUnreadCount > 0) {
                for (int i = 0; i < actualUnreadCount; i++) {
                    redisService.incrementUnreadCount(userId);
                }
            }

            // Send current unread count
            NotificationCountDto unreadCount = getUnreadCount(userId);
            webSocketService.sendUnreadCountUpdate(userId, unreadCount);

        } catch (Exception e) {
            log.error("Error syncing notifications for user {} on login: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Send push notification (placeholder for Firebase Cloud Messaging integration)
     */
    @Async
    protected void sendPushNotification(User user, Notification notification) {
        try {
            // TODO: Implement Firebase Cloud Messaging or other push notification service
            log.debug("Would send push notification to user {}: {}", user.getId(), notification.getTitle());

            // You can integrate with:
            // - Firebase Cloud Messaging (FCM)
            // - Apple Push Notification Service (APNs)
            // - Email notifications
            // - SMS notifications

        } catch (Exception e) {
            log.error("Error sending push notification to user {}: {}", user.getId(), e.getMessage());
        }
    }

    // Helper methods
    private Long getTaskNotificationCount(Map<NotificationType, Long> typeCountMap) {
        return typeCountMap.entrySet().stream()
                .filter(entry -> entry.getKey().name().startsWith("TASK_"))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private Long getSystemNotificationCount(Map<NotificationType, Long> typeCountMap) {
        return typeCountMap.entrySet().stream()
                .filter(entry -> entry.getKey().name().startsWith("SYSTEM_"))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    /**
     * 🚨 NEW: Kiểm tra xem user đã có notification của type cụ thể hôm nay chưa
     * Để tránh spam thông báo duplicate
     */
    public boolean hasNotificationToday(Long userId, NotificationType type, java.time.LocalDate date) {
        try {
            java.time.LocalDateTime startOfDay = date.atStartOfDay();
            java.time.LocalDateTime endOfDay = date.atTime(23, 59, 59);

            long count = notificationRepository.countByUserIdAndTypeAndCreatedAtBetween(
                userId, type, startOfDay, endOfDay);

            return count > 0;
        } catch (Exception e) {
            log.error("Error checking notification for user {} type {} date {}: {}",
                userId, type, date, e.getMessage());
            return false; // Nếu có lỗi, cho phép gửi thông báo
        }
    }

    /**
     * ✅ NEW: Get bookmarked notifications for a user
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getBookmarkedNotifications(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Page<Notification> notifications = notificationRepository.findByUserAndIsBookmarkedTrueOrderByBookmarkedAtDesc(user, pageable);
        return notifications.map(notificationMapper::toResponseDto);
    }

    /**
     * ✅ NEW: Get archived notifications for a user
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getArchivedNotifications(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Page<Notification> notifications = notificationRepository.findByUserAndIsArchivedTrueOrderByArchivedAtDesc(user, pageable);
        return notifications.map(notificationMapper::toResponseDto);
    }

    /**
     * ✅ NEW: Get active (non-archived) notifications for inbox view
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getActiveNotifications(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Page<Notification> notifications = notificationRepository.findByUserAndIsArchivedFalseOrderByCreatedAtDesc(user, pageable);
        return notifications.map(notificationMapper::toResponseDto);
    }

    /**
     * ✅ NEW: Toggle bookmark status of a notification
     */
    @Transactional
    public void toggleBookmark(Long userId, Long notificationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        // Verify the notification belongs to the user
        if (!notification.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied: Notification does not belong to user");
        }

        // Toggle bookmark status
        notification.toggleBookmark();
        notificationRepository.save(notification);

        log.info("Toggled bookmark for notification {} by user {}: {}",
                notificationId, userId, notification.getIsBookmarked());
    }

    /**
     * ✅ NEW: Archive notifications by IDs
     */
    @Transactional
    public void archiveNotifications(Long userId, List<Long> notificationIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        LocalDateTime now = LocalDateTime.now();
        int updated = notificationRepository.archiveByIds(notificationIds, user, now);

        log.info("Archived {} notifications for user {}", updated, userId);
    }

    /**
     * ✅ NEW: Unarchive notifications by IDs
     */
    @Transactional
    public void unarchiveNotifications(Long userId, List<Long> notificationIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        int updated = notificationRepository.unarchiveByIds(notificationIds, user);

        log.info("Unarchived {} notifications for user {}", updated, userId);
    }

    /**
     * ✅ NEW: Get enhanced notification count including bookmarks and archives
     */
    @Transactional(readOnly = true)
    public NotificationCountDto getEnhancedUnreadCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Get basic unread count
        NotificationCountDto basicCount = getUnreadCount(userId);

        // Add bookmark and archive counts
        Long bookmarkedCount = notificationRepository.countBookmarkedByUser(user);
        Long archivedCount = notificationRepository.countArchivedByUser(user);

        return NotificationCountDto.builder()
                .userId(userId)
                .unreadCount(basicCount.getUnreadCount())
                .unreadChatMessages(basicCount.getUnreadChatMessages())
                .unreadTaskNotifications(basicCount.getUnreadTaskNotifications())
                .unreadSystemNotifications(basicCount.getUnreadSystemNotifications())
                .unreadFriendRequests(basicCount.getUnreadFriendRequests())
                .unreadMeetingInvitations(basicCount.getUnreadMeetingInvitations())
                .bookmarkedCount(bookmarkedCount)
                .archivedCount(archivedCount)
                .build();
    }

    /**
     * ✅ NEW: Delete single notification permanently
     */
    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        // Verify the notification belongs to the user
        if (!notification.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied: Notification does not belong to user");
        }

        // Delete the notification permanently
        notificationRepository.delete(notification);

        // Update Redis counter if it was unread
        if (!notification.getIsRead()) {
            redisService.decrementUnreadCount(userId, 1);
        }

        log.info("Permanently deleted notification {} for user {}", notificationId, userId);
    }

    /**
     * ✅ NEW: Delete multiple notifications permanently
     */
    @Transactional
    public void deleteNotifications(Long userId, List<Long> notificationIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        int unreadDeletedCount = 0;

        for (Long notificationId : notificationIds) {
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

            // Verify the notification belongs to the user
            if (!notification.getUser().getId().equals(userId)) {
                throw new RuntimeException("Access denied: Notification " + notificationId + " does not belong to user");
            }

            // Count unread notifications for Redis counter update
            if (!notification.getIsRead()) {
                unreadDeletedCount++;
            }

            // Delete the notification permanently
            notificationRepository.delete(notification);
        }

        // Update Redis counter for deleted unread notifications
        if (unreadDeletedCount > 0) {
            redisService.decrementUnreadCount(userId, unreadDeletedCount);
        }

        log.info("Permanently deleted {} notifications for user {} ({} were unread)",
                notificationIds.size(), userId, unreadDeletedCount);
    }
}
