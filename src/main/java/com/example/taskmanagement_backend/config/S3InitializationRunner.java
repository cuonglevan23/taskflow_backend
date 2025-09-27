package com.example.taskmanagement_backend.config;

import com.example.taskmanagement_backend.services.S3ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 🚀 S3 Initialization Component
 * Validates S3 configuration on application startup
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3InitializationRunner implements CommandLineRunner {

    private final S3ValidationService s3ValidationService;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.access-key-id}")
    private String accessKeyId;

    @Value("${aws.region}")
    private String region;

    @Override
    public void run(String... args) {
        log.info("🔍 Initializing S3 configuration validation...");
        log.info("📋 AWS Credentials: Access Key ID = {}", accessKeyId.substring(0, 10) + "...");
        log.info("📋 S3 Bucket: {}", bucketName);
        log.info("📋 AWS Region: {}", region);

        try {
            // First, try to create bucket if it doesn't exist (for development)
            s3ValidationService.createBucketIfNotExists();

            // Then validate all S3 operations
            s3ValidationService.validateS3Configuration();

            log.info("🎉 S3 configuration is valid and ready for file uploads!");

        } catch (Exception e) {
            log.error("💥 S3 configuration validation failed!");
            log.error("❌ Error: {}", e.getMessage());
            log.error("🔧 Please check:");
            log.error("   1. AWS credentials are correct");
            log.error("   2. S3 bucket '{}' exists", bucketName);
            log.error("   3. AWS region '{}' is correct", region);
            log.error("   4. IAM user has S3 permissions: s3:GetObject, s3:PutObject, s3:DeleteObject");

            // Don't fail application startup, but log the issue
            log.warn("⚠️ Application will continue but file uploads may fail");
        }
    }
}
