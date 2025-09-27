package com.example.taskmanagement_backend.dtos.PostDto;

import com.example.taskmanagement_backend.enums.PostPrivacy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostJsonRequestDto {
    private String content; // New content for the post
    private PostPrivacy privacy; // New privacy setting
    private Long linkedTaskId; // Optional: update task link
    private Long linkedProjectId; // Optional: update project link
    private Boolean isPinned; // Optional: update pinned status

    // IDs of existing images/files to remove (no file upload in JSON)
    private List<Long> removeImageIds; // IDs of images to remove
    private List<Long> removeFileIds; // IDs of files to remove
}
