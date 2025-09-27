package com.example.taskmanagement_backend.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Collection of Search Response DTOs
 */
public class SearchResponseDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResponse<T> {
        private List<T> results;
        private Long totalResults;
        private Integer currentPage;
        private Integer totalPages;
        private String query;
        private Long searchTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutocompleteResponse {
        private List<String> suggestions;
        private String query;
        private String entityType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchStatsResponse {
        private Map<String, Long> entityCounts;
        private Long totalResults;
        private String query;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchHistoryResponse {
        private List<String> history;
        private Integer totalCount;
    }
}
