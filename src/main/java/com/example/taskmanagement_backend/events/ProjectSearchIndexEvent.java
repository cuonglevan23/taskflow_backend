package com.example.taskmanagement_backend.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Project-specific search index event
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ProjectSearchIndexEvent extends SearchIndexEvent {
    private Long projectId;
    private String name;
    private String description;
    private String status;
    private Long ownerId;

    public ProjectSearchIndexEvent(String eventType, Long projectId, Long userId) {
        super(eventType, "PROJECT", projectId.toString(), userId);
        this.projectId = projectId;
    }
}
