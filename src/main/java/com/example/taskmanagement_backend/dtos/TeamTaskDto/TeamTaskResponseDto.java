package com.example.taskmanagement_backend.dtos.TeamTaskDto;

import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.enums.TaskPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamTaskResponseDto {

    private Long id;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate startDate;
    private LocalDate deadline;
    private Integer estimatedHours;
    private Integer actualHours;
    private Integer progressPercentage;
    private String taskCategory;

    // Team information
    private Long teamId;
    private String teamName;

    // User information
    private Long creatorId;
    private String creatorName;
    private String creatorEmail;

    private Long assigneeId;
    private String assigneeName;
    private String assigneeEmail;

    private List<AssignedMemberDto> assignedMembers;

    // Related project information
    private Long relatedProjectId;
    private String relatedProjectName;

    // Parent task information
    private Long parentTaskId;
    private String parentTaskTitle;

    // Subtasks information
    private List<SubtaskDto> subtasks;
    private int subtaskCount;

    // Recurring task information
    private Boolean isRecurring;
    private String recurrencePattern;
    private LocalDate recurrenceEndDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Nested DTOs
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedMemberDto {
        private Long id;
        private String name;
        private String email;
        private String role; // Team role
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubtaskDto {
        private Long id;
        private String title;
        private TaskStatus status;
        private Integer progressPercentage;
        private LocalDate deadline;
        private String taskCategory;
    }
}