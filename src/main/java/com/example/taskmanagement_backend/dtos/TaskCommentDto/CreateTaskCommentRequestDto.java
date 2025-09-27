package com.example.taskmanagement_backend.dtos.TaskCommentDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskCommentRequestDto {

    @NotBlank(message = "Comment content is required")
    private String content;

    @NotNull(message = "Task ID is required")
    private Long taskId;

    // User ID sẽ được lấy từ authentication context, không cần gửi từ frontend
}
