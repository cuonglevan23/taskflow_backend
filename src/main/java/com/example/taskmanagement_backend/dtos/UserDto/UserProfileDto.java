package com.example.taskmanagement_backend.dtos.UserDto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {

    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    private String jobTitle;
    private String department;
    private String aboutMe;
    private String status;
    private String avatarUrl;
    private boolean isUpgraded;

    // Online status fields
    private String onlineStatus;        // "online", "offline", "away", "busy"
    private LocalDateTime lastSeen;     // Last time user was active
    private Boolean isOnline;           // Simple boolean for quick checks

    // Computed fields for frontend convenience
    private String displayName;  // firstName + lastName or username
    private String initials;     // First letters of firstName + lastName
}
