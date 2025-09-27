package com.example.taskmanagement_backend.dtos.NotificationDto;

import lombok.*;

/**
 * WebSocket event DTO for real-time notifications
 * Used to send notifications through WebSocket channels
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketNotificationEventDto {

    private String eventType; // "NOTIFICATION", "UNREAD_COUNT_UPDATE", "PRESENCE_UPDATE"

    private NotificationResponseDto notification;

    private NotificationCountDto unreadCount;

    private Long userId;

    private String sessionId;

    private Long timestamp;

    // For presence updates
    private Boolean isOnline;

    // For batch notifications
    private java.util.List<NotificationResponseDto> notifications;
}
