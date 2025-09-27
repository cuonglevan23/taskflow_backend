package com.example.taskmanagement_backend.dtos.TaskActivityDto;

import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import com.example.taskmanagement_backend.enums.TaskActivityType;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskActivityResponseDto {

    private Long id;
    private Long taskId;
    private TaskActivityType activityType;
    private String description;
    private String oldValue;
    private String newValue;
    private String fieldName;
    private LocalDateTime createdAt;

    // User information
    private UserProfileDto user;

    // Formatted message for display
    private String formattedMessage;

    // Time ago (e.g., "2 minutes ago", "4 days ago")
    private String timeAgo;
}
