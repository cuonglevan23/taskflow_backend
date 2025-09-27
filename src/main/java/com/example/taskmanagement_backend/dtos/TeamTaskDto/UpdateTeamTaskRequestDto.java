package com.example.taskmanagement_backend.dtos.TeamTaskDto;

import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.enums.TaskPriority;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTeamTaskRequestDto {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private TaskStatus status;

    private TaskPriority priority;

    private LocalDate startDate;

    private LocalDate deadline;

    @Min(value = 0, message = "Estimated hours must be non-negative")
    private Integer estimatedHours;

    @Min(value = 0, message = "Actual hours must be non-negative")
    private Integer actualHours;

    @Min(value = 0, message = "Progress percentage must be between 0 and 100")
    @Max(value = 100, message = "Progress percentage must be between 0 and 100")
    private Integer progressPercentage;

    @Pattern(regexp = "MEETING|PLANNING|REVIEW|ADMIN|TRAINING|RESEARCH|OTHER",
             message = "Task category must be one of: MEETING, PLANNING, REVIEW, ADMIN, TRAINING, RESEARCH, OTHER")
    private String taskCategory;

    private Long assigneeId;

    private List<Long> assignedMemberIds;

    private Long relatedProjectId;

    private Long parentTaskId;

    // Recurring task settings
    private Boolean isRecurring;

    @Pattern(regexp = "DAILY|WEEKLY|MONTHLY",
             message = "Recurrence pattern must be one of: DAILY, WEEKLY, MONTHLY")
    private String recurrencePattern;

    private LocalDate recurrenceEndDate;
}