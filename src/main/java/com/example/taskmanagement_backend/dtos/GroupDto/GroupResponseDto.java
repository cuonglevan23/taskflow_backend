package com.example.taskmanagement_backend.dtos.GroupDto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponseDto {

    private Long id;

    private String name;

    private String description;

    private Long projectId;

    private Long leaderId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}