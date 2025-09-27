package com.example.taskmanagement_backend.dtos.CommentDto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCommentRequestDto {

    @NotBlank(message = "Content is required")
    private String content;
}
