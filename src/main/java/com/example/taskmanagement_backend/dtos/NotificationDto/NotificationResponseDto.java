package com.example.taskmanagement_backend.dtos.NotificationDto;

import com.example.taskmanagement_backend.enums.NotificationType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDto {

    private Long id;

    private Long userId;

    private String title;

    private String content;

    private NotificationType type;

    private Long referenceId;

    private String referenceType;

    private Map<String, String> metadata;

    private Boolean isRead;

    private LocalDateTime readAt;

    // ✅ NEW: Bookmark and Archive fields for inbox functionality
    private Boolean isBookmarked;

    private LocalDateTime bookmarkedAt;

    private Boolean isArchived;

    private LocalDateTime archivedAt;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private Integer priority;

    // For real-time WebSocket notifications
    private String channelId; // WebSocket channel identifier

    private Boolean isRealTime; // Whether this was sent via WebSocket

    // Additional fields for frontend convenience
    private String actionUrl; // URL to navigate when notification is clicked

    private String avatarUrl; // Avatar for the notification sender

    private String senderName; // Name of the person/system that triggered notification
}
