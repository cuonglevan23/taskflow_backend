package com.example.taskmanagement_backend.dtos.CommentDto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponseDto {

    private Long id;

    private Long userId;

    private Long taskId;

    private String content;

    private LocalDateTime createdAt;
}
