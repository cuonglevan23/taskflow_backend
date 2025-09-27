package com.example.taskmanagement_backend.dtos.FileUploadDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for file upload confirmation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponseDto {

    private String fileKey;
    private String fileName;
    private String downloadUrl;
    private Long fileSize;
    private String contentType;
    private LocalDateTime uploadedAt;
    private Long taskId;
    private String uploadStatus; // SUCCESS, FAILED, PENDING
    private String message;
    private String progressSessionId; // Track upload progress session
}
