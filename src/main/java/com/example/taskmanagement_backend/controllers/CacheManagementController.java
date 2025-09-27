package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.services.cache.CacheMetricsService;
import com.example.taskmanagement_backend.services.cache.TaskCacheService;
import com.example.taskmanagement_backend.services.cache.CacheWarmupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Professional Cache Management Controller
 * 
 * Features:
 * - Cache health monitoring
 * - Performance metrics
 * - Cache management operations
 * - Admin-only access for sensitive operations
 * 
 * @author Task Management Team
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CacheManagementController {

    private final TaskCacheService taskCacheService;
    private final CacheMetricsService cacheMetricsService;
    private final CacheWarmupService cacheWarmupService;

    /**
     * Get cache health status (Public endpoint for testing)
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getCacheHealth() {
        log.debug("🏥 Checking cache health");
        
        boolean isHealthy = taskCacheService.isCacheAvailable();
        TaskCacheService.CacheStats stats = taskCacheService.getCacheStats();
        
        Map<String, Object> health = Map.of(
                "status", isHealthy ? "UP" : "DOWN",
                "available", isHealthy,
                "totalCacheSize", stats.getTotalCacheSize(),
                "taskCacheSize", stats.getTaskCacheSize(),
                "userTasksCacheSize", stats.getUserTasksCacheSize(),
                "teamTasksCacheSize", stats.getTeamTasksCacheSize(),
                "projectTasksCacheSize", stats.getProjectTasksCacheSize()
        );
        
        return ResponseEntity.ok(health);
    }

    /**
     * Get comprehensive cache metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<CacheMetricsService.CacheMetrics> getCacheMetrics() {
        log.debug("📊 Getting cache metrics");
        
        CacheMetricsService.CacheMetrics metrics = cacheMetricsService.getMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get cache summary statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        log.debug("📈 Getting cache statistics");
        
        Map<String, Object> stats = cacheMetricsService.getSummaryStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get metrics for specific cache type
     */
    @GetMapping("/metrics/{cacheType}")
    public ResponseEntity<CacheMetricsService.CacheTypeMetrics> getCacheTypeMetrics(
            @PathVariable String cacheType) {
        log.debug("📊 Getting metrics for cache type: {}", cacheType);
        
        CacheMetricsService.CacheTypeMetrics metrics = cacheMetricsService.getMetricsForCacheType(cacheType);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Evict specific task from cache
     * Admin only operation
     */
    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> evictTask(@PathVariable Long taskId) {
        log.info("🗑️ Admin evicting task cache: {}", taskId);
        
        try {
            taskCacheService.evictTask(taskId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Task cache evicted successfully",
                    "taskId", taskId.toString()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to evict task cache: {}", taskId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to evict task cache",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Evict user tasks cache
     * Admin only operation
     */
    @DeleteMapping("/users/{userId}/tasks")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> evictUserTasks(@PathVariable Long userId) {
        log.info("🗑️ Admin evicting user tasks cache: {}", userId);
        
        try {
            taskCacheService.evictUserTasks(userId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "User tasks cache evicted successfully",
                    "userId", userId.toString()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to evict user tasks cache: {}", userId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to evict user tasks cache",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Evict team tasks cache
     * Admin only operation
     */
    @DeleteMapping("/teams/{teamId}/tasks")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> evictTeamTasks(@PathVariable Long teamId) {
        log.info("🗑️ Admin evicting team tasks cache: {}", teamId);
        
        try {
            taskCacheService.evictTeamTasks(teamId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Team tasks cache evicted successfully",
                    "teamId", teamId.toString()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to evict team tasks cache: {}", teamId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to evict team tasks cache",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Evict project tasks cache
     * Admin only operation
     */
    @DeleteMapping("/projects/{projectId}/tasks")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> evictProjectTasks(@PathVariable Long projectId) {
        log.info("🗑️ Admin evicting project tasks cache: {}", projectId);
        
        try {
            taskCacheService.evictProjectTasks(projectId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Project tasks cache evicted successfully",
                    "projectId", projectId.toString()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to evict project tasks cache: {}", projectId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to evict project tasks cache",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Evict all task caches
     * Super admin only operation
     */
    @DeleteMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> evictAllCaches() {
        log.warn("🚨 Admin performing bulk cache eviction");
        
        try {
            taskCacheService.evictAllTaskCaches();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "All task caches evicted successfully"
            ));
        } catch (Exception e) {
            log.error("❌ Failed to evict all caches", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to evict all caches",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Reset cache metrics
     * Admin only operation
     */
    @PostMapping("/metrics/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> resetMetrics() {
        log.info("🔄 Admin resetting cache metrics");
        
        try {
            cacheMetricsService.resetMetrics();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Cache metrics reset successfully"
            ));
        } catch (Exception e) {
            log.error("❌ Failed to reset cache metrics", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to reset cache metrics",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get cache configuration info
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getCacheConfig() {
        log.debug("⚙️ Getting cache configuration");
        
        Map<String, Object> config = Map.of(
                "cacheTypes", Map.of(
                        "tasks", "15 minutes TTL",
                        "user_tasks", "10 minutes TTL", 
                        "team_tasks", "8 minutes TTL",
                        "project_tasks", "8 minutes TTL",
                        "task_stats", "5 minutes TTL"
                ),
                "features", Map.of(
                        "cacheFirst", true,
                        "fallbackToDatabase", true,
                        "metricsEnabled", true,
                        "bulkEviction", true,
                        "healthCheck", true
                )
        );
        
        return ResponseEntity.ok(config);
    }

    /**
     * Trigger manual cache warmup
     * Admin only operation
     */
    @PostMapping("/warmup")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> triggerWarmup() {
        log.info("🔥 Admin triggering manual cache warmup");
        
        try {
            cacheWarmupService.manualWarmup();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Cache warmup triggered successfully"
            ));
        } catch (Exception e) {
            log.error("❌ Failed to trigger cache warmup", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to trigger cache warmup",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get cache warmup status
     */
    @GetMapping("/warmup/status")
    public ResponseEntity<CacheWarmupService.WarmupStatus> getWarmupStatus() {
        log.debug("🔥 Getting cache warmup status");
        
        try {
            CacheWarmupService.WarmupStatus status = cacheWarmupService.getWarmupStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("❌ Failed to get warmup status", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}