package com.example.taskmanagement_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration để enable Spring Scheduling
 * Cần thiết cho AutoNotificationService để chạy các scheduled tasks
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Spring sẽ tự động enable scheduling cho các method có @Scheduled annotation
}
