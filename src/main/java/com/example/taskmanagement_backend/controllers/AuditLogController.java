package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.AuditLogDto.*;
import com.example.taskmanagement_backend.services.AuditLogService;
import com.example.taskmanagement_backend.enums.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Audit Logs", description = "Admin APIs for audit logs management and monitoring")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class AuditLogController {

    private final AuditLogService auditLogService;

    // ===== ADMIN AUDIT LOG MANAGEMENT =====

    /**
     * Get all audit logs with advanced filtering and pagination (Admin only)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit logs with filtering",
            description = "Admin endpoint to get all audit logs with pagination, sorting, and advanced filtering")
    public ResponseEntity<Page<AuditLogResponseDto>> getAllAuditLogs(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Filter by user ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "Filter by action type") @RequestParam(required = false) String action,
            @Parameter(description = "Filter by entity type") @RequestParam(required = false) String entityType,
            @Parameter(description = "Filter by entity ID") @RequestParam(required = false) String entityId,
            @Parameter(description = "Start date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Search in action description") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by IP address") @RequestParam(required = false) String ipAddress,
            @Parameter(description = "Show only suspicious activities") @RequestParam(defaultValue = "false") boolean suspiciousOnly) {

        try {
            Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            log.info("🔍 [AuditLogController] Getting audit logs - page: {}, size: {}, user: {}, action: {}, suspicious: {}",
                    page, size, userId, action, suspiciousOnly);

            Page<AuditLogResponseDto> auditLogs = auditLogService.getAuditLogsWithFilters(
                    pageable, userId, action, entityType, entityId, startDate, endDate, search, ipAddress, suspiciousOnly);

            log.info("✅ [AuditLogController] Retrieved {} audit logs", auditLogs.getTotalElements());
            return ResponseEntity.ok(auditLogs);
        } catch (Exception e) {
            log.error("❌ [AuditLogController] Error getting audit logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get audit log by ID (Admin only)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit log by ID",
            description = "Admin endpoint to get detailed audit log information")
    public ResponseEntity<AuditLogResponseDto> getAuditLogById(@PathVariable Long id) {
        try {
            log.info("🔍 [AuditLogController] Getting audit log by ID: {}", id);

            AuditLogResponseDto auditLog = auditLogService.getById(id);

            return ResponseEntity.ok(auditLog);
        } catch (RuntimeException e) {
            log.warn("❌ [AuditLogController] Audit log not found: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [AuditLogController] Error getting audit log: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get audit logs by user ID with pagination (Admin only)
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit logs by user",
            description = "Admin endpoint to get all audit logs for a specific user")
    public ResponseEntity<Page<AuditLogResponseDto>> getAuditLogsByUserId(
            @PathVariable Long userId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Number of days to look back") @RequestParam(defaultValue = "30") int days) {

        try {
            log.info("🔍 [AuditLogController] Getting audit logs for user: {} - Days: {}", userId, days);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<AuditLogResponseDto> userAuditLogs = auditLogService.getAuditLogsByUserId(userId, pageable, days);

            log.info("✅ [AuditLogController] Retrieved {} audit logs for user: {}",
                    userAuditLogs.getTotalElements(), userId);
            return ResponseEntity.ok(userAuditLogs);
        } catch (Exception e) {
            log.error("❌ [AuditLogController] Error getting user audit logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get suspicious activities report (Admin only)
     */
    @GetMapping("/suspicious")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get suspicious activities",
            description = "Admin endpoint to detect and report suspicious user activities")
    public ResponseEntity<Map<String, Object>> getSuspiciousActivities(
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours,
            @Parameter(description = "Minimum actions per hour to be suspicious") @RequestParam(defaultValue = "50") int threshold) {

        try {
            log.info("🔍 [AuditLogController] Analyzing suspicious activities - Hours: {}, Threshold: {}", hours, threshold);

            Map<String, Object> suspiciousReport = auditLogService.detectSuspiciousActivities(hours, threshold);

            log.info("✅ [AuditLogController] Generated suspicious activities report");
            return ResponseEntity.ok(suspiciousReport);
        } catch (Exception e) {
            log.error("❌ [AuditLogController] Error generating suspicious activities report: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get audit statistics and summary (Admin only)
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit statistics",
            description = "Admin endpoint to get audit log statistics and summary")
    public ResponseEntity<Map<String, Object>> getAuditStatistics(
            @Parameter(description = "Number of days for statistics") @RequestParam(defaultValue = "7") int days) {

        try {
            log.info("🔍 [AuditLogController] Getting audit statistics for {} days", days);

            Map<String, Object> statistics = auditLogService.getAuditStatistics(days);

            log.info("✅ [AuditLogController] Retrieved audit statistics");
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("❌ [AuditLogController] Error getting audit statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get recent activities dashboard (Admin only)
     */
    @GetMapping("/recent")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get recent activities",
            description = "Admin endpoint to get recent activities for dashboard")
    public ResponseEntity<List<AuditLogResponseDto>> getRecentActivities(
            @Parameter(description = "Number of recent activities") @RequestParam(defaultValue = "100") int limit) {

        try {
            log.info("🔍 [AuditLogController] Getting {} recent activities", limit);

            List<AuditLogResponseDto> recentActivities = auditLogService.getRecentActivities(limit);

            log.info("✅ [AuditLogController] Retrieved {} recent activities", recentActivities.size());
            return ResponseEntity.ok(recentActivities);
        } catch (Exception e) {
            log.error("❌ [AuditLogController] Error getting recent activities: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get audit logs by action type (Admin only)
     */
    @GetMapping("/actions/{actionType}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit logs by action type",
            description = "Admin endpoint to get audit logs filtered by specific action type")
    public ResponseEntity<Page<AuditLogResponseDto>> getAuditLogsByAction(
            @PathVariable String actionType,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Number of days to look back") @RequestParam(defaultValue = "7") int days) {

        try {
            log.info("🔍 [AuditLogController] Getting audit logs for action: {} - Days: {}", actionType, days);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<AuditLogResponseDto> actionLogs = auditLogService.getAuditLogsByAction(actionType, pageable, days);

            log.info("✅ [AuditLogController] Retrieved {} audit logs for action: {}",
                    actionLogs.getTotalElements(), actionType);
            return ResponseEntity.ok(actionLogs);
        } catch (Exception e) {
            log.error("❌ [AuditLogController] Error getting action audit logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Export audit logs (Admin only)
     */
    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Export audit logs",
            description = "Admin endpoint to export audit logs for external analysis")
    public ResponseEntity<Map<String, Object>> exportAuditLogs(
            @Parameter(description = "Start date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Export format") @RequestParam(defaultValue = "JSON") String format) {

        try {
            log.info("🔍 [AuditLogController] Exporting audit logs from {} to {} in {} format",
                    startDate, endDate, format);

            Map<String, Object> exportResult = auditLogService.exportAuditLogs(startDate, endDate, format);

            log.info("✅ [AuditLogController] Generated audit logs export");
            return ResponseEntity.ok(exportResult);
        } catch (Exception e) {
            log.error("❌ [AuditLogController] Error exporting audit logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Delete old audit logs (Admin only)
     */
    @DeleteMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cleanup old audit logs",
            description = "Admin endpoint to delete audit logs older than specified days")
    public ResponseEntity<Map<String, Object>> cleanupOldAuditLogs(
            @Parameter(description = "Delete logs older than this many days") @RequestParam(defaultValue = "365") int daysOld) {

        try {
            log.info("🔄 [AuditLogController] Cleaning up audit logs older than {} days", daysOld);

            Map<String, Object> cleanupResult = auditLogService.cleanupOldAuditLogs(daysOld);

            log.info("✅ [AuditLogController] Completed audit logs cleanup");
            return ResponseEntity.ok(cleanupResult);
        } catch (Exception e) {
            log.error("❌ [AuditLogController] Error during audit logs cleanup: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===== PUBLIC ENDPOINTS (for creating audit logs) =====

    /**
     * Create audit log entry - Support for LOGIN, PAYMENT, TASK_CREATE, etc.
     */
    @PostMapping
    @Operation(summary = "Create audit log",
            description = "System endpoint to create audit log entries for various actions like LOGIN, PAYMENT, TASK_CREATION, etc.")
    public ResponseEntity<AuditLogResponseDto> createAuditLog(@Valid @RequestBody CreateAuditLogRequestDto dto) {
        try {
            log.info("🔄 [AuditLogController] Creating audit log for user: {}, action: {}",
                    dto.getUserId(), dto.getAction());

            AuditLogResponseDto auditLog = auditLogService.create(dto);

            log.info("✅ [AuditLogController] Created audit log with ID: {}", auditLog.getId());
            return ResponseEntity.ok(auditLog);
        } catch (Exception e) {
            log.error("❌ [AuditLogController] Error creating audit log: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Helper method to provide user-friendly descriptions for different action types
     */
    private String getActionDescription(String actionType) {
        return switch (actionType) {
            case "LOGIN" -> "User login activity";
            case "LOGOUT" -> "User logout activity";
            case "PAYMENT" -> "Payment transaction";
            case "TASK_CREATE" -> "Task creation";
            case "TASK_UPDATE" -> "Task modification";
            case "TASK_DELETE" -> "Task deletion";
            case "PROJECT_CREATE" -> "Project creation";
            case "PROJECT_UPDATE" -> "Project modification";
            case "USER_UPDATE" -> "User profile update";
            case "PASSWORD_CHANGE" -> "Password change";
            case "PERMISSION_CHANGE" -> "Permission modification";
            default -> "System activity";
        };
    }
}
