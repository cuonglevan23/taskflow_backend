package com.example.taskmanagement_backend.dtos.PostDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentRequestDto {
    private String content;
    private Long parentCommentId; // Optional: for replying to existing comment
}
