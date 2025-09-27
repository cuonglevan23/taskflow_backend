package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.services.infrastructure.JwtTokenService;
import com.example.taskmanagement_backend.services.infrastructure.TokenCleanupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/tokens")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Token Management", description = "Admin endpoints for token management and monitoring")
public class TokenManagementController {

    private final JwtTokenService jwtTokenService;
    private final TokenCleanupService tokenCleanupService;

    @GetMapping("/stats")
    @Operation(summary = "Get token statistics", description = "Get comprehensive token statistics for monitoring")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<JwtTokenService.TokenStats> getTokenStats() {
        log.info("Admin requested token statistics");
        JwtTokenService.TokenStats stats = jwtTokenService.getTokenStats();
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/cleanup/expired")
    @Operation(summary = "Manual cleanup of expired tokens", description = "Manually trigger cleanup of expired and revoked tokens")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> cleanupExpiredTokens() {
        log.info("Admin triggered manual cleanup of expired tokens");
        
        try {
            jwtTokenService.cleanupExpiredTokens();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Expired tokens cleanup completed successfully"
            ));
        } catch (Exception e) {
            log.error("Error during manual token cleanup: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to cleanup tokens: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/cleanup/old")
    @Operation(summary = "Manual cleanup of old tokens", description = "Manually trigger cleanup of tokens older than 30 days")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> cleanupOldTokens() {
        log.info("Admin triggered manual cleanup of old tokens");
        
        try {
            tokenCleanupService.cleanupOldTokens();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Old tokens cleanup completed successfully"
            ));
        } catch (Exception e) {
            log.error("Error during manual old token cleanup: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to cleanup old tokens: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/limit-per-user")
    @Operation(summary = "Limit tokens per user", description = "Manually trigger token limitation per user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> limitTokensPerUser(@RequestParam(defaultValue = "5") int maxTokens) {
        log.info("Admin triggered token limitation per user: max {} tokens", maxTokens);
        
        try {
            tokenCleanupService.limitTokensPerUser();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Token limitation completed successfully",
                "maxTokensPerUser", String.valueOf(maxTokens)
            ));
        } catch (Exception e) {
            log.error("Error during token limitation: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to limit tokens: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Token system health check", description = "Check the health of token management system")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getTokenSystemHealth() {
        log.info("Admin requested token system health check");
        
        try {
            JwtTokenService.TokenStats stats = jwtTokenService.getTokenStats();
            
            // Calculate health metrics
            double activeRatio = stats.getTotalTokens() > 0 ? 
                (double) stats.getActiveTokens() / stats.getTotalTokens() : 0;
            
            String healthStatus = "healthy";
            if (stats.getTotalTokens() > 100000) {
                healthStatus = "warning"; // Too many tokens
            }
            if (activeRatio < 0.1 && stats.getTotalTokens() > 1000) {
                healthStatus = "critical"; // Too many inactive tokens
            }
            
            return ResponseEntity.ok(Map.of(
                "status", healthStatus,
                "totalTokens", stats.getTotalTokens(),
                "activeTokens", stats.getActiveTokens(),
                "activeRatio", String.format("%.2f%%", activeRatio * 100),
                "recommendations", getHealthRecommendations(stats, activeRatio)
            ));
            
        } catch (Exception e) {
            log.error("Error during health check: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to check system health: " + e.getMessage()
            ));
        }
    }

    private String getHealthRecommendations(JwtTokenService.TokenStats stats, double activeRatio) {
        if (stats.getTotalTokens() > 100000) {
            return "Consider running cleanup operations more frequently";
        }
        if (activeRatio < 0.1 && stats.getTotalTokens() > 1000) {
            return "High number of inactive tokens detected, run cleanup immediately";
        }
        if (stats.getExpiredTokens() > stats.getActiveTokens()) {
            return "More expired tokens than active ones, cleanup recommended";
        }
        return "Token system is healthy";
    }
}