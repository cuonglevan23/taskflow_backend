package com.example.taskmanagement_backend.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Search criteria for team search operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamSearchCriteria {

    /**
     * Search term for team name/description
     */
    private String searchTerm;

    /**
     * Team types to filter by
     */
    private List<String> teamTypes;

    /**
     * Departments to filter by
     */
    private List<String> departments;

    /**
     * Minimum number of members
     */
    private Integer minMembers;

    /**
     * Maximum number of members
     */
    private Integer maxMembers;

    /**
     * User ID for filtering teams user belongs to
     */
    private Long userId;

    /**
     * Organization ID for filtering by organization
     */
    private Long organizationId;

    /**
     * Active status filter
     */
    private Boolean isActive;

    /**
     * Public status filter
     */
    private Boolean isPublic;

    /**
     * Check if any filters are applied
     */
    public boolean hasFilters() {
        return (teamTypes != null && !teamTypes.isEmpty()) ||
               (departments != null && !departments.isEmpty()) ||
               minMembers != null ||
               maxMembers != null ||
               organizationId != null ||
               isActive != null ||
               isPublic != null;
    }
}
