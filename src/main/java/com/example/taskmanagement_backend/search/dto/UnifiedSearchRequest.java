package com.example.taskmanagement_backend.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for unified search across all entities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class       UnifiedSearchRequest {

    /**
     * Search query string
     */
    private String query;

    /**
     * List of entities to search in (tasks, projects, users, teams)
     * If null or empty, search in all entities
     */
    private List<String> entities;

    /**
     * Filters for each entity type
     * Example: {"tasks": {"status": ["OPEN", "IN_PROGRESS"]}, "projects": {"status": ["ACTIVE"]}}
     */
    private Map<String, Object> filters;

    /**
     * Pagination settings
     */
    private PaginationRequest pagination;

    /**
     * Sort settings
     */
    private SortRequest sort;

    /**
     * Search scope (my, team, all)
     */
    private String scope;

    /**
     * Include suggestions in response
     */
    private Boolean includeSuggestions;

    /**
     * Search context for AI suggestions - Can be Object or String
     * Changed from String to Object to handle complex context data
     */
    private Object context;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaginationRequest {
        @Builder.Default
        private Integer page = 0;
        @Builder.Default
        private Integer size = 20;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SortRequest {
        private String field;
        @Builder.Default
        private String direction = "desc"; // asc or desc
    }
}
