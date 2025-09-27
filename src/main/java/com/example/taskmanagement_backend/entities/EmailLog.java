package com.example.taskmanagement_backend.entities;

import com.example.taskmanagement_backend.enums.EmailDirection;
import com.example.taskmanagement_backend.enums.EmailStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "gmail_message_id", length = 255)
    private String gmailMessageId;
    
    @Column(name = "subject", length = 255)
    private String subject;
    
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmailStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private EmailDirection direction;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "sender_email", length = 255)
    private String senderEmail;

    @Column(name = "thread_id", length = 255)
    private String threadId;

    @Column(name = "label_ids", columnDefinition = "TEXT")
    private String labelIds; // JSON string of label IDs

    @Builder.Default
    @Column(name = "has_attachments")
    private Boolean hasAttachments = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
