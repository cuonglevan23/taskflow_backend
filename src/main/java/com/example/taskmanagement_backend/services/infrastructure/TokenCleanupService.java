package com.example.taskmanagement_backend.services.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupService {

    private final JwtTokenService jwtTokenService;

    /**
     * Clean up expired and revoked tokens every hour
     * Runs at minute 0 of every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting scheduled cleanup of expired refresh tokens");
        
        try {
            jwtTokenService.cleanupExpiredTokens();
            log.info("Successfully completed token cleanup");
        } catch (Exception e) {
            log.error("Error during token cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up old tokens daily at 2 AM
     * Remove tokens older than 30 days regardless of status
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldTokens() {
        log.info("Starting scheduled cleanup of old refresh tokens (30+ days)");
        
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
            jwtTokenService.cleanupOldTokens(cutoffDate);
            log.info("Successfully completed old token cleanup");
        } catch (Exception e) {
            log.error("Error during old token cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Limit tokens per user - run daily at 3 AM
     * Keep only the 5 most recent tokens per user
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void limitTokensPerUser() {
        log.info("Starting token limitation per user (max 5 tokens)");
        
        try {
            jwtTokenService.limitTokensPerUser(5);
            log.info("Successfully completed token limitation");
        } catch (Exception e) {
            log.error("Error during token limitation: {}", e.getMessage(), e);
        }
    }
}