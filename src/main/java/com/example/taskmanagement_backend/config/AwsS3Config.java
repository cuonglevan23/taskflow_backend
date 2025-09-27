package com.example.taskmanagement_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.time.Duration;

/**
 * AWS S3 Configuration following 2025 best practices
 * - Uses AWS SDK v2 with async clients for better performance
 * - Implements proper credentials management with validation
 * - Configures TransferManager for multipart uploads
 * - Uses connection pooling for scalability
 * - Handles missing credentials gracefully
 */
@Slf4j
@Configuration
public class AwsS3Config {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    /**
     * 2025 Best Practice: Use DefaultCredentialsProvider first, fallback to explicit credentials
     * Order of credential resolution:
     * 1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
     * 2. Java system properties
     * 3. IAM roles (for EC2/ECS/Lambda)
     * 4. Explicit credentials (last resort)
     */
    @Bean
    public AwsCredentials awsCredentials() {
        // Check if explicit credentials are provided and not empty
        if (accessKeyId != null && !accessKeyId.trim().isEmpty() &&
            secretAccessKey != null && !secretAccessKey.trim().isEmpty()) {
            log.info("✅ Using explicit AWS credentials for S3 access");
            return AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim());
        }

        log.warn("���️ No explicit AWS credentials found. Will attempt to use DefaultCredentialsProvider");
        log.info("💡 Please set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables or configure IAM roles");

        // Return null to use DefaultCredentialsProvider
        return null;
    }

    /**
     * Validate AWS credentials are available
     */
    private boolean hasValidCredentials() {
        return (accessKeyId != null && !accessKeyId.trim().isEmpty() &&
                secretAccessKey != null && !secretAccessKey.trim().isEmpty());
    }

    /**
     * 2025 Best Practice: S3 Sync Client for simple operations
     * - Use for presigned URLs and metadata operations
     * - Connection pooling with optimized settings
     */
    @Bean
    public S3Client s3Client() {
        try {
            var builder = S3Client.builder()
                    .region(Region.of(region));

            AwsCredentials credentials = awsCredentials();
            if (credentials != null) {
                builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
                log.info("✅ S3Client configured with static credentials");
            } else {
                builder.credentialsProvider(DefaultCredentialsProvider.create());
                log.info("✅ S3Client configured with DefaultCredentialsProvider");
            }

            return builder.build();
        } catch (Exception e) {
            log.error("❌ Failed to create S3Client: {}", e.getMessage());
            throw new RuntimeException("Failed to configure AWS S3 client. Please check your AWS credentials.", e);
        }
    }

    /**
     * 2025 Best Practice: S3 Async Client for high-throughput operations
     * - Optimized for concurrent uploads/downloads
     * - Uses Netty NIO for better performance
     * - Custom connection pool settings
     */
    @Bean
    public S3AsyncClient s3AsyncClient() {
        try {
            var httpClient = NettyNioAsyncHttpClient.builder()
                    .maxConcurrency(100)                    // Handle 100 concurrent requests
                    .connectionTimeout(Duration.ofSeconds(30))
                    .connectionAcquisitionTimeout(Duration.ofSeconds(60))
                    .connectionTimeToLive(Duration.ofMinutes(5))
                    .build();

            var builder = S3AsyncClient.builder()
                    .region(Region.of(region))
                    .httpClient(httpClient);

            AwsCredentials credentials = awsCredentials();
            if (credentials != null) {
                builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
                log.info("✅ S3AsyncClient configured with static credentials");
            } else {
                builder.credentialsProvider(DefaultCredentialsProvider.create());
                log.info("✅ S3AsyncClient configured with DefaultCredentialsProvider");
            }

            return builder.build();
        } catch (Exception e) {
            log.error("❌ Failed to create S3AsyncClient: {}", e.getMessage());
            throw new RuntimeException("Failed to configure AWS S3 async client. Please check your AWS credentials.", e);
        }
    }

    /**
     * 2025 Best Practice: S3 Presigner for generating presigned URLs
     * - Secure URL generation without exposing credentials
     * - Frontend can upload directly to S3
     */
    @Bean
    public S3Presigner s3Presigner() {
        try {
            var builder = S3Presigner.builder()
                    .region(Region.of(region));

            AwsCredentials credentials = awsCredentials();
            if (credentials != null) {
                builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
                log.info("✅ S3Presigner configured with static credentials");
            } else {
                builder.credentialsProvider(DefaultCredentialsProvider.create());
                log.info("✅ S3Presigner configured with DefaultCredentialsProvider");
            }

            return builder.build();
        } catch (Exception e) {
            log.error("❌ Failed to create S3Presigner: {}", e.getMessage());
            throw new RuntimeException("Failed to configure AWS S3 presigner. Please check your AWS credentials.", e);
        }
    }

    /**
     * 2025 Best Practice: TransferManager for efficient file operations
     * - Automatic multipart uploads for files > 8MB
     * - Parallel chunk uploads for better performance
     * - Built-in retry logic and error handling
     * - Progress tracking support
     */
    @Bean
    public S3TransferManager s3TransferManager(S3AsyncClient s3AsyncClient) {
        try {
            return S3TransferManager.builder()
                    .s3Client(s3AsyncClient)
                    .build();
        } catch (Exception e) {
            log.error("❌ Failed to create S3TransferManager: {}", e.getMessage());
            throw new RuntimeException("Failed to configure AWS S3 transfer manager. Please check your AWS credentials.", e);
        }
    }
}
