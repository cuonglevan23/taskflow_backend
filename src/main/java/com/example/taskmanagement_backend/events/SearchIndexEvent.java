package com.example.taskmanagement_backend.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Base class for all search indexing events
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchIndexEvent {
    private String eventId;
    private String eventType; // CREATE, UPDATE, DELETE
    private String entityType; // TASK, PROJECT, USER, TEAM
    private String entityId;
    private LocalDateTime timestamp;
    private Long userId; // User who triggered the event

    public SearchIndexEvent(String eventType, String entityType, String entityId, Long userId) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.userId = userId;
        this.timestamp = LocalDateTime.now();
    }
}
