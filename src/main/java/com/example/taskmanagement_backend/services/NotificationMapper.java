package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.NotificationDto.NotificationResponseDto;
import com.example.taskmanagement_backend.entities.Notification;
import org.springframework.stereotype.Service;

/**
 * Mapper service for converting Notification entities to DTOs
 */
@Service
public class NotificationMapper {

    public NotificationResponseDto toResponseDto(Notification notification) {
        if (notification == null) {
            return null;
        }

        return NotificationResponseDto.builder()
                .id(notification.getId())
                .userId(notification.getUser().getId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .type(notification.getType())
                .referenceId(notification.getReferenceId())
                .referenceType(notification.getReferenceType())
                .metadata(notification.getMetadata())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .isBookmarked(notification.getIsBookmarked())
                .bookmarkedAt(notification.getBookmarkedAt())
                .isArchived(notification.getIsArchived())
                .archivedAt(notification.getArchivedAt())
                .createdAt(notification.getCreatedAt())
                .expiresAt(notification.getExpiresAt())
                .priority(notification.getPriority())
                .channelId("/queue/notifications-" + notification.getUser().getId())
                .isRealTime(false) // Will be set by the service layer
                .build();
    }
}
