package com.example.taskmanagement_backend.dtos.NotificationDto;

import com.example.taskmanagement_backend.enums.NotificationType;
import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequestDto {

    private Long userId;

    private String title;

    private String content;

    private NotificationType type;

    private Long referenceId;

    private String referenceType;

    private Map<String, String> metadata;

    private Integer priority;

    // Optional: specify expiration time in hours
    private Integer expiresInHours;

    // For system notifications
    private String senderName;

    private String avatarUrl;

    private String actionUrl;
}
