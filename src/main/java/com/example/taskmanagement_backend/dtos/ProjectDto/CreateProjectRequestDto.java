package com.example.taskmanagement_backend.dtos.ProjectDto;


import com.example.taskmanagement_backend.enums.ProjectStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequestDto {

    @NotBlank(message = "Project name is required")
    private String name;

    private String description;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private ProjectStatus status;

    private Long ownerId;

    private Long organizationId;

    private Long teamId; // Optional - for team projects

    @JsonProperty("isPersonal")
    @Builder.Default
    private boolean personal = false; // True for personal projects
}
