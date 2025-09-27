package com.example.taskmanagement_backend.dtos.TaskCommentDto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskCommentRequestDto {

    private String content;
}
