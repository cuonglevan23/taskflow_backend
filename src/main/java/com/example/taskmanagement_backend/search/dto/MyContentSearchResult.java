package com.example.taskmanagement_backend.search.dto;

import com.example.taskmanagement_backend.search.documents.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

/**
 * Response DTO for my content search results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyContentSearchResult {

    /**
     * Task search results
     */
    private Page<TaskSearchDocument> tasks;

    /**
     * Project search results
     */
    private Page<ProjectSearchDocument> projects;

    /**
     * Total number of results across all entities
     */
    private Long totalResults;

    /**
     * Query that was executed
     */
    private String searchTerm;
}
