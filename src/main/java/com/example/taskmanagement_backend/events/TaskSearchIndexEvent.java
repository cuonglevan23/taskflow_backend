package com.example.taskmanagement_backend.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Task-specific search index event
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TaskSearchIndexEvent extends SearchIndexEvent {
    private Long taskId;
    private String title;
    private String description;
    private String status;
    private String priority;
    private Long assigneeId;
    private Long projectId;

    public TaskSearchIndexEvent(String eventType, Long taskId, Long userId) {
        super(eventType, "TASK", taskId.toString(), userId);
        this.taskId = taskId;
    }
}
