package com.example.taskmanagement_backend.dtos.ProcessDto;

import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamProgressResponseDto {
    
    private Long id;
    private Long teamId;
    private String teamName;
    private Integer totalTasks;
    private Integer completedTasks;
    private Double completionPercentage;
    private LocalDateTime lastUpdated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ✅ NEW: Thông tin user profiles của team members
    private UserProfileDto teamOwner;           // Owner của team
    private List<UserProfileDto> teamMembers;   // Tất cả members trong team
    private UserProfileDto lastUpdatedBy;       // User cuối cùng update progress
}