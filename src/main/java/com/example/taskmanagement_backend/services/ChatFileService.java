package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ChatDto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 💬📁 Chat File Service
 * Specialized service for handling file uploads/downloads in chat messages
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatFileService {

    private final S3Service s3Service;

    @Value("${chat.file.max-size:10485760}") // 10MB default
    private long maxFileSize;

    @Value("${chat.file.allowed-types:image/jpeg,image/png,image/gif,image/webp,video/mp4,video/avi,video/mov,video/wmv,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,application/zip,application/rar}")
    private String allowedFileTypes;

    // Supported image types for preview generation
    private static final Set<String> IMAGE_TYPES = Set.of(
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    // Supported video types for preview
    private static final Set<String> VIDEO_TYPES = Set.of(
        "video/mp4", "video/avi", "video/mov", "video/wmv", "video/flv", "video/webm"
    );

    // Document types
    private static final Set<String> DOCUMENT_TYPES = Set.of(
        "application/pdf", "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain", "text/csv"
    );

    /**
     * 📤 Upload file for chat message
     * 🔧 Updated to store S3 key instead of presigned URL to avoid 403 Forbidden errors
     */
    public ChatFileUploadResponseDto uploadChatFile(MultipartFile file, Long userId, Long conversationId) {
        try {
            // Validate file
            validateFile(file);

            String originalFileName = file.getOriginalFilename();
            String contentType = file.getContentType();
            long fileSize = file.getSize();

            log.info("🚀 Uploading chat file: {} (type: {}, size: {} bytes)", originalFileName, contentType, fileSize);

            // Upload to S3 with chat-specific prefix
            String s3Key = s3Service.uploadFile(
                "chat/" + conversationId + "/" + originalFileName,
                file.getInputStream(),
                fileSize,
                contentType
            );

            // 🔧 Store S3 key instead of presigned URL to avoid 403 Forbidden errors
            // The presigned URL will be generated on-demand when needed
            String fileUrl = s3Key; // Store S3 key directly
            String previewUrl = generatePreviewUrl(s3Key, contentType);

            // Determine file category
            FileCategory category = determineFileCategory(contentType);

            ChatFileUploadResponseDto response = ChatFileUploadResponseDto.builder()
                    .fileName(originalFileName)
                    .fileUrl(fileUrl) // 🔧 Now stores S3 key instead of presigned URL
                    .previewUrl(previewUrl)
                    .fileSize(fileSize)
                    .contentType(contentType)
                    .category(category.name())
                    .s3Key(s3Key)
                    .uploadedAt(java.time.LocalDateTime.now())
                    .build();

            log.info("✅ Chat file uploaded successfully: {} (S3 key: {})", originalFileName, s3Key);
            return response;

        } catch (IOException e) {
            log.error("❌ Error uploading chat file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload chat file: " + e.getMessage(), e);
        }
    }

    /**
     * 📥 Get file download URL (generates fresh presigned URL)
     * 🔧 Always generates fresh presigned URL to avoid 403 Forbidden errors
     */
    public String getFileDownloadUrl(String s3Key) {
        try {
            // Generate fresh presigned URL to avoid 403 Forbidden errors
            String presignedUrl = s3Service.generateDownloadUrl(s3Key);
            log.debug("📥 Generated fresh download URL for S3 key: {}", s3Key);
            return presignedUrl;
        } catch (Exception e) {
            log.error("❌ Error generating download URL for chat file: {}", s3Key, e);
            throw new RuntimeException("Failed to generate download URL: " + e.getMessage(), e);
        }
    }

    /**
     * 🖼️ Generate preview URL for supported file types (fresh presigned URL)
     * 🔧 Always generates fresh presigned URL to avoid 403 Forbidden errors
     */
    public String generatePreviewUrl(String s3Key, String contentType) {
        try {
            // For images and videos, return fresh presigned URL to avoid 403 Forbidden errors
            if (isPreviewableType(contentType)) {
                String presignedUrl = s3Service.generateDownloadUrl(s3Key);
                log.debug("🖼️ Generated fresh preview URL for S3 key: {}", s3Key);
                return presignedUrl;
            }

            // For documents, could integrate with a document preview service
            // For now, return null to indicate no preview available
            return null;

        } catch (Exception e) {
            log.error("❌ Error generating preview URL for: {}", s3Key, e);
            return null;
        }
    }

    /**
     * 🗑️ Delete chat file
     */
    public void deleteChatFile(String s3Key) {
        try {
            s3Service.deleteFile(s3Key);
            log.info("🗑️ Chat file deleted: {}", s3Key);
        } catch (Exception e) {
            log.error("❌ Error deleting chat file: {}", s3Key, e);
            throw new RuntimeException("Failed to delete chat file: " + e.getMessage(), e);
        }
    }

    /**
     * 📋 Get file metadata
     */
    public ChatFileMetadataDto getFileMetadata(String s3Key) {
        try {
            // This would typically get metadata from S3 or database
            // For now, return basic info
            return ChatFileMetadataDto.builder()
                    .s3Key(s3Key)
                    .downloadUrl(s3Service.generateDownloadUrl(s3Key))
                    .build();

        } catch (Exception e) {
            log.error("❌ Error getting file metadata: {}", s3Key, e);
            throw new RuntimeException("Failed to get file metadata: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ Validate uploaded file
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // Check file size
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " + maxFileSize + " bytes");
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedFileType(contentType)) {
            throw new IllegalArgumentException("File type not allowed: " + contentType);
        }

        // Check filename
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }
    }

    /**
     * 🔍 Check if file type is allowed
     */
    private boolean isAllowedFileType(String contentType) {
        Set<String> allowedTypes = new HashSet<>(Arrays.asList(allowedFileTypes.split(",")));
        return allowedTypes.contains(contentType.toLowerCase());
    }

    /**
     * 🖼️ Check if file type supports preview
     */
    private boolean isPreviewableType(String contentType) {
        return IMAGE_TYPES.contains(contentType.toLowerCase()) ||
               VIDEO_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * 📁 Determine file category
     */
    private FileCategory determineFileCategory(String contentType) {
        String lowerContentType = contentType.toLowerCase();

        if (IMAGE_TYPES.contains(lowerContentType)) {
            return FileCategory.IMAGE;
        } else if (VIDEO_TYPES.contains(lowerContentType)) {
            return FileCategory.VIDEO;
        } else if (DOCUMENT_TYPES.contains(lowerContentType)) {
            return FileCategory.DOCUMENT;
        } else {
            return FileCategory.OTHER;
        }
    }

    /**
     * 📊 Get file upload statistics
     */
    public ChatFileStatsDto getFileStats(Long conversationId) {
        // This would typically query the database for file statistics
        // For now, return empty stats
        return ChatFileStatsDto.builder()
                .conversationId(conversationId)
                .totalFiles(0)
                .totalSize(0L)
                .imageCount(0)
                .videoCount(0)
                .documentCount(0)
                .build();
    }

    /**
     * 📋 File categories enum
     */
    public enum FileCategory {
        IMAGE, VIDEO, DOCUMENT, OTHER
    }
}
