package com.example.taskmanagement_backend.dtos.UserDto;

import com.example.taskmanagement_backend.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatusDto {
    private Long userId;
    private String userEmail;
    private String userFullName;
    private UserStatus previousStatus;
    private UserStatus currentStatus;
    private String reason;
    private String changedBy;
    private LocalDateTime changedAt;
    private boolean success;
    private String message;

    public static UserStatusDto success(Long userId, String userEmail, String userFullName,
                                       UserStatus previousStatus, UserStatus currentStatus,
                                       String reason, String changedBy) {
        return UserStatusDto.builder()
            .userId(userId)
            .userEmail(userEmail)
            .userFullName(userFullName)
            .previousStatus(previousStatus)
            .currentStatus(currentStatus)
            .reason(reason)
            .changedBy(changedBy)
            .changedAt(LocalDateTime.now())
            .success(true)
            .message("User status changed successfully from " + previousStatus + " to " + currentStatus)
            .build();
    }

    public static UserStatusDto failure(Long userId, String userEmail, String message) {
        return UserStatusDto.builder()
            .userId(userId)
            .userEmail(userEmail)
            .success(false)
            .message(message)
            .changedAt(LocalDateTime.now())
            .build();
    }
}
