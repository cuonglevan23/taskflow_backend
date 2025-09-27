package com.example.taskmanagement_backend.dtos.ProjectDto;

import com.example.taskmanagement_backend.entities.Organization;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.ProjectStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponseDto {

    private Long id;
    private String name;
    private String description;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate endDate;

    private Long ownerId;

    private Long organizationId;

    private Long teamId;

    private Long createdById;

    private boolean isPersonal;

    // ✅ ADD: Current user's role in this project
    private String currentUserRole;
    private boolean isCurrentUserMember;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


}
