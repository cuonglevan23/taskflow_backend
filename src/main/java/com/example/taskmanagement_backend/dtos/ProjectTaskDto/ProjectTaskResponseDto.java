package com.example.taskmanagement_backend.dtos.ProjectTaskDto;

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
public class ProjectTaskResponseDto {

    private Long id;
    private String title;
    private String description;
    private String name; // Used by TaskService for project name
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate startDate;
    private LocalDate deadline;
    private Integer estimatedHours;
    private Integer actualHours;
    private Integer progressPercentage;

    // Project information
    private Long projectId;
    private String projectName;

    // Creator information
    private Long creatorId;
    private String creatorName;
    private String creatorEmail;

    // Assignee information
    private Long assigneeId;
    private String assigneeName;
    private String assigneeEmail;

    private List<AssigneeDto> additionalAssignees;

    // Parent task information
    private Long parentTaskId;
    private String parentTaskTitle;

    // Subtasks information
    private List<SubtaskDto> subtasks;
    private int subtaskCount;

    // File attachment
    private String urlFile;
    private String comment;

    // Google Calendar integration fields
    private String googleCalendarEventId;
    private String googleCalendarEventUrl;
    private String googleMeetLink;
    private Boolean isSyncedToCalendar;
    private LocalDateTime calendarSyncedAt;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Nested DTOs
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssigneeDto {
        private Long id;
        private String name;
        private String email;
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
    }
}