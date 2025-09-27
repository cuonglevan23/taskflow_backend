package com.example.taskmanagement_backend.dtos.TeamDto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTeamRequestDto {

    @NotBlank(message = "Team name is required")
    private String name;

    private String description;

    private Long organizationId; // Optional - for organization teams

    @Builder.Default
    private boolean isDefaultWorkspace = false;
}