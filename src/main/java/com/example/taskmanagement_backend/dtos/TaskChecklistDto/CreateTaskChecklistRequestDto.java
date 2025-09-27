package com.example.taskmanagement_backend.dtos.TaskChecklistDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskChecklistRequestDto {

    @NotNull(message = "Task ID is required")
    private Long taskId;

    @NotBlank(message = "Item is required")
    private String item;
}
