package com.example.taskmanagement_backend.dtos.TeamDto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponseDto {

    private Long id;
    private String name;
    private String description;
    private boolean isDefaultWorkspace;
    private Long createdById;
    private Long organizationId;

    // ✅ ADD: Current user's role in this team
    private String currentUserRole;
    private boolean isCurrentUserMember;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}