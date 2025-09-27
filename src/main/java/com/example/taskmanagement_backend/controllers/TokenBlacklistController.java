package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.services.infrastructure.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Token Blacklist Management Controller
 * 
 * Admin endpoints for managing JWT token blacklist
 * Requires ADMIN role for security
 * 
 * @author Task Management Team
 * @version 1.0
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/token-blacklist")
@Slf4j
@Tag(name = "Token Blacklist Management", description = "Admin endpoints for token blacklist management")
@PreAuthorize("hasRole('ADMIN')")
public class TokenBlacklistController {

    private final TokenBlacklistService tokenBlacklistService;

    @GetMapping("/stats")
    @Operation(summary = "Get blacklist statistics", description = "Get current blacklist size and status")
    public ResponseEntity<Map<String, Object>> getBlacklistStats() {
        log.info("Admin requesting blacklist statistics");
        
        long blacklistSize = tokenBlacklistService.getBlacklistSize();
        boolean serviceAvailable = tokenBlacklistService.isServiceAvailable();
        
        Map<String, Object> stats = Map.of(
            "blacklistSize", blacklistSize,
            "serviceAvailable", serviceAvailable,
            "status", serviceAvailable ? "HEALTHY" : "UNAVAILABLE"
        );
        
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/blacklist")
    @Operation(summary = "Manually blacklist token", description = "Manually add a token to blacklist with custom TTL")
    public ResponseEntity<Map<String, String>> blacklistToken(
            @RequestParam String token,
            @RequestParam(defaultValue = "1440") long ttlMinutes) { // Default 24 hours
        
        log.info("Admin manually blacklisting token with TTL: {} minutes", ttlMinutes);
        
        try {
            tokenBlacklistService.blacklistToken(token, ttlMinutes);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Token blacklisted successfully",
                "ttlMinutes", String.valueOf(ttlMinutes)
            ));
            
        } catch (Exception e) {
            log.error("Failed to blacklist token manually", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to blacklist token"
            ));
        }
    }

    @DeleteMapping("/whitelist")
    @Operation(summary = "Remove token from blacklist", description = "Manually remove a token from blacklist")
    public ResponseEntity<Map<String, String>> whitelistToken(@RequestParam String token) {
        log.info("Admin removing token from blacklist");
        
        try {
            tokenBlacklistService.removeTokenFromBlacklist(token);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Token removed from blacklist successfully"
            ));
            
        } catch (Exception e) {
            log.error("Failed to remove token from blacklist", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to remove token from blacklist"
            ));
        }
    }

    @GetMapping("/check")
    @Operation(summary = "Check if token is blacklisted", description = "Check blacklist status of a specific token")
    public ResponseEntity<Map<String, Object>> checkTokenStatus(@RequestParam String token) {
        log.info("Admin checking token blacklist status");
        
        try {
            boolean isBlacklisted = tokenBlacklistService.isTokenBlacklisted(token);
            
            return ResponseEntity.ok(Map.of(
                "token", token.substring(0, Math.min(20, token.length())) + "...", // Partial token for security
                "isBlacklisted", isBlacklisted,
                "status", isBlacklisted ? "BLACKLISTED" : "VALID"
            ));
            
        } catch (Exception e) {
            log.error("Failed to check token status", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to check token status"
            ));
        }
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Clear all blacklisted tokens", description = "Remove all tokens from blacklist (use with caution)")
    public ResponseEntity<Map<String, String>> clearBlacklist() {
        log.warn("Admin clearing entire token blacklist");
        
        try {
            tokenBlacklistService.clearBlacklist();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "All blacklisted tokens cleared successfully"
            ));
            
        } catch (Exception e) {
            log.error("Failed to clear blacklist", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to clear blacklist"
            ));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Check blacklist service health", description = "Check if Redis blacklist service is available")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        boolean isAvailable = tokenBlacklistService.isServiceAvailable();
        
        return ResponseEntity.ok(Map.of(
            "service", "TokenBlacklistService",
            "status", isAvailable ? "UP" : "DOWN",
            "available", isAvailable
        ));
    }
}