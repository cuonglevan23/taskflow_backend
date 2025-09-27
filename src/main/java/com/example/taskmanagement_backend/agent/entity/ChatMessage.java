package com.example.taskmanagement_backend.agent.entity;

import com.example.taskmanagement_backend.agent.enums.SenderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Chat Message Entity - MySQL/JPA with optimized indexing for audit queries
 */
@Entity
@Table(name = "agent_chat_messages",
       indexes = {
           @Index(name = "idx_user_session_created", columnList = "user_id, session_id, created_at"),
           @Index(name = "idx_user_created", columnList = "user_id, created_at"),
           @Index(name = "idx_created_at", columnList = "created_at"),
           @Index(name = "idx_session_id", columnList = "session_id"),
           @Index(name = "idx_sender_type", columnList = "sender_type"),
           @Index(name = "idx_conversation_created", columnList = "conversation_id, created_at")
           // Removed idx_content_search as TEXT columns need explicit key length in MySQL
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", unique = true, nullable = false)
    private String messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "sender_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SenderType senderType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // AI-specific fields
    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "confidence_score")
    private Double confidence;

    @Column(name = "detected_intent")
    private String intent;

    @Column(name = "tags")
    private String tags; // JSON array as string

    @Column(name = "rag_context", columnDefinition = "TEXT")
    private String ragContext;

    // Metadata
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "language")
    private String language;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;
}
