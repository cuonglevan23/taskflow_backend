package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.NotificationDto.CreateNotificationRequestDto;
import com.example.taskmanagement_backend.dtos.NotificationDto.NotificationCountDto;
import com.example.taskmanagement_backend.dtos.NotificationDto.NotificationResponseDto;
import com.example.taskmanagement_backend.dtos.NotificationDto.NotificationIdsRequestDto;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.services.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for the comprehensive real-time notification system
 * Provides endpoints for notification management, unread counts, and sync functionality
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Real-time notification management endpoints")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserJpaRepository userRepository;

    /**
     * 🔥 MAIN SYNC ENDPOINT: Get all unread notifications for sync when user logs in
     */
    @GetMapping("/unread")
    @Operation(summary = "Get unread notifications", description = "Fetch all unread notifications for the current user - used for sync on login")
    public ResponseEntity<Page<NotificationResponseDto>> getUnreadNotifications(
            Authentication authentication,
            Pageable pageable) {

        Long userId = getUserIdFromAuth(authentication);
        Page<NotificationResponseDto> unreadNotifications = notificationService.getUnreadNotifications(userId, pageable);

        log.info("Retrieved {} unread notifications for user {}", unreadNotifications.getTotalElements(), userId);
        return ResponseEntity.ok(unreadNotifications);
    }

    /**
     * Get all notifications for a user (read and unread)
     */
    @GetMapping
    @Operation(summary = "Get all notifications", description = "Fetch all notifications for the current user with pagination")
    public ResponseEntity<Page<NotificationResponseDto>> getAllNotifications(
            Authentication authentication,
            Pageable pageable) {

        Long userId = getUserIdFromAuth(authentication);
        Page<NotificationResponseDto> notifications = notificationService.getNotifications(userId, pageable);

        return ResponseEntity.ok(notifications);
    }

    /**
     * Get unread notification count with breakdown by type
     */
    @GetMapping("/count")
    @Operation(summary = "Get unread notification count", description = "Get unread notification count with breakdown by type")
    public ResponseEntity<NotificationCountDto> getUnreadCount(Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        NotificationCountDto unreadCount = notificationService.getUnreadCount(userId);

        return ResponseEntity.ok(unreadCount);
    }

    /**
     * Get unread notification count - alias for /count endpoint
     * This maintains backward compatibility with frontend code using /unread-count
     */
    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count (alias)", description = "Alias for /count endpoint - gets unread notification count with breakdown by type")
    public ResponseEntity<NotificationCountDto> getUnreadCountAlias(Authentication authentication) {
        return getUnreadCount(authentication);
    }

    /**
     * Mark specific notifications as read - Support both POST and PUT methods
     * ✅ UPDATED: Better error handling for JSON format issues
     */
    @RequestMapping(value = "/mark-read", method = {RequestMethod.POST, RequestMethod.PUT})
    @Operation(summary = "Mark notifications as read", description = "Mark specific notifications as read by their IDs (supports both POST and PUT). Accepts array format [1,2,3] or object format {\"ids\":[1,2,3]}")
    public ResponseEntity<Map<String, Object>> markNotificationsAsRead(
            Authentication authentication,
            @RequestBody(required = false) Object requestBody) {

        try {
            Long userId = getUserIdFromAuth(authentication);
            List<Long> notificationIds;

            // Handle different JSON formats gracefully
            if (requestBody == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // Try to parse as direct array first [1,2,3]
            if (requestBody instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> rawList = (List<Object>) requestBody;
                notificationIds = rawList.stream()
                    .map(obj -> {
                        if (obj instanceof Number) {
                            return ((Number) obj).longValue();
                        } else if (obj instanceof String) {
                            return Long.parseLong((String) obj);
                        }
                        throw new IllegalArgumentException("Invalid notification ID: " + obj);
                    })
                    .toList();
            } else {
                // Try to parse as object {"ids": [1,2,3]} or {"notificationIds": [1,2,3]}
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    NotificationIdsRequestDto dto = mapper.convertValue(requestBody, NotificationIdsRequestDto.class);
                    notificationIds = dto.getEffectiveIds();
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid request format. Expected array [1,2,3] or object {\"ids\":[1,2,3]}. Error: " + e.getMessage());
                }
            }

            if (notificationIds.isEmpty()) {
                throw new IllegalArgumentException("At least one notification ID must be provided");
            }

            notificationService.markNotificationsAsRead(userId, notificationIds);

            log.info("Marked {} notifications as read for user {}", notificationIds.size(), userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully marked " + notificationIds.size() + " notifications as read");
            response.put("processedIds", notificationIds);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for mark notifications as read: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("expectedFormats", List.of(
                "Array format: [123, 124, 125]",
                "Object format: {\"ids\": [123, 124, 125]}",
                "Object format: {\"notificationIds\": [123, 124, 125]}"
            ));

            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error marking notifications as read: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Mark all notifications as read - Support both POST and PUT methods
     */
    @RequestMapping(value = "/mark-all-read", method = {RequestMethod.POST, RequestMethod.PUT})
    @Operation(summary = "Mark all notifications as read", description = "Mark all notifications as read for the current user (supports both POST and PUT)")
    public ResponseEntity<Void> markAllNotificationsAsRead(Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        notificationService.markAllNotificationsAsRead(userId);

        log.info("Marked all notifications as read for user {}", userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Create a new notification (admin/system use)
     */
    @PostMapping
    @Operation(summary = "Create notification", description = "Create a new notification (admin/system use)")
    public ResponseEntity<Void> createNotification(@RequestBody CreateNotificationRequestDto request) {

        notificationService.createAndSendNotification(request);

        log.info("Created notification for user {}: {}", request.getUserId(), request.getTitle());
        return ResponseEntity.ok().build();
    }

    /**
     * Force sync notifications (when user reconnects)
     */
    @PostMapping("/sync")
    @Operation(summary = "Sync notifications", description = "Force sync notifications for the current user")
    public ResponseEntity<Void> syncNotifications(Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        notificationService.syncNotificationsOnLogin(userId);

        log.info("Synced notifications for user {}", userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Health check endpoint for notification system
     */
    @GetMapping("/health")
    @Operation(summary = "Notification system health check", description = "Check if the notification system is working properly")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Notification system is healthy");
    }

    /**
     * ✅ NEW: Get bookmarked notifications (Starred/Saved notifications)
     */
    @GetMapping("/bookmarked")
    @Operation(summary = "Get bookmarked notifications", description = "Get all bookmarked/starred notifications for the current user")
    public ResponseEntity<Page<NotificationResponseDto>> getBookmarkedNotifications(
            Authentication authentication,
            Pageable pageable) {

        Long userId = getUserIdFromAuth(authentication);
        Page<NotificationResponseDto> bookmarkedNotifications = notificationService.getBookmarkedNotifications(userId, pageable);

        log.info("Retrieved {} bookmarked notifications for user {}", bookmarkedNotifications.getTotalElements(), userId);
        return ResponseEntity.ok(bookmarkedNotifications);
    }

    /**
     * ✅ NEW: Get archived notifications
     */
    @GetMapping("/archived")
    @Operation(summary = "Get archived notifications", description = "Get all archived notifications for the current user")
    public ResponseEntity<Page<NotificationResponseDto>> getArchivedNotifications(
            Authentication authentication,
            Pageable pageable) {

        Long userId = getUserIdFromAuth(authentication);
        Page<NotificationResponseDto> archivedNotifications = notificationService.getArchivedNotifications(userId, pageable);

        log.info("Retrieved {} archived notifications for user {}", archivedNotifications.getTotalElements(), userId);
        return ResponseEntity.ok(archivedNotifications);
    }

    /**
     * ✅ NEW: Get active (non-archived) notifications for main inbox view
     */
    @GetMapping("/inbox")
    @Operation(summary = "Get inbox notifications", description = "Get active (non-archived) notifications for main inbox view")
    public ResponseEntity<Page<NotificationResponseDto>> getInboxNotifications(
            Authentication authentication,
            Pageable pageable) {

        Long userId = getUserIdFromAuth(authentication);
        Page<NotificationResponseDto> inboxNotifications = notificationService.getActiveNotifications(userId, pageable);

        log.info("Retrieved {} inbox notifications for user {}", inboxNotifications.getTotalElements(), userId);
        return ResponseEntity.ok(inboxNotifications);
    }

    /**
     * ✅ NEW: Toggle bookmark status of a notification
     */
    @PostMapping("/{notificationId}/bookmark")
    @Operation(summary = "Toggle bookmark", description = "Toggle bookmark status of a notification (star/unstar)")
    public ResponseEntity<Void> toggleBookmark(
            Authentication authentication,
            @PathVariable Long notificationId) {

        Long userId = getUserIdFromAuth(authentication);
        notificationService.toggleBookmark(userId, notificationId);

        log.info("Toggled bookmark for notification {} by user {}", notificationId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * ✅ NEW: Archive single notification by ID
     */
    @PostMapping("/{notificationId}/archive")
    @Operation(summary = "Archive single notification", description = "Archive a single notification by its ID")
    public ResponseEntity<Void> archiveNotification(
            Authentication authentication,
            @PathVariable Long notificationId) {

        Long userId = getUserIdFromAuth(authentication);
        notificationService.archiveNotifications(userId, List.of(notificationId));

        log.info("Archived notification {} for user {}", notificationId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * ✅ NEW: Unarchive single notification by ID
     */
    @PostMapping("/{notificationId}/unarchive")
    @Operation(summary = "Unarchive single notification", description = "Unarchive a single notification by its ID")
    public ResponseEntity<Void> unarchiveNotification(
            Authentication authentication,
            @PathVariable Long notificationId) {

        Long userId = getUserIdFromAuth(authentication);
        notificationService.unarchiveNotifications(userId, List.of(notificationId));

        log.info("Unarchived notification {} for user {}", notificationId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * ✅ NEW: Get enhanced notification count (includes bookmarks and archives)
     */
    @GetMapping("/count/enhanced")
    @Operation(summary = "Get enhanced notification count", description = "Get unread count with bookmark and archive counts")
    public ResponseEntity<NotificationCountDto> getEnhancedCount(Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        NotificationCountDto enhancedCount = notificationService.getEnhancedUnreadCount(userId);

        return ResponseEntity.ok(enhancedCount);
    }

    /**
     * ✅ NEW: Update notification status (PUT endpoint to fix the method not supported error)
     */
    @PutMapping("/{notificationId}")
    @Operation(summary = "Update notification status", description = "Update notification read/bookmark/archive status")
    public ResponseEntity<Void> updateNotificationStatus(
            Authentication authentication,
            @PathVariable Long notificationId,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) Boolean isBookmarked,
            @RequestParam(required = false) Boolean isArchived) {

        Long userId = getUserIdFromAuth(authentication);

        // Update read status if provided
        if (isRead != null && isRead) {
            notificationService.markNotificationsAsRead(userId, List.of(notificationId));
        }

        // Update bookmark status if provided
        if (isBookmarked != null) {
            notificationService.toggleBookmark(userId, notificationId);
        }

        // Update archive status if provided
        if (isArchived != null) {
            if (isArchived) {
                notificationService.archiveNotifications(userId, List.of(notificationId));
            } else {
                notificationService.unarchiveNotifications(userId, List.of(notificationId));
            }
        }

        log.info("Updated notification {} status for user {} - read: {}, bookmark: {}, archive: {}",
                notificationId, userId, isRead, isBookmarked, isArchived);
        return ResponseEntity.ok().build();
    }

    /**
     * ✅ NEW: Delete single notification permanently
     */
    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete notification", description = "Permanently delete a single notification by its ID")
    public ResponseEntity<Void> deleteNotification(
            Authentication authentication,
            @PathVariable Long notificationId) {

        Long userId = getUserIdFromAuth(authentication);
        notificationService.deleteNotification(userId, notificationId);

        log.info("Deleted notification {} for user {}", notificationId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * ✅ NEW: Delete multiple notifications permanently
     */
    @DeleteMapping
    @Operation(summary = "Delete multiple notifications", description = "Permanently delete multiple notifications by their IDs")
    public ResponseEntity<Void> deleteNotifications(
            Authentication authentication,
            @RequestBody List<Long> notificationIds) {

        Long userId = getUserIdFromAuth(authentication);
        notificationService.deleteNotifications(userId, notificationIds);

        log.info("Deleted {} notifications for user {}", notificationIds.size(), userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Extract user ID from authentication context
     */
    private Long getUserIdFromAuth(Authentication authentication) {
        try {
            // Proper authentication handling - extract user from JWT token
            if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                // Get email from authentication (stored as username in UserDetails)
                String email = userDetails.getUsername();

                // Find user by email and return the user ID
                return userRepository.findByEmail(email)
                        .map(user -> user.getId())
                        .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
            }
            throw new RuntimeException("Invalid authentication object");
        } catch (Exception e) {
            log.error("Error extracting user ID from authentication: {}", e.getMessage());
            throw new RuntimeException("Could not extract user ID from authentication", e);
        }
    }
}
