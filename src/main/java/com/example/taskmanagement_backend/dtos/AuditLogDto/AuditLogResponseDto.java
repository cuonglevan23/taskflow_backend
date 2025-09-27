package com.example.taskmanagement_backend.dtos.AuditLogDto;


import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponseDto {

    private Long id;

    private Long userId;

    private String userEmail;

    private String userFullName;

    private String action;

    // Enhanced audit tracking fields
    private String entityType;     // Type of entity being audited (USER, PROJECT, TEAM, TASK)
    private String entityId;       // ID of the affected entity
    private String ipAddress;      // User's IP address for security tracking
    private String userAgent;      // Browser/client information
    private String sessionId;      // Session identifier
    private String details;        // Additional details in JSON format
    private String severity;       // LOW, MEDIUM, HIGH, CRITICAL
    private Boolean success;       // Whether the action was successful

    private LocalDateTime createdAt;

    // Helper methods for display
    public String getFormattedAction() {
        return action != null ? action.replaceAll("_", " ").toLowerCase() : "";
    }

    public String getSeverityBadgeClass() {
        if (severity == null) return "badge-secondary";
        return switch (severity.toUpperCase()) {
            case "LOW" -> "badge-success";
            case "MEDIUM" -> "badge-warning";
            case "HIGH" -> "badge-danger";
            case "CRITICAL" -> "badge-dark";
            default -> "badge-secondary";
        };
    }

    public String getActionTypeCategory() {
        if (action == null) return "OTHER";

        String actionLower = action.toLowerCase();
        if (actionLower.contains("login") || actionLower.contains("logout")) return "AUTH";
        if (actionLower.contains("create") || actionLower.contains("add")) return "CREATE";
        if (actionLower.contains("update") || actionLower.contains("edit")) return "UPDATE";
        if (actionLower.contains("delete") || actionLower.contains("remove")) return "DELETE";
        if (actionLower.contains("view") || actionLower.contains("read")) return "READ";
        if (actionLower.contains("failed") || actionLower.contains("error")) return "ERROR";

        return "OTHER";
    }

    public boolean isSuspicious() {
        return "HIGH".equals(severity) || "CRITICAL".equals(severity) ||
                (success != null && !success) ||
                (action != null && action.toLowerCase().contains("failed"));
    }
}