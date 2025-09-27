package com.example.taskmanagement_backend.dtos.TaskChecklistDto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskChecklistResponseDto {

    private Long id;

    private Long taskId;

    private String item;

    private Boolean isCompleted;

    private LocalDateTime createdAt;
}
