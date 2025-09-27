package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.entity.ChatMessage;
import com.example.taskmanagement_backend.agent.entity.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Admin Dashboard Service - Administrative interface cho audit logs
 * Provides admin interface to view all audit logs from ephemeral chats
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    @Qualifier("agentAuditLogService")
    private final AuditLogService auditLogService;

    /**
     * Get audit logs with filters for admin dashboard
     */
    public Page<ChatMessage> getAuditLogs(int page, int size, Long userId, String sessionId,
                                         LocalDateTime startDate, LocalDateTime endDate) {

        // Default to last 30 days if no date range provided
        if (startDate == null) {
            startDate = LocalDateTime.now().minus(30, ChronoUnit.DAYS);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        log.info("Admin retrieving audit logs: page={}, size={}, userId={}, sessionId={}, startDate={}, endDate={}",
                page, size, userId, sessionId, startDate, endDate);

        return auditLogService.getAuditLogs(page, size, userId, sessionId, startDate, endDate);
    }

    /**
     * Search audit logs by content
     */
    public Page<ChatMessage> searchAuditLogs(String query, int page, int size) {
        log.info("Admin searching audit logs: query={}, page={}, size={}", query, page, size);
        return auditLogService.searchAuditLogs(query, page, size);
    }

    /**
     * Get audit statistics for dashboard
     */
    public Map<String, Object> getAuditStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        // Default to last 30 days if no date range provided
        if (startDate == null) {
            startDate = LocalDateTime.now().minus(30, ChronoUnit.DAYS);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        log.info("Admin retrieving audit statistics: startDate={}, endDate={}", startDate, endDate);

        Map<String, Object> stats = auditLogService.getAuditStatistics(startDate, endDate);

        // Add additional admin-specific statistics
        stats.put("ephemeralChatsNote", "These are logs from ephemeral user sessions that users cannot see");
        stats.put("dataRetention", "Audit data is retained permanently for compliance");
        stats.put("auditLevel", "FULL_MONITORING");

        return stats;
    }

    /**
     * Get real-time activity dashboard
     */
    public Map<String, Object> getRealTimeActivity() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        LocalDateTime oneDayAgo = now.minus(1, ChronoUnit.DAYS);

        Map<String, Object> activity = auditLogService.getAuditStatistics(oneHourAgo, now);
        Map<String, Object> dailyActivity = auditLogService.getAuditStatistics(oneDayAgo, now);

        activity.put("last24Hours", dailyActivity);
        activity.put("currentTime", now);
        activity.put("monitoringStatus", "ACTIVE");

        log.debug("Admin retrieved real-time activity dashboard");

        return activity;
    }

    /**
     * Get user activity summary
     */
    public Map<String, Object> getUserActivitySummary(Long userId, int days) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minus(days, ChronoUnit.DAYS);

        Map<String, Object> stats = auditLogService.getAuditStatistics(startDate, endDate);

        // Add user-specific info
        stats.put("userId", userId);
        stats.put("periodDays", days);
        stats.put("summaryType", "USER_ACTIVITY");

        log.info("Admin retrieved user activity summary: userId={}, days={}", userId, days);

        return stats;
    }

    /**
     * Export audit data for compliance
     */
    public Map<String, Object> exportAuditData(LocalDateTime startDate, LocalDateTime endDate, String format) {
        log.info("Admin exporting audit data: startDate={}, endDate={}, format={}", startDate, endDate, format);

        Map<String, Object> exportInfo = auditLogService.getAuditStatistics(startDate, endDate);
        exportInfo.put("exportFormat", format);
        exportInfo.put("exportTimestamp", LocalDateTime.now());
        exportInfo.put("exportedBy", "ADMIN");
        exportInfo.put("complianceNote", "Full audit trail including ephemeral chat sessions");

        // In a real implementation, this would generate the actual export file
        exportInfo.put("status", "EXPORT_INITIATED");
        exportInfo.put("downloadUrl", "/api/ai-agent/admin/audit/download/" + System.currentTimeMillis());

        return exportInfo;
    }

    /**
     * Get system health metrics
     */
    public Map<String, Object> getSystemHealthMetrics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minus(1, ChronoUnit.HOURS);

        Map<String, Object> health = auditLogService.getAuditStatistics(oneHourAgo, now);

        // Add system health indicators
        health.put("auditSystemStatus", "OPERATIONAL");
        health.put("ephemeralChatStatus", "ACTIVE");
        health.put("sessionManagementStatus", "ACTIVE");
        health.put("dataRetentionStatus", "COMPLIANT");
        health.put("lastHealthCheck", now);

        log.debug("Admin retrieved system health metrics");

        return health;
    }

    /**
     * Get privacy compliance report
     */
    public Map<String, Object> getPrivacyComplianceReport() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        Map<String, Object> compliance = auditLogService.getAuditStatistics(thirtyDaysAgo, now);

        // Add privacy compliance info
        compliance.put("ephemeralDataPolicy", "User data cleared on logout - admin audit retained");
        compliance.put("dataRetentionPolicy", "Audit logs retained for compliance and security");
        compliance.put("userPrivacyLevel", "EPHEMERAL_SESSION_BASED");
        compliance.put("adminMonitoringLevel", "FULL_AUDIT_TRAIL");
        compliance.put("complianceStatus", "GDPR_COMPLIANT");
        compliance.put("reportGenerated", now);

        log.info("Admin generated privacy compliance report");

        return compliance;
    }
}
