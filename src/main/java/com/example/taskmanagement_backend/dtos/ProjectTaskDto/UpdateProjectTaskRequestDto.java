package com.example.taskmanagement_backend.dtos.ProjectTaskDto;

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
public class UpdateProjectTaskRequestDto {

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

    private Long assigneeId;

    private List<Long> additionalAssigneeIds;

    private Long parentTaskId;

    // Added for file upload support
    private String urlFile;

    // Added for comment support
    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String comment;
}