package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.entities.AuditLog;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.AuditLogJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Automatic Audit Logger for tracking user activities
 * Tự động ghi lại các hoạt động quan trọng của user
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogger {

    private final AuditLogJpaRepository auditLogRepository;
    private final UserJpaRepository userRepository;

    // ===== TASK OPERATIONS =====

    @Async
    public void logTaskCreated(Long userId, Long taskId, String taskTitle) {
        logAction(userId, "TASK_CREATED", "TASK", taskId.toString(),
                String.format("Created task: %s", taskTitle), "LOW");
    }

    @Async
    public void logTaskUpdated(Long userId, Long taskId, String taskTitle, String changes) {
        logAction(userId, "TASK_UPDATED", "TASK", taskId.toString(),
                String.format("Updated task '%s': %s", taskTitle, changes), "LOW");
    }

    @Async
    public void logTaskDeleted(Long userId, Long taskId, String taskTitle) {
        logAction(userId, "TASK_DELETED", "TASK", taskId.toString(),
                String.format("Deleted task: %s", taskTitle), "MEDIUM");
    }

    @Async
    public void logTaskStatusChanged(Long userId, Long taskId, String taskTitle, String oldStatus, String newStatus) {
        logAction(userId, "TASK_STATUS_CHANGED", "TASK", taskId.toString(),
                String.format("Changed task '%s' status from %s to %s", taskTitle, oldStatus, newStatus), "LOW");
    }

    @Async
    public void logTaskAssigned(Long userId, Long taskId, String taskTitle, Long assigneeId, String assigneeName) {
        logAction(userId, "TASK_ASSIGNED", "TASK", taskId.toString(),
                String.format("Assigned task '%s' to %s (ID: %d)", taskTitle, assigneeName, assigneeId), "LOW");
    }

    // ===== PROJECT OPERATIONS =====

    @Async
    public void logProjectCreated(Long userId, Long projectId, String projectName) {
        logAction(userId, "PROJECT_CREATED", "PROJECT", projectId.toString(),
                String.format("Created project: %s", projectName), "MEDIUM");
    }

    @Async
    public void logProjectUpdated(Long userId, Long projectId, String projectName, String changes) {
        logAction(userId, "PROJECT_UPDATED", "PROJECT", projectId.toString(),
                String.format("Updated project '%s': %s", projectName, changes), "LOW");
    }

    @Async
    public void logProjectDeleted(Long userId, Long projectId, String projectName) {
        logAction(userId, "PROJECT_DELETED", "PROJECT", projectId.toString(),
                String.format("Deleted project: %s", projectName), "HIGH");
    }

    @Async
    public void logProjectMemberAdded(Long userId, Long projectId, String projectName, Long memberId, String memberName, String role) {
        logAction(userId, "PROJECT_MEMBER_ADDED", "PROJECT", projectId.toString(),
                String.format("Added %s (ID: %d) to project '%s' with role: %s", memberName, memberId, projectName, role), "MEDIUM");
    }

    @Async
    public void logProjectMemberRemoved(Long userId, Long projectId, String projectName, Long memberId, String memberName) {
        logAction(userId, "PROJECT_MEMBER_REMOVED", "PROJECT", projectId.toString(),
                String.format("Removed %s (ID: %d) from project '%s'", memberName, memberId, projectName), "MEDIUM");
    }

    @Async
    public void logProjectRoleChanged(Long userId, Long projectId, String projectName, Long memberId, String memberName, String oldRole, String newRole) {
        logAction(userId, "PROJECT_ROLE_CHANGED", "PROJECT", projectId.toString(),
                String.format("Changed %s's role in project '%s' from %s to %s", memberName, projectName, oldRole, newRole), "HIGH");
    }

    // ===== USER MANAGEMENT =====

    @Async
    public void logUserLogin(Long userId, String email, boolean success) {
        String action = success ? "USER_LOGIN_SUCCESS" : "USER_LOGIN_FAILED";
        String severity = success ? "LOW" : "HIGH";
        logAction(userId, action, "USER", userId != null ? userId.toString() : "unknown",
                String.format("Login attempt for email: %s", email), severity);
    }

    @Async
    public void logUserLogout(Long userId, String email) {
        logAction(userId, "USER_LOGOUT", "USER", userId.toString(),
                String.format("User logged out: %s", email), "LOW");
    }

    @Async
    public void logUserRegistered(Long userId, String email) {
        logAction(userId, "USER_REGISTERED", "USER", userId.toString(),
                String.format("New user registered: %s", email), "MEDIUM");
    }

    @Async
    public void logUserRoleChanged(Long adminId, Long targetUserId, String targetEmail, String oldRole, String newRole) {
        logAction(adminId, "USER_ROLE_CHANGED", "USER", targetUserId.toString(),
                String.format("Changed user %s role from %s to %s", targetEmail, oldRole, newRole), "CRITICAL");
    }

    @Async
    public void logUserStatusChanged(Long adminId, Long targetUserId, String targetEmail, String oldStatus, String newStatus) {
        logAction(adminId, "USER_STATUS_CHANGED", "USER", targetUserId.toString(),
                String.format("Changed user %s status from %s to %s", targetEmail, oldStatus, newStatus), "HIGH");
    }

    @Async
    public void logPasswordChanged(Long userId, String email) {
        logAction(userId, "PASSWORD_CHANGED", "USER", userId.toString(),
                String.format("Password changed for user: %s", email), "MEDIUM");
    }

    // ===== SECURITY EVENTS =====

    @Async
    public void logSuspiciousActivity(Long userId, String activity, String details) {
        logAction(userId, "SUSPICIOUS_ACTIVITY", "SECURITY", userId != null ? userId.toString() : "unknown",
                String.format("Suspicious activity detected: %s - %s", activity, details), "CRITICAL");
    }

    @Async
    public void logUnauthorizedAccess(Long userId, String resource, String method) {
        logAction(userId, "UNAUTHORIZED_ACCESS", "SECURITY", resource,
                String.format("Unauthorized access attempt to %s with method %s", resource, method), "HIGH");
    }

    @Async
    public void logDataExport(Long userId, String dataType, int recordCount) {
        logAction(userId, "DATA_EXPORT", "DATA", dataType,
                String.format("Exported %d records of type: %s", recordCount, dataType), "MEDIUM");
    }

    // ===== CORE LOGGING METHOD =====

    private void logAction(Long userId, String action, String entityType, String entityId, String details, String severity) {
        try {
            // Get user from database
            User user = null;
            if (userId != null) {
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    user = userOpt.get();
                } else {
                    log.warn("⚠️ [AuditLogger] User not found for ID: {}, logging anyway", userId);
                }
            }

            // Get request information
            String ipAddress = getClientIpAddress();
            String userAgent = getUserAgent();
            String sessionId = getSessionId();

            // Create audit log
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .severity(severity)
                    .success(true)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .sessionId(sessionId)
                    .createdAt(LocalDateTime.now())
                    .build();

            // Save to database
            auditLogRepository.save(auditLog);

            log.info("📝 [AuditLogger] Logged action: {} for user: {} - Entity: {}/{} - Severity: {}",
                    action, userId, entityType, entityId, severity);

        } catch (Exception e) {
            log.error("❌ [AuditLogger] Failed to log action: {} for user: {} - Error: {}",
                    action, userId, e.getMessage(), e);
        }
    }

    // ===== HELPER METHODS =====

    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }

                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp;
                }

                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not get client IP address: {}", e.getMessage());
        }
        return "unknown";
    }

    private String getUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Could not get user agent: {}", e.getMessage());
        }
        return "unknown";
    }

    private String getSessionId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getSession().getId();
            }
        } catch (Exception e) {
            log.debug("Could not get session ID: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Get current authenticated user ID from security context
     */
    public Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
                String email = authentication.getName();
                return userRepository.findByEmail(email)
                        .map(User::getId)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.debug("Could not get current user ID: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Convenience method for logging without specifying user ID (gets from context)
     */
    @Async
    public void logCurrentUserAction(String action, String entityType, String entityId, String details, String severity) {
        Long userId = getCurrentUserId();
        logAction(userId, action, entityType, entityId, details, severity);
    }
}
