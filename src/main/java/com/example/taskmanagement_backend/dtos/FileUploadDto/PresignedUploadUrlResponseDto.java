package com.example.taskmanagement_backend.dtos.FileUploadDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for presigned URL generation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadUrlResponseDto {

    private String uploadUrl;
    private String downloadUrl;
    private String fileKey;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private LocalDateTime expiresAt;
    private Long taskId;

    // Additional fields for frontend handling
    private String bucketName;
    private Integer expirationSeconds;
}
