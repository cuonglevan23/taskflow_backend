package com.example.taskmanagement_backend.dtos.TaskCommentDto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCommentResponseDto {

    private Long id;

    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long taskId;

    private Long userId;

    private String userEmail;

    private String userName; // Full name của user

    private String userAvatar; // Avatar URL của user (nếu có)
}
