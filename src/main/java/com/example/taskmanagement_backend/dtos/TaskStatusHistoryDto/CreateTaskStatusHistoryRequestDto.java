package com.example.taskmanagement_backend.dtos.TaskStatusHistoryDto;




import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskStatusHistoryRequestDto {

    @NotNull
    private Long taskId;

    @NotBlank
    private String oldStatus;

    @NotBlank
    private String newStatus;

    @NotNull
    private Long changedBy;
}