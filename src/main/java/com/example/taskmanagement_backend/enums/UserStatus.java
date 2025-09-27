package com.example.taskmanagement_backend.enums;

/**
 * Enum representing the status of a user account
 */
public enum UserStatus {
    ACTIVE("Active", "User account is active and can access the system"),
    INACTIVE("Inactive", "User account is temporarily inactive"),
    SUSPENDED("Suspended", "User account is suspended due to policy violations"),
    PENDING("Pending", "User account is pending activation"),
    DELETED("Deleted", "User account has been soft deleted"),
    LOCKED("Locked", "User account is locked due to security reasons");

    private final String displayName;
    private final String description;

    UserStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if the status allows user to access the system
     */
    public boolean isAccessible() {
        return this == ACTIVE;
    }

    /**
     * Check if the status allows admin to reactivate the account
     */
    public boolean canBeReactivated() {
        return this == INACTIVE || this == SUSPENDED || this == LOCKED;
    }
}
