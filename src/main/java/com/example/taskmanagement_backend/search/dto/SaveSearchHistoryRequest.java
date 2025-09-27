package com.example.taskmanagement_backend.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for saving search history
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaveSearchHistoryRequest {

    /**
     * The search query to save
     */
    private String query;

    /**
     * Optional: Search context or metadata
     */
    private String context;
}
