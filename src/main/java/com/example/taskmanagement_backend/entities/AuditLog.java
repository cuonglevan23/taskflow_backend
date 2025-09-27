package com.example.taskmanagement_backend.entities;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_created_at", columnList = "created_at"),
    @Index(name = "idx_audit_entity_type", columnList = "entity_type"),
    @Index(name = "idx_audit_ip_address", columnList = "ip_address"),
    @Index(name = "idx_audit_severity", columnList = "severity")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ON DELETE SET NULL => optional = true, nullable = true
    @ManyToOne
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_auditlog_user"))
    private User user;

    @Column(nullable = false, length = 500)
    private String action; // Changed from @Lob to regular String with length limit

    // Additional fields for comprehensive audit tracking
    @Column(name = "entity_type", length = 50)
    private String entityType; // e.g., "USER", "PROJECT", "TEAM", "TASK"

    @Column(name = "entity_id", length = 50)
    private String entityId; // ID of the affected entity

    @Column(name = "ip_address", length = 45)
    private String ipAddress; // User's IP address for security tracking

    @Column(name = "user_agent", length = 500)
    private String userAgent; // Browser/client information

    @Column(name = "session_id", length = 100)
    private String sessionId; // Session identifier

    @Lob
    @Column(name = "details")
    private String details; // Additional details in JSON format

    @Column(name = "severity", length = 20)
    private String severity; // "LOW", "MEDIUM", "HIGH", "CRITICAL"

    @Column(name = "success")
    private Boolean success; // Whether the action was successful

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Helper method to create audit log for successful actions
    public static AuditLog success(User user, String action, String entityType, String entityId, String details) {
        return AuditLog.builder()
            .user(user)
            .action(action)
            .entityType(entityType)
            .entityId(entityId)
            .details(details)
            .severity("LOW")
            .success(true)
            .createdAt(LocalDateTime.now())
            .build();
    }

    // Helper method to create audit log for failed actions
    public static AuditLog failure(User user, String action, String entityType, String entityId, String details) {
        return AuditLog.builder()
            .user(user)
            .action(action)
            .entityType(entityType)
            .entityId(entityId)
            .details(details)
            .severity("HIGH")
            .success(false)
            .createdAt(LocalDateTime.now())
            .build();
    }

    // Helper method to create security-related audit log
    public static AuditLog security(User user, String action, String ipAddress, String userAgent, String details) {
        return AuditLog.builder()
            .user(user)
            .action(action)
            .entityType("SECURITY")
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .details(details)
            .severity("CRITICAL")
            .success(false)
            .createdAt(LocalDateTime.now())
            .build();
    }
}