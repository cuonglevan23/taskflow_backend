package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.FileUploadDto.FileUploadResponseDto;
import com.example.taskmanagement_backend.dtos.FileUploadDto.PresignedUploadUrlRequestDto;
import com.example.taskmanagement_backend.dtos.FileUploadDto.PresignedUploadUrlResponseDto;
import com.example.taskmanagement_backend.services.S3FileUploadService;
import com.example.taskmanagement_backend.services.TaskAttachmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * File Upload Controller supporting AWS S3 with 2025 best practices
 *
 * Endpoints:
 * 1. POST /api/files/presigned-upload-url - Generate presigned URL for direct frontend uploads
 * 2. POST /api/files/upload - Server-side upload (for small files or backend processing)
 * 3. GET /api/files/download/{fileKey} - Generate download URL
 * 4. DELETE /api/files/{fileKey} - Delete file
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final S3FileUploadService s3FileUploadService;
    private final TaskAttachmentService taskAttachmentService;

    /**
     * 🔗 Generate presigned URL for direct frontend uploads (Recommended for 2025)
     * Frontend Flow:
     * 1. Call this endpoint to get upload URL
     * 2. Upload file directly to S3 using the URL
     * 3. Call /api/files/upload-success to save attachment info
     */
    @PostMapping("/presigned-upload-url")
    public ResponseEntity<PresignedUploadUrlResponseDto> generatePresignedUploadUrl(
            @Valid @RequestBody PresignedUploadUrlRequestDto request) {

        log.info("📋 Generating presigned upload URL for task: {} file: {}",
                request.getTaskId(), request.getFileName());

        try {
            PresignedUploadUrlResponseDto response = s3FileUploadService.generatePresignedUploadUrl(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Invalid upload request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌ Failed to generate presigned URL", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * ✅ Confirm successful upload and save attachment info
     * Call this after successful presigned URL upload
     */
    @PostMapping("/upload-success")
    public ResponseEntity<Object> confirmUploadSuccess(@RequestBody UploadSuccessRequest request) {
        log.info("✅ Confirming upload success for task: {} file: {}",
                request.getTaskId(), request.getFileName());

        try {
            // Get current user for metadata
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String currentUserEmail = authentication != null ? authentication.getName() : "unknown";

            // Add metadata to the uploaded file
            boolean metadataAdded = s3FileUploadService.addMetadataToUploadedFile(
                request.getFileKey(),
                request.getFileName(),
                request.getTaskId(),
                currentUserEmail
            );

            if (!metadataAdded) {
                log.warn("⚠️ Failed to add metadata to uploaded file: {}", request.getFileKey());
            }

            // Save attachment info to database
            taskAttachmentService.saveAttachment(
                request.getTaskId(),
                request.getFileKey(),
                request.getFileName(),
                request.getFileSize(),
                request.getContentType(),
                request.getDownloadUrl()
            );

            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "File attachment saved successfully",
                "taskId", request.getTaskId(),
                "fileName", request.getFileName(),
                "fileKey", request.getFileKey()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to save attachment info", e);
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 📤 Server-side file upload with progress tracking
     * Automatically saves attachment info after successful upload
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<FileUploadResponseDto>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("taskId") Long taskId,
            @RequestParam(value = "folder", required = false) String folder) {

        log.info("📤 Server-side upload for task: {} file: {} ({}KB)",
                taskId, file.getOriginalFilename(), file.getSize() / 1024);

        try {
            // Validate file
            if (file.isEmpty()) {
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().build());
            }

            final String userEmail = getCurrentUserEmail();

            // Start async upload with progress tracking
            return s3FileUploadService.uploadFileAsync(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream(),
                    file.getSize(),
                    taskId
            ).thenApply(response -> {
                if ("SUCCESS".equals(response.getUploadStatus())) {
                    // Automatically save attachment info after successful upload
                    try {
                        if (userEmail != null) {
                            taskAttachmentService.saveAttachmentWithUser(
                                taskId,
                                response.getFileKey(),
                                response.getFileName(),
                                response.getFileSize(),
                                response.getContentType(),
                                response.getDownloadUrl(),
                                userEmail
                            );
                            log.info("✅ Attachment info saved automatically for task: {}", taskId);
                        } else {
                            log.warn("⚠️ Could not save attachment info - user not authenticated");
                        }
                    } catch (Exception e) {
                        log.error("⚠️ Failed to save attachment info automatically", e);
                    }

                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.internalServerError().body(response);
                }
            }).exceptionally(throwable -> {
                log.error("❌ Upload failed with exception", throwable);
                return ResponseEntity.internalServerError().build();
            });

        } catch (Exception e) {
            log.error("❌ Failed to upload file", e);
            return CompletableFuture.completedFuture(
                ResponseEntity.internalServerError().build());
        }
    }

    /**
     * 📥 Generate download URL for file access
     */
    @GetMapping("/download/{fileKey}")
    public ResponseEntity<String> generateDownloadUrl(@PathVariable String fileKey) {
        try {
            // Check if file exists
            if (!s3FileUploadService.fileExists(fileKey)) {
                return ResponseEntity.notFound().build();
            }

            // Generate download URL (valid for 1 hour)
            String downloadUrl = s3FileUploadService.generatePresignedDownloadUrl(
                    fileKey, java.time.Duration.ofHours(1));

            return ResponseEntity.ok(downloadUrl);
        } catch (Exception e) {
            log.error("❌ Failed to generate download URL for key: {}", fileKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 🗑️ Delete file from S3
     */
    @DeleteMapping("/{fileKey}")
    public ResponseEntity<String> deleteFile(@PathVariable String fileKey) {
        try {
            boolean deleted = s3FileUploadService.deleteFile(fileKey);
            if (deleted) {
                return ResponseEntity.ok("File deleted successfully");
            } else {
                return ResponseEntity.internalServerError().body("Failed to delete file");
            }
        } catch (Exception e) {
            log.error("❌ Failed to delete file: {}", fileKey, e);
            return ResponseEntity.internalServerError().body("Error deleting file: " + e.getMessage());
        }
    }

    /**
     * 📊 Get file metadata and info
     */
    @GetMapping("/info/{fileKey}")
    public ResponseEntity<Object> getFileInfo(@PathVariable String fileKey) {
        try {
            if (!s3FileUploadService.fileExists(fileKey)) {
                return ResponseEntity.notFound().build();
            }

            var metadata = s3FileUploadService.getFileMetadata(fileKey);

            return ResponseEntity.ok(java.util.Map.of(
                "fileKey", fileKey,
                "contentType", metadata.contentType(),
                "contentLength", metadata.contentLength(),
                "lastModified", metadata.lastModified(),
                "metadata", metadata.metadata()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to get file info: {}", fileKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Request DTO for upload success confirmation
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UploadSuccessRequest {
        private Long taskId;
        private String fileKey;
        private String fileName;
        private Long fileSize;
        private String contentType;
        private String downloadUrl;
    }

    private String getCurrentUserEmail() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not get current user for attachment saving", e);
        }
        return null;
    }
}
