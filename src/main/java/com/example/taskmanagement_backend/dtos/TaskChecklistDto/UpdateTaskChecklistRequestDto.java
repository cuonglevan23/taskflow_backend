package com.example.taskmanagement_backend.dtos.TaskChecklistDto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskChecklistRequestDto {

    private String item;

    private Boolean isCompleted;
}
