package com.example.taskmanagement_backend.dtos.ProcessDto;

import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectProgressResponseDto {
    
    private Long id;
    private Long projectId;
    private String projectName;
    private Integer totalTasks;
    private Integer completedTasks;
    private Double completionPercentage;
    private Integer totalTeams;
    private LocalDateTime lastUpdated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Optional: Include team-project progress details
    private List<TeamProjectProgressResponseDto> teamProjectProgressList;

    // ✅ NEW: Thông tin user profiles cho project
    private UserProfileDto projectCreator;      // User tạo project
    private List<UserProfileDto> projectMembers; // Tất cả members trong project
    private UserProfileDto lastUpdatedBy;       // User cuối cùng update progress
}