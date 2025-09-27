package com.example.taskmanagement_backend.search.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for smart suggestions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore unknown fields to prevent future errors
public class SmartSuggestionsRequest {

    /**
     * Current search context (can be a string or complex object)
     */
    private Object context;

    /**
     * Current partial query - also accepts "query" field from frontend
     */
    @JsonAlias({"query"}) // Map both "query" and "partialQuery" to this field
    private String partialQuery;

    /**
     * User's recent search history
     */
    private List<String> recentSearches;

    /**
     * Entity types to get suggestions for
     */
    private List<String> entityTypes;

    /**
     * Maximum number of suggestions to return
     */
    @Builder.Default
    private Integer maxSuggestions = 5;

    /**
     * User's current activity context
     */
    private String activityContext;

    // Convenience method to get query value regardless of field name used
    public String getQuery() {
        return partialQuery;
    }

    // Convenience method to set query value
    public void setQuery(String query) {
        this.partialQuery = query;
    }

    // Convenience method to get context as string if needed
    public String getContextAsString() {
        if (context == null) {
            return null;
        }
        if (context instanceof String) {
            return (String) context;
        }
        return context.toString();
    }
}
