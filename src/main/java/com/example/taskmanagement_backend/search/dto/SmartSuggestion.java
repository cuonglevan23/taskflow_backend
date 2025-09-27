package com.example.taskmanagement_backend.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for smart search suggestions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartSuggestion {

    /**
     * Suggested search term
     */
    private String suggestion;

    /**
     * Type of suggestion (query, filter, entity)
     */
    private String type;

    /**
     * Confidence score (0.0 to 1.0)
     */
    private Double confidence;

    /**
     * Description of the suggestion
     */
    private String description;

    /**
     * Entity type this suggestion applies to
     */
    private String entityType;
}
