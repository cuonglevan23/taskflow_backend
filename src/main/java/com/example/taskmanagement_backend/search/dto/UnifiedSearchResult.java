package com.example.taskmanagement_backend.search.dto;

import com.example.taskmanagement_backend.search.documents.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Response DTO for unified search results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedSearchResult {

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
     * Search execution time in milliseconds
     */
    private Long searchTime;

    /**
     * Search suggestions
     */
    private List<String> suggestions;

    /**
     * Query that was executed
     */
    private String executedQuery;

    /**
     * Entities that were searched
     */
    private List<String> searchedEntities;
}
