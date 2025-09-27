package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.AuditLogDto.*;
import com.example.taskmanagement_backend.entities.AuditLog;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.AuditLogJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogJpaRepository auditRepo;
    private final UserJpaRepository userRepo;

    public AuditLogResponseDto create(CreateAuditLogRequestDto dto) {
        User user = null;
        if (dto.getUserId() != null) {
            user = userRepo.findById(dto.getUserId())
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
        }

        AuditLog log = AuditLog.builder()
                .user(user)
                .action(dto.getAction())
                .createdAt(LocalDateTime.now())
                .build();

        return toDto(auditRepo.save(log));
    }

    public AuditLogResponseDto getById(Long id) {
        return auditRepo.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("AuditLog not found"));
    }

    public List<AuditLogResponseDto> getAll() {
        return auditRepo.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public void delete(Long id) {
        if (!auditRepo.existsById(id)) {
            throw new EntityNotFoundException("AuditLog not found");
        }
        auditRepo.deleteById(id);
    }

    // Find AuditLog by id user
    public List<AuditLogResponseDto> findByUserId(Long userId) {
        return auditRepo.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ===== ADMIN-SPECIFIC METHODS =====

    /**
     * Get audit logs with advanced filtering and pagination
     */
    public Page<AuditLogResponseDto> getAuditLogsWithFilters(
            Pageable pageable, Long userId, String action, String entityType,
            String entityId, LocalDateTime startDate, LocalDateTime endDate,
            String search, String ipAddress, boolean suspiciousOnly) {

        try {
            log.info("🔍 [AuditLogService] Getting audit logs with filters - User: {}, Action: {}, Suspicious: {}",
                    userId, action, suspiciousOnly);

            Specification<AuditLog> spec = (root, query, criteriaBuilder) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

                // Filter by user ID
                if (userId != null) {
                    predicates.add(criteriaBuilder.equal(root.get("user").get("id"), userId));
                }

                // Filter by action
                if (action != null && !action.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("action")),
                            "%" + action.toLowerCase() + "%"
                    ));
                }

                // Filter by entity type
                if (entityType != null && !entityType.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("entityType")),
                            "%" + entityType.toLowerCase() + "%"
                    ));
                }

                // Filter by entity ID
                if (entityId != null && !entityId.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.equal(root.get("entityId"), entityId));
                }

                // Filter by date range
                if (startDate != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
                }
                if (endDate != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
                }

                // Search in action description
                if (search != null && !search.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("action")),
                            "%" + search.toLowerCase() + "%"
                    ));
                }

                // Filter by IP address
                if (ipAddress != null && !ipAddress.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.equal(root.get("ipAddress"), ipAddress));
                }

                // Filter suspicious activities (placeholder logic)
                if (suspiciousOnly) {
                    // Add logic to detect suspicious patterns
                    // For now, we'll mark frequent actions as suspicious
                    predicates.add(criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("action")),
                            "%failed%"
                    ));
                }

                return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };

            Page<AuditLog> auditLogPage = auditRepo.findAll(spec, pageable);
            List<AuditLogResponseDto> auditLogs = auditLogPage.getContent().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            return new PageImpl<>(auditLogs, pageable, auditLogPage.getTotalElements());
        } catch (Exception e) {
            log.error("❌ [AuditLogService] Error getting audit logs with filters: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get audit logs", e);
        }
    }

    /**
     * Get audit logs by user ID with pagination
     */
    public Page<AuditLogResponseDto> getAuditLogsByUserId(Long userId, Pageable pageable, int days) {
        try {
            log.info("🔍 [AuditLogService] Getting audit logs for user: {} - Days: {}", userId, days);

            LocalDateTime startDate = LocalDateTime.now().minusDays(days);

            Specification<AuditLog> spec = (root, query, criteriaBuilder) ->
                    criteriaBuilder.and(
                            criteriaBuilder.equal(root.get("user").get("id"), userId),
                            criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate)
                    );

            Page<AuditLog> auditLogPage = auditRepo.findAll(spec, pageable);
            List<AuditLogResponseDto> auditLogs = auditLogPage.getContent().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            return new PageImpl<>(auditLogs, pageable, auditLogPage.getTotalElements());
        } catch (Exception e) {
            log.error("❌ [AuditLogService] Error getting user audit logs: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get user audit logs", e);
        }
    }

    /**
     * Detect suspicious activities
     */
    public Map<String, Object> detectSuspiciousActivities(int hours, int threshold) {
        try {
            log.info("🔍 [AuditLogService] Detecting suspicious activities - Hours: {}, Threshold: {}", hours, threshold);

            LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

            // Get all audit logs in the time range
            List<AuditLog> recentLogs = auditRepo.findByCreatedAtAfter(startTime);

            // Group by user and count actions
            Map<Long, Long> userActionCounts = recentLogs.stream()
                    .filter(log -> log.getUser() != null)
                    .collect(Collectors.groupingBy(
                            log -> log.getUser().getId(),
                            Collectors.counting()
                    ));

            // Find users exceeding threshold
            List<Map<String, Object>> suspiciousUsers = userActionCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() > threshold)
                    .map(entry -> {
                        User user = userRepo.findById(entry.getKey()).orElse(null);
                        Map<String, Object> suspiciousUser = new HashMap<>();
                        suspiciousUser.put("userId", entry.getKey());
                        suspiciousUser.put("userEmail", user != null ? user.getEmail() : "Unknown");
                        suspiciousUser.put("actionCount", entry.getValue());
                        suspiciousUser.put("timeFrame", hours + " hours");
                        suspiciousUser.put("threshold", threshold);
                        return suspiciousUser;
                    })
                    .collect(Collectors.toList());

            // Detect failed login attempts
            long failedLogins = recentLogs.stream()
                    .filter(log -> log.getAction().toLowerCase().contains("failed") ||
                            log.getAction().toLowerCase().contains("login"))
                    .count();

            Map<String, Object> report = new HashMap<>();
            report.put("analysisTimeRange", hours + " hours");
            report.put("threshold", threshold);
            report.put("suspiciousUsers", suspiciousUsers);
            report.put("totalSuspiciousUsers", suspiciousUsers.size());
            report.put("failedLoginAttempts", failedLogins);
            report.put("totalActionsAnalyzed", recentLogs.size());
            report.put("generatedAt", LocalDateTime.now());

            return report;
        } catch (Exception e) {
            log.error("❌ [AuditLogService] Error detecting suspicious activities: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to detect suspicious activities", e);
        }
    }

    /**
     * Get audit statistics
     */
    public Map<String, Object> getAuditStatistics(int days) {
        try {
            log.info("🔍 [AuditLogService] Getting audit statistics for {} days", days);

            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<AuditLog> logs = auditRepo.findByCreatedAtAfter(startDate);

            // Count by action types
            Map<String, Long> actionCounts = logs.stream()
                    .collect(Collectors.groupingBy(
                            AuditLog::getAction,
                            Collectors.counting()
                    ));

            // Count by users
            Map<Long, Long> userCounts = logs.stream()
                    .filter(log -> log.getUser() != null)
                    .collect(Collectors.groupingBy(
                            log -> log.getUser().getId(),
                            Collectors.counting()
                    ));

            // Daily activity counts
            Map<String, Long> dailyActivity = logs.stream()
                    .collect(Collectors.groupingBy(
                            log -> log.getCreatedAt().toLocalDate().toString(),
                            Collectors.counting()
                    ));

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("timeRange", days + " days");
            statistics.put("totalActivities", logs.size());
            statistics.put("uniqueUsers", userCounts.size());
            statistics.put("actionBreakdown", actionCounts);
            statistics.put("topUsers", userCounts.entrySet().stream()
                    .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                    .limit(10)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    )));
            statistics.put("dailyActivity", dailyActivity);
            statistics.put("generatedAt", LocalDateTime.now());

            return statistics;
        } catch (Exception e) {
            log.error("❌ [AuditLogService] Error getting audit statistics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get audit statistics", e);
        }
    }

    /**
     * Get recent activities
     */
    public List<AuditLogResponseDto> getRecentActivities(int limit) {
        try {
            log.info("🔍 [AuditLogService] Getting {} recent activities", limit);

            List<AuditLog> recentLogs = auditRepo.findTopByOrderByCreatedAtDesc(limit);

            return recentLogs.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("❌ [AuditLogService] Error getting recent activities: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get recent activities", e);
        }
    }

    /**
     * Get audit logs by action type
     */
    public Page<AuditLogResponseDto> getAuditLogsByAction(String actionType, Pageable pageable, int days) {
        try {
            log.info("🔍 [AuditLogService] Getting audit logs for action: {} - Days: {}", actionType, days);

            LocalDateTime startDate = LocalDateTime.now().minusDays(days);

            Specification<AuditLog> spec = (root, query, criteriaBuilder) ->
                    criteriaBuilder.and(
                            criteriaBuilder.like(
                                    criteriaBuilder.lower(root.get("action")),
                                    "%" + actionType.toLowerCase() + "%"
                            ),
                            criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate)
                    );

            Page<AuditLog> auditLogPage = auditRepo.findAll(spec, pageable);
            List<AuditLogResponseDto> auditLogs = auditLogPage.getContent().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            return new PageImpl<>(auditLogs, pageable, auditLogPage.getTotalElements());
        } catch (Exception e) {
            log.error("❌ [AuditLogService] Error getting action audit logs: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get action audit logs", e);
        }
    }

    /**
     * Export audit logs
     */
    public Map<String, Object> exportAuditLogs(LocalDateTime startDate, LocalDateTime endDate, String format) {
        try {
            log.info("🔍 [AuditLogService] Exporting audit logs from {} to {} in {} format",
                    startDate, endDate, format);

            List<AuditLog> logs = auditRepo.findByCreatedAtBetween(startDate, endDate);
            List<AuditLogResponseDto> exportLogs = logs.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            Map<String, Object> exportResult = new HashMap<>();
            exportResult.put("format", format);
            exportResult.put("startDate", startDate);
            exportResult.put("endDate", endDate);
            exportResult.put("totalRecords", exportLogs.size());
            exportResult.put("data", exportLogs);
            exportResult.put("exportedAt", LocalDateTime.now());

            return exportResult;
        } catch (Exception e) {
            log.error("❌ [AuditLogService] Error exporting audit logs: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to export audit logs", e);
        }
    }

    /**
     * Cleanup old audit logs
     */
    public Map<String, Object> cleanupOldAuditLogs(int daysOld) {
        try {
            log.info("🔄 [AuditLogService] Cleaning up audit logs older than {} days", daysOld);

            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
            List<AuditLog> oldLogs = auditRepo.findByCreatedAtBefore(cutoffDate);

            int deletedCount = oldLogs.size();
            auditRepo.deleteByCreatedAtBefore(cutoffDate);

            Map<String, Object> cleanupResult = new HashMap<>();
            cleanupResult.put("cutoffDate", cutoffDate);
            cleanupResult.put("deletedCount", deletedCount);
            cleanupResult.put("cleanupCompletedAt", LocalDateTime.now());

            log.info("✅ [AuditLogService] Cleaned up {} old audit logs", deletedCount);
            return cleanupResult;
        } catch (Exception e) {
            log.error("❌ [AuditLogService] Error during audit logs cleanup: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to cleanup audit logs", e);
        }
    }

    private AuditLogResponseDto toDto(AuditLog log) {
        return AuditLogResponseDto.builder()
                .id(log.getId())
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .userEmail(log.getUser() != null ? log.getUser().getEmail() : null)
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
