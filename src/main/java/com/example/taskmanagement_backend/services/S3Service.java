package com.example.taskmanagement_backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * 📁 AWS S3 Service
 * Service để xử lý upload/download files từ S3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.key-prefix:task-files/}")
    private String keyPrefix;

    @Value("${aws.s3.presigned-url-expiration:3600}")
    private long presignedUrlExpiration;

    /**
     * 🚀 Upload file to S3
     */
    public String uploadFile(String fileName, InputStream fileStream, long fileSize, String contentType) {
        try {
            String key = keyPrefix + UUID.randomUUID() + "_" + fileName;

            log.info("🚀 Uploading file to S3: {} (size: {} bytes)", key, fileSize);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(fileSize)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(fileStream, fileSize));

            log.info("✅ File uploaded successfully: {}", key);
            return key;

        } catch (Exception e) {
            log.error("❌ Error uploading file to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    /**
     * 📥 Generate presigned URL for download
     */
    public String generateDownloadUrl(String s3Key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(presignedUrlExpiration))
                    .getObjectRequest(getObjectRequest)
                    .build();

            String presignedUrl = s3Presigner.presignGetObject(presignRequest).url().toString();

            log.debug("📥 Generated download URL for: {}", s3Key);
            return presignedUrl;

        } catch (Exception e) {
            log.error("❌ Error generating download URL for: {}", s3Key, e);
            throw new RuntimeException("Failed to generate download URL: " + e.getMessage(), e);
        }
    }

    /**
     * 📤 Generate presigned URL for upload
     */
    public String generateUploadUrl(String fileName, String contentType) {
        try {
            String key = keyPrefix + UUID.randomUUID() + "_" + fileName;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(presignedUrlExpiration))
                    .putObjectRequest(putObjectRequest)
                    .build();

            String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

            log.info("📤 Generated upload URL for: {}", key);
            return presignedUrl;

        } catch (Exception e) {
            log.error("❌ Error generating upload URL for: {}", fileName, e);
            throw new RuntimeException("Failed to generate upload URL: " + e.getMessage(), e);
        }
    }

    /**
     * 🗑️ Delete file from S3
     */
    public void deleteFile(String s3Key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);

            log.info("🗑️ File deleted from S3: {}", s3Key);

        } catch (Exception e) {
            log.error("❌ Error deleting file from S3: {}", s3Key, e);
            throw new RuntimeException("Failed to delete file from S3: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ Check if file exists in S3
     */
    public boolean fileExists(String s3Key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("❌ Error checking file existence: {}", s3Key, e);
            return false;
        }
    }

    /**
     * 🖼️ Get safe avatar URL (handles both S3 keys and existing URLs)
     * This utility method ensures we never return raw S3 keys to the frontend
     */
    public String getSafeAvatarUrl(String avatarPath) {
        if (avatarPath == null) {
            return null;
        }

        // If it's already a fully qualified URL, return it as is
        if (avatarPath.startsWith("http://") || avatarPath.startsWith("https://")) {
            return avatarPath;
        }

        // If it's an S3 key, generate a presigned URL
        if (avatarPath.startsWith("task-files/") || avatarPath.startsWith("avatars/")) {
            try {
                return generateDownloadUrl(avatarPath);
            } catch (Exception e) {
                log.warn("❌ Could not generate presigned URL for avatar: {}", avatarPath, e);
                // Return null if we can't generate a URL to avoid frontend errors
                return null;
            }
        }

        // For other paths, return as is (could be local paths or legacy URLs)
        return avatarPath;
    }

    /**
     * 🖼️ Get direct public URL for avatar (không sử dụng presigned URL)
     * Phương thức này trả về URL trực tiếp đến file trong S3, không có presigned params
     * Lưu ý: Yêu cầu S3 bucket được cấu hình cho phép public read access
     */
    public String getDirectAvatarUrl(String avatarPath) {
        if (avatarPath == null) {
            return null;
        }

        // If it's already a fully qualified URL, return it as is
        if (avatarPath.startsWith("http://") || avatarPath.startsWith("https://")) {
            return avatarPath;
        }

        // If it's an S3 key, generate a direct URL
        if (avatarPath.startsWith("task-files/") || avatarPath.startsWith("avatars/")) {
            return "https://" + bucketName + ".s3.amazonaws.com/" + avatarPath;
        }

        // For other paths, return as is
        return avatarPath;
    }
}
