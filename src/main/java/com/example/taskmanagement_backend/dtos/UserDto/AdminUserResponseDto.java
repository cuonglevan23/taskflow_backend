package com.example.taskmanagement_backend.dtos.UserDto;

import com.example.taskmanagement_backend.dtos.RoleDto.RoleResponseDto;
import com.example.taskmanagement_backend.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserResponseDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String avatarUrl;
    private UserStatus status;
    private boolean firstLogin;
    private boolean deleted;
    private boolean online;
    private boolean isEmailVerified;
    private boolean isPremium;
    private boolean isUpgraded;
    private String premiumPlanType;
    private LocalDateTime premiumExpiry;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime lastSeenAt;
    private String department;
    private String jobTitle;
    private String aboutMe;
    private List<RoleResponseDto> roles;
    private List<String> roleNames;
    private String organizationName;
    private int totalTeams;
    private int totalProjects;
    private int totalTasks;
    private boolean isOnline;
    private String onlineStatus;

    // Additional admin-specific fields
    private String registrationSource; // "MANUAL", "GOOGLE_OAUTH", "INVITATION"
    private String statusReason; // Reason for status change (if any)
    private LocalDateTime statusChangedAt;
    private String statusChangedBy;

    // New fields to match frontend expectations
    private String displayName;
    private String fullName;
    private boolean emailVerified;
    private boolean premium;
    private boolean upgraded;

    public String getFullName() {
        if (firstName == null && lastName == null) {
            return email;
        }
        return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
    }

    public String getDisplayName() {
        String fullName = getFullName();
        return fullName.isEmpty() ? email : fullName;
    }
}
