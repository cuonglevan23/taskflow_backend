package com.example.taskmanagement_backend.search.dto;

import com.example.taskmanagement_backend.search.documents.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

/**
 * Response DTO for global search results across all entities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResult {

    /**
     * Task search results
     */
    private Page<TaskSearchDocument> tasks;

    /**
     * Project search results
     */
    private Page<ProjectSearchDocument> projects;

    /**
     * User search results
     */
    private Page<UserSearchDocument> users;

    /**
     * Team search results
     */
    private Page<TeamSearchDocument> teams;

    /**
     * Total number of results across all entities
     */
    private Long totalResults;

    /**
     * Search term that was used
     */
    private String searchTerm;

    /**
     * Search execution time in milliseconds
     */
    private Long searchTime;
}
