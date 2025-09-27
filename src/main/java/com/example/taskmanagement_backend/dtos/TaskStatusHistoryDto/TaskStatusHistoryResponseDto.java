package com.example.taskmanagement_backend.dtos.TaskStatusHistoryDto;


import lombok.AllArgsConstructor;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusHistoryResponseDto {

    private Long id;
    private String oldStatus;
    private String newStatus;
    private Long taskId;
    private Long changedBy;
    private LocalDateTime changedAt;
}