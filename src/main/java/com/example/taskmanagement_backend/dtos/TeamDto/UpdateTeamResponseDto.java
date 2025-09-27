package com.example.taskmanagement_backend.dtos.TeamDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTeamResponseDto {
    private String name;

    private String description;

    private Long projectId;

    private LocalDateTime updatedAt;
}
