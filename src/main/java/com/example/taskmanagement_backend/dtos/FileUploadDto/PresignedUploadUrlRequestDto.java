package com.example.taskmanagement_backend.dtos.FileUploadDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating presigned URL for file upload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadUrlRequestDto {

    @NotBlank(message = "File name is required")
    private String fileName;

    @NotBlank(message = "Content type is required")
    private String contentType;

    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long fileSize;

    @NotNull(message = "Task ID is required")
    private Long taskId;

    // Optional: for organizing files by user/project
    private String folder;
}
