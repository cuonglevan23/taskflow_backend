package com.example.taskmanagement_backend.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Search criteria for user search operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchCriteria {

    /**
     * Search term for name/email/username
     */
    private String searchTerm;

    /**
     * Departments to filter by
     */
    private List<String> departments;

    /**
     * Skills to filter by
     */
    private List<String> skills;

    /**
     * Company to filter by
     */
    private String company;

    /**
     * Job title to filter by
     */
    private String jobTitle;

    /**
     * Location to filter by
     */
    private String location;

    /**
     * Premium status filter
     */
    private Boolean isPremium;

    /**
     * Online status filter
     */
    private Boolean isOnline;

    /**
     * Active status filter
     */
    private Boolean isActive;

    /**
     * Searchable status filter
     */
    private Boolean searchable;

    /**
     * Check if any filters are applied
     */
    public boolean hasFilters() {
        return (departments != null && !departments.isEmpty()) ||
               (skills != null && !skills.isEmpty()) ||
               company != null ||
               jobTitle != null ||
               location != null ||
               isPremium != null ||
               isOnline != null ||
               isActive != null ||
               searchable != null;
    }
}
