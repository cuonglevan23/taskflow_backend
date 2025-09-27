package com.example.taskmanagement_backend.entities;

import com.example.taskmanagement_backend.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_notification_user_read", columnList = "user_id, is_read"),
    @Index(name = "idx_notification_type", columnList = "type")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notification_user"))
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(name = "reference_id")
    private Long referenceId; // ID of related entity (task, project, etc.)

    @Column(name = "reference_type")
    private String referenceType; // Type of related entity

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "notification_metadata", joinColumns = @JoinColumn(name = "notification_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;

    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "is_bookmarked")
    @Builder.Default
    private Boolean isBookmarked = false;

    @Column(name = "is_archived")
    @Builder.Default
    private Boolean isArchived = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "bookmarked_at")
    private LocalDateTime bookmarkedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0; // 0=normal, 1=high, 2=urgent

    // Convenience method to mark as read
    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }

    // Convenience method to toggle bookmark
    public void toggleBookmark() {
        this.isBookmarked = !this.isBookmarked;
        this.bookmarkedAt = this.isBookmarked ? LocalDateTime.now() : null;
    }

    // Convenience method to archive notification
    public void archive() {
        this.isArchived = true;
        this.archivedAt = LocalDateTime.now();
    }

    // Convenience method to unarchive notification
    public void unarchive() {
        this.isArchived = false;
        this.archivedAt = null;
    }
}
