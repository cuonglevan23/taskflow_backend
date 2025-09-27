package com.example.taskmanagement_backend.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Search criteria for project search operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSearchCriteria {

    /**
     * Search term for name/description
     */
    private String searchTerm;

    /**
     * Project statuses to filter by
     */
    private List<String> statuses;

    /**
     * Tags to filter by
     */
    private List<String> tags;

    /**
     * User ID for filtering user-specific projects
     */
    private Long userId;

    /**
     * Owner ID for filtering by project owner
     */
    private Long ownerId;

    /**
     * Team ID for filtering team projects
     */
    private Long teamId;

    /**
     * Created date range start
     */
    private LocalDateTime createdDateStart;

    /**
     * Created date range end
     */
    private LocalDateTime createdDateEnd;

    /**
     * Check if any filters are applied
     */
    public boolean hasFilters() {
        return (statuses != null && !statuses.isEmpty()) ||
               (tags != null && !tags.isEmpty()) ||
               ownerId != null ||
               teamId != null ||
               createdDateStart != null ||
               createdDateEnd != null;
    }
}
