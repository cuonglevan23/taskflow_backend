package com.example.taskmanagement_backend.dtos.NotificationDto;

import lombok.*;
import java.util.List;

/**
 * DTO to handle notification IDs requests with better error handling
 * Supports both array format and object format for flexibility
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationIdsRequestDto {

    /**
     * Array of notification IDs to process
     * Expected format: [123, 124, 125]
     */
    private List<Long> notificationIds;

    /**
     * Alternative field for object-based requests
     * Expected format: {"ids": [123, 124, 125]}
     */
    private List<Long> ids;

    /**
     * Get the notification IDs from either field
     * Prioritizes notificationIds, falls back to ids
     */
    public List<Long> getEffectiveIds() {
        if (notificationIds != null && !notificationIds.isEmpty()) {
            return notificationIds;
        }
        if (ids != null && !ids.isEmpty()) {
            return ids;
        }
        throw new IllegalArgumentException("Either 'notificationIds' array or 'ids' array must be provided");
    }
}
