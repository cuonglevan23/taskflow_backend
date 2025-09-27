package com.example.taskmanagement_backend.dtos.NoteDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteAttachmentResponseDto {

    private Long id;
    private String fileName;
    private String storedFileName;
    private String contentType;
    private Long fileSize;
    private String formattedFileSize;
    private String description;
    private Boolean isImage;
    private String fileExtension;

    // Note info
    private Long noteId;
    private String noteTitle;

    // Uploader info
    private Long uploadedById;
    private String uploadedByName;
    private String uploadedByEmail;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Download URL
    private String downloadUrl;
}
