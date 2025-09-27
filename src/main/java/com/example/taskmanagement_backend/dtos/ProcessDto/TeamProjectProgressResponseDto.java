package com.example.taskmanagement_backend.dtos.ProcessDto;

import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamProjectProgressResponseDto {
    
    private Long id;
    private Long teamId;
    private String teamName;
    private Long projectId;
    private String projectName;
    private Integer totalTasks;
    private Integer completedTasks;
    private Double completionPercentage;
    private LocalDateTime lastUpdated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ✅ NEW: Thông tin user profiles cho team-project progress
    private List<UserProfileDto> teamMembersInProject; // Members của team trong project này
    private UserProfileDto lastUpdatedBy;              // User cuối cùng update progress
}