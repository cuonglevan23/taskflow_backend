package com.example.taskmanagement_backend.dtos.CommentDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentRequestDto {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Task ID is required")
    private Long taskId;

    @NotBlank(message = "Content is required")
    private String content;
}
