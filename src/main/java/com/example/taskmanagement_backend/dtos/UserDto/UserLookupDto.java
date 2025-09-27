package com.example.taskmanagement_backend.dtos.UserDto;

import lombok.*;

/**
 * Lightweight DTO for user lookup operations
 * Optimized for performance and security - only essential fields
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLookupDto {

    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    private String avatarUrl;
    private boolean exists;
    private boolean isOnline;

    // Computed display name for UI
    public String getDisplayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (firstName != null) {
            return firstName;
        }
        if (username != null) {
            return username;
        }
        return email;
    }

    // Get initials for avatar fallback
    public String getInitials() {
        if (firstName != null && lastName != null) {
            return firstName.substring(0, 1).toUpperCase() + lastName.substring(0, 1).toUpperCase();
        }
        if (firstName != null && firstName.length() > 0) {
            return firstName.substring(0, 1).toUpperCase();
        }
        if (username != null && username.length() > 0) {
            return username.substring(0, 1).toUpperCase();
        }
        if (email != null && email.length() > 0) {
            return email.substring(0, 1).toUpperCase();
        }
        return "U";
    }
}
