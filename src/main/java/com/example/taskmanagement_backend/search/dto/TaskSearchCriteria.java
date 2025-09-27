package com.example.taskmanagement_backend.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Search criteria for task search operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSearchCriteria {

    /**
     * Search term for title/description
     */
    private String searchTerm;

    /**
     * Task statuses to filter by
     */
    private List<String> statuses;

    /**
     * Task priority to filter by
     */
    private String priority;

    /**
     * Tags to filter by
     */
    private List<String> tags;

    /**
     * Completion status filter
     */
    private Boolean isCompleted;

    /**
     * Due date range start
     */
    private LocalDateTime dueDateStart;

    /**
     * Due date range end
     */
    private LocalDateTime dueDateEnd;

    /**
     * User ID for filtering user-specific tasks
     */
    private Long userId;

    /**
     * Project ID for filtering project-specific tasks
     */
    private Long projectId;

    /**
     * Assignee ID for filtering by assignee
     */
    private Long assigneeId;

    /**
     * Check if any filters are applied
     */
    public boolean hasFilters() {
        return (statuses != null && !statuses.isEmpty()) ||
               priority != null ||
               (tags != null && !tags.isEmpty()) ||
               isCompleted != null ||
               dueDateStart != null ||
               dueDateEnd != null ||
               projectId != null ||
               assigneeId != null;
    }
}
