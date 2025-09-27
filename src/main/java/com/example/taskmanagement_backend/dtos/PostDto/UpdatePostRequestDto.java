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
public class UpdatePostRequestDto {
    private String content; // New content for the post
    private PostPrivacy privacy; // New privacy setting
    private Long linkedTaskId; // Optional: update task link
    private Long linkedProjectId; // Optional: update project link
    private MultipartFile image; // Optional: new single image attachment
    private List<MultipartFile> images; // Optional: new multiple image attachments
    private List<MultipartFile> files; // Optional: new multiple file attachments
    private Boolean isPinned; // Optional: update pinned status

    // IDs of existing images/files to remove
    private List<Long> removeImageIds; // IDs of images to remove
    private List<Long> removeFileIds; // IDs of files to remove
}
