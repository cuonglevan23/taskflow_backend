package com.example.taskmanagement_backend.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event for tracking search history
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryEvent {

    /**
     * User ID who performed the search
     */
    private Long userId;

    /**
     * Search query string
     */
    private String query;

    /**
     * Timestamp when the search was performed
     */
    private Long timestamp;
}
