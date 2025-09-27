package com.example.taskmanagement_backend.dtos.PostDto;

import com.example.taskmanagement_backend.enums.PostPrivacy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequestDto {
    private String content;
    @Builder.Default
    private PostPrivacy privacy = PostPrivacy.FRIENDS;
    private Long linkedTaskId; // Optional: link to existing task
    private Long linkedProjectId; // Optional: link to existing project
    private MultipartFile image; // Optional: single image attachment (for backward compatibility)
    private List<MultipartFile> images; // Optional: multiple image attachments
    private List<MultipartFile> files; // Optional: multiple file attachments
    @Builder.Default
    private Boolean isPinned = false;
}
