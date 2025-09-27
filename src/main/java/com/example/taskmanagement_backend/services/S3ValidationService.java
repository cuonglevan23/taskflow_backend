package com.example.taskmanagement_backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.LocalDateTime;

/**
 * 🔧 S3 Validation Service
 * Service để test và validate S3 connection và permissions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3ValidationService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    /**
     * 🧪 Test S3 connection and permissions
     */
    public void validateS3Configuration() {
        log.info("🧪 Starting S3 configuration validation...");
        log.info("📋 Bucket: {}, Region: {}", bucketName, region);

        try {
            // Test 1: Check if bucket exists and is accessible
            testBucketAccess();

            // Test 2: Check bucket location
            testBucketLocation();

            // Test 3: Test upload permissions
            testUploadPermissions();

            // Test 4: Test list permissions
            testListPermissions();

            log.info("✅ S3 configuration validation completed successfully!");

        } catch (Exception e) {
            log.error("❌ S3 configuration validation failed: {}", e.getMessage(), e);
        }
    }

    private void testBucketAccess() {
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            s3Client.headBucket(headBucketRequest);
            log.info("✅ Bucket access test passed - bucket exists and is accessible");

        } catch (NoSuchBucketException e) {
            log.error("❌ Bucket '{}' does not exist", bucketName);
            throw new RuntimeException("S3 bucket does not exist: " + bucketName);
        } catch (S3Exception e) {
            log.error("❌ S3 access error: {} (Status: {})", e.getMessage(), e.statusCode());
            throw new RuntimeException("S3 access denied: " + e.getMessage());
        }
    }

    private void testBucketLocation() {
        try {
            GetBucketLocationRequest locationRequest = GetBucketLocationRequest.builder()
                    .bucket(bucketName)
                    .build();

            GetBucketLocationResponse locationResponse = s3Client.getBucketLocation(locationRequest);
            String bucketRegion = locationResponse.locationConstraintAsString();

            // Handle null/empty region (defaults to us-east-1)
            if (bucketRegion == null || bucketRegion.isEmpty()) {
                bucketRegion = "us-east-1";
            }

            log.info("📍 Bucket region: {}, Configured region: {}", bucketRegion, region);

            if (!bucketRegion.equals(region)) {
                log.warn("⚠️ Region mismatch! Bucket is in '{}' but configured for '{}'", bucketRegion, region);
            } else {
                log.info("✅ Region configuration is correct");
            }

        } catch (Exception e) {
            log.error("❌ Failed to get bucket location: {}", e.getMessage());
        }
    }

    private void testUploadPermissions() {
        try {
            String testKey = "test-upload-" + System.currentTimeMillis() + ".txt";
            String testContent = "Test upload at " + LocalDateTime.now();

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(testKey)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromString(testContent));
            log.info("✅ Upload permissions test passed - test file uploaded successfully");

            // Clean up test file
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(testKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("🧹 Test file cleaned up");

        } catch (Exception e) {
            log.error("❌ Upload permissions test failed: {}", e.getMessage());
            throw new RuntimeException("No S3 upload permissions: " + e.getMessage());
        }
    }

    private void testListPermissions() {
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(1)
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            log.info("✅ List permissions test passed - found {} objects", listResponse.keyCount());

        } catch (Exception e) {
            log.warn("⚠️ List permissions test failed: {}", e.getMessage());
            // List permission is not critical for file uploads
        }
    }

    /**
     * 🛠️ Create bucket if it doesn't exist (for development)
     */
    public void createBucketIfNotExists() {
        try {
            log.info("🛠️ Checking if bucket '{}' exists...", bucketName);

            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            s3Client.headBucket(headBucketRequest);
            log.info("✅ Bucket '{}' already exists", bucketName);

        } catch (NoSuchBucketException e) {
            log.info("🆕 Bucket '{}' doesn't exist. Creating...", bucketName);

            try {
                CreateBucketRequest.Builder createRequestBuilder = CreateBucketRequest.builder()
                        .bucket(bucketName);

                // Only set CreateBucketConfiguration for regions other than us-east-1
                if (!"us-east-1".equals(region)) {
                    CreateBucketConfiguration bucketConfiguration = CreateBucketConfiguration.builder()
                            .locationConstraint(region)
                            .build();
                    createRequestBuilder.createBucketConfiguration(bucketConfiguration);
                }

                s3Client.createBucket(createRequestBuilder.build());
                log.info("✅ Bucket '{}' created successfully in region '{}'", bucketName, region);

            } catch (Exception createException) {
                log.error("❌ Failed to create bucket '{}': {}", bucketName, createException.getMessage());
                throw new RuntimeException("Failed to create S3 bucket: " + createException.getMessage());
            }

        } catch (Exception e) {
            log.error("❌ Error checking bucket existence: {}", e.getMessage());
            throw new RuntimeException("Error validating S3 bucket: " + e.getMessage());
        }
    }
}
