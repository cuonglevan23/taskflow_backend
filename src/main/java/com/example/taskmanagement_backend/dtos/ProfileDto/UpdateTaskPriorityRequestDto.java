package com.example.taskmanagement_backend.dtos.ProfileDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskPriorityRequestDto {
    
    @NotEmpty(message = "Task priorities list cannot be empty")
    @Valid
    private List<TaskPriorityUpdateDto> priorities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskPriorityUpdateDto {
        private Long id; // null for new priorities
        private String priorityKey;
        private String displayName;
        private String color;
        private Integer sortOrder;
        private Boolean isActive;
    }
}