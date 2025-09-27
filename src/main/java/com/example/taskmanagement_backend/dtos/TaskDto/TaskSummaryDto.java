package com.example.taskmanagement_backend.dtos.TaskDto;

import com.example.taskmanagement_backend.enums.TaskPriority;
import com.example.taskmanagement_backend.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ✅ PROJECTION DTO: Only essential fields for task list view
 * Reduces data transfer and improves performance
 */
@Data
@Builder
@NoArgsConstructor
public class TaskSummaryDto {
    
    private Long id;
    private String title;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate deadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long creatorId;
    private Long projectId;
    private Long teamId;
    private Long checklistCount;
    private Long assigneeCount;
    
    // Constructor for JPQL projection
    public TaskSummaryDto(Long id, String title, TaskStatus status, TaskPriority priority, 
                         LocalDate deadline, LocalDateTime createdAt, LocalDateTime updatedAt,
                         Long creatorId, Long projectId, Long teamId, 
                         Long checklistCount, Long assigneeCount) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.priority = priority;
        this.deadline = deadline;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.creatorId = creatorId;
        this.projectId = projectId;
        this.teamId = teamId;
        this.checklistCount = checklistCount;
        this.assigneeCount = assigneeCount;
    }
}