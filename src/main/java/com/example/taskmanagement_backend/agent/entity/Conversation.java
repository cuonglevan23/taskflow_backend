package com.example.taskmanagement_backend.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent Conversation Entity - MySQL/JPA with optimized indexing for audit queries
 * Separate from main app conversations - used for AI chat audit logging
 */
@Entity(name = "AgentConversation")
@Table(name = "agent_conversations",
       indexes = {
           @Index(name = "idx_user_id", columnList = "user_id"),
           @Index(name = "idx_conversation_id", columnList = "conversation_id"),
           @Index(name = "idx_user_status", columnList = "user_id, status"),
           @Index(name = "idx_created_at", columnList = "created_at"),
           @Index(name = "idx_last_activity", columnList = "last_activity_at"),
           @Index(name = "idx_category", columnList = "category"),
           @Index(name = "idx_tags_search", columnList = "tags"), // Fixed: removed (255)
           @Index(name = "idx_supervisor", columnList = "supervisor_id"),
           @Index(name = "idx_status", columnList = "status")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", unique = true, nullable = false)
    private String conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title")
    private String title;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    // Agent configuration
    @Column(name = "agent_active")
    @Builder.Default
    private Boolean agentActive = true;

    @Column(name = "ai_personality")
    private String aiPersonality;

    @Column(name = "language")
    @Builder.Default
    private String language = "vi";

    // Takeover management
    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Column(name = "taken_over_at")
    private LocalDateTime takenOverAt;

    // Analytics
    @Column(name = "message_count")
    @Builder.Default
    private Integer messageCount = 0;

    @Column(name = "satisfaction_score")
    private Double satisfactionScore;

    @Column(name = "category")
    private String category;

    @Column(name = "tags")
    private String tags; // JSON array as string

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    // Relationships
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChatMessage> messages;

    public enum ConversationStatus {
        ACTIVE, TAKEOVER, CLOSED, ARCHIVED
    }
}
