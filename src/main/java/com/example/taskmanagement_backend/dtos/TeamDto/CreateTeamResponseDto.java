package com.example.taskmanagement_backend.dtos.TeamDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTeamResponseDto {
    private String name;

    private String description;

    private Long project_id;
}
