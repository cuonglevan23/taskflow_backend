package com.example.taskmanagement_backend.dtos.ProjectMemberDto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberResponseDto {

    private Long id;

    private Long projectId;

    private Long userId;

    private LocalDateTime joinedAt;
}
