package com.example.taskmanagement_backend.dtos.ProjectDto;

import com.example.taskmanagement_backend.enums.ProjectStatus;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProjectRequestDto {

    private String name;

    private String description;

    private ProjectStatus status;

    private LocalDate startDate;

    private LocalDate endDate;

    private Long ownerId;

    private Long organizationId;

    private Long teamId;

    private boolean isPersonal;
}
