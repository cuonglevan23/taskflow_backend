package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.repositories.NotificationJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled service for notification system maintenance tasks
 * Handles cleanup of expired notifications and other maintenance operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduledService {

    private final NotificationJpaRepository notificationRepository;

    /**
     * Clean up expired notifications
     * Runs every hour to remove notifications that have passed their expiration time
     */
    @Scheduled(fixedRate = 3600000) // 1 hour = 3600000 ms
    @Transactional
    public void cleanupExpiredNotifications() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int deletedCount = notificationRepository.deleteExpiredNotifications(now);

            if (deletedCount > 0) {
                log.info("Cleaned up {} expired notifications", deletedCount);
            }
        } catch (Exception e) {
            log.error("Error during expired notifications cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Log notification system statistics
     * Runs every 30 minutes to provide visibility into system performance
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes = 1800000 ms
    public void logNotificationStats() {
        try {
            long totalNotifications = notificationRepository.count();
            log.info("Notification system stats - Total notifications: {}", totalNotifications);
        } catch (Exception e) {
            log.error("Error getting notification stats: {}", e.getMessage());
        }
    }
}
