package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.FileUploadDto.FileUploadResponseDto;
import com.example.taskmanagement_backend.dtos.FileUploadDto.PresignedUploadUrlRequestDto;
import com.example.taskmanagement_backend.dtos.FileUploadDto.PresignedUploadUrlResponseDto;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.repositories.TaskJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * AWS S3 File Upload Service implementing 2025 best practices
 *
 * Key Features:
 * - Presigned URLs for secure direct uploads from frontend
 * - TransferManager for efficient multipart uploads (files > 8MB)
 * - User-based file organization
 * - Comprehensive file validation
 * - Progress tracking support
 * - Automatic retry and error handling
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileUploadService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3TransferManager transferManager;
    private final TaskJpaRepository taskRepository; // ✅ Inject Task repository
    private final TaskActivityService taskActivityService; // ✅ Inject TaskActivityService for logging

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.presigned-url-expiration:3600}")
    private Integer presignedUrlExpiration;

    @Value("${aws.s3.max-file-size:10485760}")
    private Long maxFileSize;

    @Value("${aws.s3.allowed-extensions}")
    private String allowedExtensions;

    @Value("${aws.s3.key-prefix:task-files/}")
    private String keyPrefix;

    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
        "image/jpeg", "image/png", "image/gif", "image/webp",
        "application/pdf", "text/plain",
        "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "video/mp4", "video/avi", "video/quicktime"
    );

    // ✅ FIX: Add ExecutorService for AsyncRequestBody (not just Executor)
    private static final java.util.concurrent.ExecutorService UPLOAD_EXECUTOR =
        Executors.newFixedThreadPool(10, r -> {
            Thread t = new Thread(r, "s3-upload-thread");
            t.setDaemon(true);
            return t;
        });

    /**
     * 2025 Best Practice: Generate presigned URL for direct frontend uploads
     * - Frontend uploads directly to S3 without going through backend
     * - Reduces server load and improves upload performance
     * - Secure with time-limited URLs and content validation
     */
    public PresignedUploadUrlResponseDto generatePresignedUploadUrl(PresignedUploadUrlRequestDto request) {
        log.info("🔗 Generating presigned upload URL for file: {} ({}MB)",
                request.getFileName(), request.getFileSize() / 1024.0 / 1024.0);

        // Validate request
        validateUploadRequest(request);

        // Get current user for file organization
        String currentUserEmail = getCurrentUserEmail();

        // Generate unique file key with user-based organization
        String fileKey = generateFileKey(currentUserEmail, request);

        try {
            // ✅ FIX: Normalize content-type to prevent charset mismatch
            String normalizedContentType = normalizeContentType(request.getContentType());

            // ✅ CRITICAL FIX: Do NOT include metadata in presigned URL signature
            // Metadata headers cause signature mismatches since frontend can't send them
            // We'll add metadata after upload using the addMetadataToUploadedFile method
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(normalizedContentType) // Use normalized content-type
                    .contentLength(request.getFileSize())
                    // ✅ REMOVED: No metadata in presigned URL - causes signature issues
                    .build();

            // Generate presigned URL for upload with longer expiration
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(presignedUrlExpiration))
                    .putObjectRequest(putObjectRequest)
                    .build();

            String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

            // Generate download URL (will be available after upload)
            String downloadUrl = generatePresignedDownloadUrl(fileKey, Duration.ofDays(7));

            log.info("✅ Generated presigned URLs for file: {} -> key: {}, normalized content-type: {}",
                    request.getFileName(), fileKey, normalizedContentType);
            log.info("🔧 Metadata will be added after upload to avoid signature issues");

            return PresignedUploadUrlResponseDto.builder()
                    .uploadUrl(uploadUrl)
                    .downloadUrl(downloadUrl)
                    .fileKey(fileKey)
                    .fileName(request.getFileName())
                    .fileSize(request.getFileSize())
                    .contentType(normalizedContentType) // Return normalized content-type to frontend
                    .expiresAt(LocalDateTime.now().plusSeconds(presignedUrlExpiration))
                    .taskId(request.getTaskId())
                    .bucketName(bucketName)
                    .expirationSeconds(presignedUrlExpiration)
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to generate presigned URL for file: {}", request.getFileName(), e);
            throw new RuntimeException("Failed to generate upload URL: " + e.getMessage(), e);
        }
    }

    /**
     * 2025 Best Practice: TransferManager for efficient server-side uploads
     * - Automatic multipart uploads for files > 8MB
     * - Progress tracking and retry logic
     * - Optimal for backend processing scenarios
     */
    public CompletableFuture<FileUploadResponseDto> uploadFileAsync(String fileName, String contentType,
                                                                   InputStream inputStream, Long fileSize, Long taskId) {
        log.info("📤 Starting async upload for file: {} ({}MB)", fileName, fileSize / 1024.0 / 1024.0);

        String currentUserEmail = getCurrentUserEmail();
        String fileKey = generateFileKey(currentUserEmail, fileName, taskId);

        try {
            // ✅ FIX: Create upload request with proper AsyncRequestBody
            UploadRequest uploadRequest = UploadRequest.builder()
                    .putObjectRequest(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(fileKey)
                            .contentType(contentType)
                            .contentLength(fileSize)
                            .metadata(buildMetadataMap(fileName, taskId, currentUserEmail))
                            .build())
                    // ✅ FIX: Use proper executor instead of null
                    .requestBody(AsyncRequestBody.fromInputStream(inputStream, fileSize, UPLOAD_EXECUTOR))
                    .build();

            // Start upload with TransferManager
            Upload upload = transferManager.upload(uploadRequest);

            // Return CompletableFuture for async handling
            return upload.completionFuture().thenApply(completedUpload -> {
                log.info("✅ Upload completed for file: {} -> key: {}", fileName, fileKey);

                String downloadUrl = generatePresignedDownloadUrl(fileKey, Duration.ofDays(7));

                return FileUploadResponseDto.builder()
                        .fileKey(fileKey)
                        .fileName(fileName)
                        .downloadUrl(downloadUrl)
                        .fileSize(fileSize)
                        .contentType(contentType)
                        .uploadedAt(LocalDateTime.now())
                        .taskId(taskId)
                        .uploadStatus("SUCCESS")
                        .message("File uploaded successfully")
                        .build();
            }).exceptionally(throwable -> {
                log.error("❌ Upload failed for file: {}", fileName, throwable);

                return FileUploadResponseDto.builder()
                        .fileName(fileName)
                        .fileSize(fileSize)
                        .contentType(contentType)
                        .taskId(taskId)
                        .uploadStatus("FAILED")
                        .message("Upload failed: " + throwable.getMessage())
                        .build();
            });

        } catch (Exception e) {
            log.error("❌ Failed to start upload for file: {}", fileName, e);

            return CompletableFuture.completedFuture(
                FileUploadResponseDto.builder()
                        .fileName(fileName)
                        .taskId(taskId)
                        .uploadStatus("FAILED")
                        .message("Failed to start upload: " + e.getMessage())
                        .build()
            );
        }
    }

    /**
     * 2025 Best Practice: Server-side upload for small files (<1MB) using sync approach
     * For files <1MB, use simple sync upload to avoid executor complexity
     */
    public CompletableFuture<FileUploadResponseDto> uploadFileAsync(
            InputStream inputStream, String fileName, String contentType,
            Long fileSize, Long taskId) {

        log.info("📤 Starting async upload for file: {} ({}MB)",
                fileName, fileSize / 1024.0 / 1024.0);

        String currentUserEmail = getCurrentUserEmail();
        String fileKey = generateFileKey(currentUserEmail, fileName, taskId);

        try {
            // ✅ FIX: For small files, use bytes instead of InputStream to avoid executor issues
            byte[] fileBytes = inputStream.readAllBytes();

            // Create upload request with proper AsyncRequestBody using bytes
            UploadRequest uploadRequest = UploadRequest.builder()
                    .putObjectRequest(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(fileKey)
                            .contentType(contentType)
                            .contentLength(fileSize)
                            .metadata(buildMetadataMap(fileName, taskId, currentUserEmail))
                            .build())
                    // ✅ FIX: Use bytes instead of InputStream to avoid executor null error
                    .requestBody(AsyncRequestBody.fromBytes(fileBytes))
                    .build();

            // Start upload with TransferManager
            Upload upload = transferManager.upload(uploadRequest);

            // Return CompletableFuture for async handling
            return upload.completionFuture().thenApply(completedUpload -> {
                log.info("✅ Upload completed for file: {} -> key: {}", fileName, fileKey);

                String downloadUrl = generatePresignedDownloadUrl(fileKey, Duration.ofDays(7));

                return FileUploadResponseDto.builder()
                        .fileName(fileName)
                        .fileKey(fileKey)
                        .downloadUrl(downloadUrl)
                        .fileSize(fileSize) // ✅ FIX: Use correct field name
                        .contentType(contentType)
                        .uploadedAt(LocalDateTime.now())
                        .taskId(taskId)
                        .uploadStatus("SUCCESS")
                        .message("File uploaded successfully")
                        .build();
            }).exceptionally(throwable -> {
                log.error("❌ Upload failed for file: {}", fileName, throwable);
                // ✅ FIX: Return proper DTO instead of throwing exception
                return FileUploadResponseDto.builder()
                        .fileName(fileName)
                        .fileKey(fileKey)
                        .fileSize(fileSize)
                        .contentType(contentType)
                        .taskId(taskId)
                        .uploadStatus("FAILED")
                        .message("Upload failed: " + throwable.getMessage())
                        .uploadedAt(LocalDateTime.now())
                        .build();
            });

        } catch (IOException e) {
            log.error("❌ Failed to read file bytes for: {}", fileName, e);
            return CompletableFuture.completedFuture(
                FileUploadResponseDto.builder()
                        .fileName(fileName)
                        .fileSize(fileSize)
                        .contentType(contentType)
                        .taskId(taskId)
                        .uploadStatus("FAILED")
                        .message("Failed to read file: " + e.getMessage())
                        .uploadedAt(LocalDateTime.now())
                        .build()
            );
        } catch (Exception e) {
            log.error("❌ Failed to start upload for file: {}", fileName, e);
            return CompletableFuture.completedFuture(
                FileUploadResponseDto.builder()
                        .fileName(fileName)
                        .fileSize(fileSize)
                        .contentType(contentType)
                        .taskId(taskId)
                        .uploadStatus("FAILED")
                        .message("Failed to start upload: " + e.getMessage())
                        .uploadedAt(LocalDateTime.now())
                        .build()
            );
        }
    }

    /**
     * Generate presigned download URL for accessing uploaded files
     */
    public String generatePresignedDownloadUrl(String fileKey, Duration expiration) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("❌ Failed to generate download URL for key: {}", fileKey, e);
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    /**
     * Delete file from S3
     */
    public boolean deleteFile(String fileKey) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("🗑️ File deleted successfully: {}", fileKey);
            return true;
        } catch (Exception e) {
            log.error("❌ Failed to delete file: {}", fileKey, e);
            return false;
        }
    }

    /**
     * Check if file exists in S3
     */
    public boolean fileExists(String fileKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("❌ Error checking file existence: {}", fileKey, e);
            return false;
        }
    }

    /**
     * Get file metadata
     */
    public HeadObjectResponse getFileMetadata(String fileKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            return s3Client.headObject(headRequest);
        } catch (Exception e) {
            log.error("❌ Failed to get file metadata: {}", fileKey, e);
            throw new RuntimeException("Failed to get file metadata", e);
        }
    }

    /**
     * Add metadata to uploaded file after successful upload
     * This is called after presigned URL upload to add metadata that couldn't be included in the signature
     */
    public boolean addMetadataToUploadedFile(String fileKey, String originalFileName, Long taskId, String uploadedBy) {
        try {
            // First check if file exists
            if (!fileExists(fileKey)) {
                log.error("❌ File not found for metadata addition: {}", fileKey);
                return false;
            }

            // Get current object to preserve existing data
            HeadObjectResponse currentMetadata = getFileMetadata(fileKey);

            // Copy object with new metadata
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(fileKey)
                    .destinationBucket(bucketName)
                    .destinationKey(fileKey)
                    .metadata(java.util.Map.of(
                        "original-filename", encodeSafeMetadataValue(originalFileName),
                        "task-id", taskId.toString(),
                        "uploaded-by", encodeSafeMetadataValue(uploadedBy),
                        "upload-timestamp", LocalDateTime.now().toString(),
                        "file-size", currentMetadata.contentLength().toString()
                    ))
                    .metadataDirective(MetadataDirective.REPLACE)
                    .build();

            s3Client.copyObject(copyRequest);
            log.info("✅ Added metadata to uploaded file: {}", fileKey);

            // ✅ NEW: Log file upload activity to task
            logFileUploadActivity(taskId, originalFileName);

            return true;

        } catch (Exception e) {
            log.error("❌ Failed to add metadata to file: {}", fileKey, e);
            return false;
        }
    }

    /**
     * ✅ NEW: Log file upload activity to task activities
     */
    private void logFileUploadActivity(Long taskId, String fileName) {
        try {
            Task task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

            taskActivityService.logFileUploaded(task, fileName);
            log.info("✅ Logged file upload activity for task: {} file: {}", taskId, fileName);
        } catch (Exception e) {
            log.error("❌ Failed to log file upload activity: {}", e.getMessage());
        }
    }

    /**
     * ✅ NEW: Log file deletion activity to task activities
     */
    public void logFileDeletionActivity(Long taskId, String fileName) {
        try {
            Task task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

            taskActivityService.logFileDeleted(task, fileName);
            log.info("✅ Logged file deletion activity for task: {} file: {}", taskId, fileName);
        } catch (Exception e) {
            log.error("❌ Failed to log file deletion activity: {}", e.getMessage());
        }
    }

    // Private helper methods

    private void validateUploadRequest(PresignedUploadUrlRequestDto request) {
        // Validate file size
        if (request.getFileSize() > maxFileSize) {
            throw new IllegalArgumentException(
                String.format("File size (%d bytes) exceeds maximum allowed size (%d bytes)",
                            request.getFileSize(), maxFileSize));
        }

        // ✅ FIX: Normalize content type before validation to match frontend
        String normalizedContentType = normalizeContentType(request.getContentType());

        // Validate content type using normalized value
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new IllegalArgumentException("Content type not allowed: " + normalizedContentType +
                " (original: " + request.getContentType() + ")");
        }

        // Validate file extension
        String fileName = request.getFileName().toLowerCase();
        List<String> allowedExts = Arrays.asList(allowedExtensions.split(","));
        boolean hasValidExtension = allowedExts.stream()
                .anyMatch(ext -> fileName.endsWith(ext.trim().toLowerCase()));

        if (!hasValidExtension) {
            throw new IllegalArgumentException("File extension not allowed. Allowed: " + allowedExtensions);
        }
    }

    private String generateFileKey(String userEmail, PresignedUploadUrlRequestDto request) {
        return generateFileKey(userEmail, request.getFileName(), request.getTaskId(), request.getFolder());
    }

    private String generateFileKey(String userEmail, String fileName, Long taskId) {
        return generateFileKey(userEmail, fileName, taskId, null);
    }

    private String generateFileKey(String userEmail, String fileName, Long taskId, String folder) {
        // Sanitize user email for file path
        String sanitizedEmail = userEmail.replaceAll("[^a-zA-Z0-9.-]", "_");

        // Generate unique identifier
        String uniqueId = UUID.randomUUID().toString();

        // 🔧 FIX: Properly encode filename instead of aggressive sanitization
        String encodedFileName = encodeFilenameForS3(fileName);

        // Build key path with proper handling for null taskId
        StringBuilder keyBuilder = new StringBuilder()
                .append(keyPrefix)
                .append(sanitizedEmail).append("/");

        // ✅ FIX: Handle null taskId for posts
        if (taskId != null) {
            keyBuilder.append("task_").append(taskId).append("/");
        } else {
            // For posts or other non-task files
            keyBuilder.append("posts/");
        }

        if (folder != null && !folder.trim().isEmpty()) {
            keyBuilder.append(folder.trim()).append("/");
        }

        keyBuilder.append(uniqueId).append("_").append(encodedFileName);

        return keyBuilder.toString();
    }

    /**
     * 🔧 IMPROVED METHOD: Robust filename encoding for S3 compatibility
     * - Handles Korean, Vietnamese, and all Unicode characters safely
     * - Uses transliteration for non-ASCII characters to avoid signature issues
     * - Prevents double file extensions (e.g., .docx.docx)
     * - Maintains original filename in S3 metadata for reference
     * - ✅ NEW: Limits filename length to prevent database truncation
     */
    private String encodeFilenameForS3(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "unnamed_file";
        }

        // ✅ FIX: Prevent double extensions - extract extension first
        String nameWithoutExt = fileName;
        String fileExtension = "";

        if (fileName.contains(".")) {
            int lastDotIndex = fileName.lastIndexOf(".");
            nameWithoutExt = fileName.substring(0, lastDotIndex);
            fileExtension = fileName.substring(lastDotIndex).toLowerCase();

            // Validate extension format
            if (!fileExtension.matches("\\.[a-zA-Z0-9]{1,5}")) {
                fileExtension = ""; // Invalid extension, remove it
            }
        }

        // ✅ NEW: Limit filename length to prevent URL truncation
        // With UUID (36 chars) + underscores + path structure, keep filename part short
        int maxNameLength = 30; // Reasonable limit to prevent database truncation
        if (nameWithoutExt.length() > maxNameLength) {
            nameWithoutExt = nameWithoutExt.substring(0, maxNameLength);
        }

        // First, normalize the filename (without extension)
        String normalized = nameWithoutExt
                // Replace multiple spaces with single space
                .replaceAll("\\s+", " ")
                // Replace Windows/path-unsafe characters with underscores
                .replaceAll("[<>:\"/\\\\|?*]", "_")
                // Remove control characters
                .replaceAll("[\\x00-\\x1F\\x7F]", "");

        // ✅ IMPROVED: Use safe ASCII transliteration for S3 compatibility
        StringBuilder safeFileName = new StringBuilder();

        for (char c : normalized.toCharArray()) {
            if (c >= 32 && c <= 126) {
                // ASCII printable characters (space to tilde) - safe to use
                if (c == ' ') {
                    safeFileName.append('_');
                } else if (c == '.' || c == '-' || c == '_' || Character.isLetterOrDigit(c)) {
                    safeFileName.append(c);
                } else {
                    safeFileName.append('_');
                }
            } else {
                // Non-ASCII characters (Korean, Vietnamese, etc.) - replace with safe pattern
                safeFileName.append('_');
            }
        }

        // Clean up multiple underscores
        String result = safeFileName.toString()
                .replaceAll("_{2,}", "_")  // Replace multiple underscores with single
                .replaceAll("^_+", "")     // Remove leading underscores
                .replaceAll("_+$", "");    // Remove trailing underscores

        // Ensure we have a valid filename
        if (result.isEmpty()) {
            result = "file";
        }

        // ✅ FIX: Add back the single, validated extension (no double extensions)
        String finalFileName = result + fileExtension;

        log.debug("🔧 Filename encoding: '{}' -> '{}' (name: '{}' + ext: '{}', length: {})",
                fileName, finalFileName, result, fileExtension, finalFileName.length());
        return finalFileName;
    }

    /**
     * ✅ FIXED: Encode metadata values to ensure safe storage in S3
     * - Properly handle @ characters in email addresses
     * - Replace all problematic characters with safe alternatives
     * - Ensure S3 metadata compliance [0-9a-zA-Z !-_.*'()]
     */
    private String encodeSafeMetadataValue(String value) {
        if (value == null) {
            return null;
        }

        // ✅ FIX: Properly encode @ and other special characters for S3 metadata
        String encoded = value
                // Handle common email characters first
                .replace("@", "_at_")           // cuongvanle1011@gmail.com -> cuongvanle1011_at_gmail.com
                .replace("+", "_plus_")         // Handle + in email addresses
                .replace("=", "_eq_")           // Handle = characters
                .replace(",", "_comma_")        // Handle commas
                .replace(";", "_semicolon_")    // Handle semicolons
                .replace(":", "_colon_")        // Handle colons
                .replace("/", "_slash_")        // Handle forward slashes
                .replace("\\", "_backslash_")   // Handle backslashes
                .replace("&", "_and_")          // Handle ampersands
                .replace("#", "_hash_")         // Handle hash symbols
                .replace("%", "_percent_")      // Handle percent symbols
                .replace("?", "_question_")     // Handle question marks
                .replace("<", "_lt_")           // Handle less than
                .replace(">", "_gt_")           // Handle greater than
                .replace("[", "_lbracket_")     // Handle left bracket
                .replace("]", "_rbracket_")     // Handle right bracket
                .replace("{", "_lcurly_")       // Handle left curly brace
                .replace("}", "_rcurly_")       // Handle right curly brace
                .replace("|", "_pipe_")         // Handle pipe character
                .replace("\"", "_quote_")       // Handle double quotes
                .replace("'", "_squote_")       // Handle single quotes (though allowed, safer to encode)
                .replace(" ", "_");             // Replace spaces with underscores

        // ✅ Final safety check: Remove any remaining non-S3-safe characters
        // S3 allows: [0-9a-zA-Z !-_.*'()]
        encoded = encoded.replaceAll("[^a-zA-Z0-9._\\-!*'()]", "_");

        // Clean up multiple consecutive underscores
        encoded = encoded.replaceAll("_{2,}", "_");

        // Remove leading/trailing underscores
        encoded = encoded.replaceAll("^_+|_+$", "");

        // Trim to 256 characters (S3 metadata value limit)
        if (encoded.length() > 256) {
            encoded = encoded.substring(0, 256);
            // Remove trailing underscore if truncation created one
            encoded = encoded.replaceAll("_+$", "");
        }

        // Ensure we don't return empty string
        if (encoded.isEmpty()) {
            encoded = "encoded_value";
        }

        log.debug("🔧 Metadata encoding: '{}' -> '{}'", value, encoded);
        return encoded;
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        throw new SecurityException("User not authenticated");
    }

    /**
     * ✅ FIXED: Normalize content type to prevent charset mismatch issues
     * - Removes charset parameters that cause 403 errors
     * - Handles content-type variations and extensions
     * - Ensures consistent content-type between presigned URL and frontend upload
     */
    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return "application/octet-stream";
        }

        // ✅ FIX: Remove charset and other parameters to prevent mismatch
        // Example: "application/vnd.openxmlformats-officedocument.wordprocessingml.document;charset=UTF-8"
        // becomes: "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        String normalized = contentType.toLowerCase().trim();

        // Split by semicolon and take only the main content type part
        if (normalized.contains(";")) {
            normalized = normalized.split(";")[0].trim();
        }

        // Handle file extension-based content types
        if (normalized.equals("jpeg") || normalized.equals("jpg")) {
            return "image/jpeg";
        } else if (normalized.equals("png")) {
            return "image/png";
        } else if (normalized.equals("gif")) {
            return "image/gif";
        } else if (normalized.equals("webp")) {
            return "image/webp";
        } else if (normalized.equals("pdf")) {
            return "application/pdf";
        } else if (normalized.equals("txt") || normalized.equals("text")) {
            return "text/plain";
        } else if (normalized.equals("doc")) {
            return "application/msword";
        } else if (normalized.equals("docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (normalized.equals("xls")) {
            return "application/vnd.ms-excel";
        } else if (normalized.equals("xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (normalized.equals("mp4")) {
            return "video/mp4";
        } else if (normalized.equals("avi")) {
            return "video/avi";
        } else if (normalized.equals("mov") || normalized.equals("qt")) {
            return "video/quicktime";
        }

        // Handle full MIME types - ensure they're in our allowed list
        switch (normalized) {
            case "image/jpeg":
            case "image/jpg":
                return "image/jpeg";
            case "image/png":
                return "image/png";
            case "image/gif":
                return "image/gif";
            case "image/webp":
                return "image/webp";
            case "application/pdf":
                return "application/pdf";
            case "text/plain":
                return "text/plain";
            case "application/msword":
                return "application/msword";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "application/vnd.ms-excel":
                return "application/vnd.ms-excel";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "video/mp4":
                return "video/mp4";
            case "video/avi":
                return "video/avi";
            case "video/quicktime":
                return "video/quicktime";
            default:
                // For any unrecognized content type, use generic binary
                log.warn("⚠️ Unknown content type '{}', using application/octet-stream", contentType);
                return "application/octet-stream";
        }
    }

    private java.util.Map<String, String> buildMetadataMap(String fileName, Long taskId, String currentUserEmail) {
        java.util.Map<String, String> metadata = new java.util.HashMap<>();
        metadata.put("original-filename", encodeSafeMetadataValue(fileName));
        metadata.put("uploaded-by", encodeSafeMetadataValue(currentUserEmail));
        metadata.put("upload-timestamp", LocalDateTime.now().toString());

        if (taskId != null) {
            metadata.put("task-id", taskId.toString());
        }

        return metadata;
    }
}
