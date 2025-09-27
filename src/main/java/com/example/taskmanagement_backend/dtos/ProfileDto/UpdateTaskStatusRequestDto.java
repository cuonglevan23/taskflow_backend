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
public class UpdateTaskStatusRequestDto {
    
    @NotEmpty(message = "Task statuses list cannot be empty")
    @Valid
    private List<TaskStatusUpdateDto> statuses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStatusUpdateDto {
        private Long id; // null for new statuses
        private String statusKey;
        private String displayName;
        private String color;
        private Integer sortOrder;
        private Boolean isActive;
    }
}