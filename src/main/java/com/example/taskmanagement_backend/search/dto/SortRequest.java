package com.example.taskmanagement_backend.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for sort settings in search requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SortRequest {

    /**
     * Field to sort by
     */
    @Builder.Default
    private String field = "relevance";

    /**
     * Sort direction (asc, desc)
     */
    @Builder.Default
    private String direction = "desc";

    /**
     * Available sort fields
     */
    public enum SortField {
        RELEVANCE("relevance"),
        CREATED_AT("createdAt"),
        UPDATED_AT("updatedAt"),
        NAME("name"),
        TITLE("title"),
        DUE_DATE("dueDate"),
        PRIORITY("priority"),
        STATUS("status");

        private final String value;

        SortField(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Sort directions
     */
    public enum SortDirection {
        ASC("asc"),
        DESC("desc");

        private final String value;

        SortDirection(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Get validated sort field
     */
    public String getField() {
        return field != null ? field : "relevance";
    }

    /**
     * Get validated sort direction
     */
    public String getDirection() {
        if ("asc".equalsIgnoreCase(direction) || "desc".equalsIgnoreCase(direction)) {
            return direction.toLowerCase();
        }
        return "desc";
    }
}
