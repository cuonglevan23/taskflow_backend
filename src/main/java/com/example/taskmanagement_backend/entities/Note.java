package com.example.taskmanagement_backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "notes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    // ✅ USING JSON for MySQL XAMPP with fallback support
    @Lob
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private String content; // JSON content for BlockNote editor

    @Column(length = 1000)
    private String description; // Optional plain text description

    // Note can belong to either a user OR a project, not both
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // Personal note

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project; // Project note

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator; // Who created this note

    // NEW: File attachments support
    @OneToMany(mappedBy = "note", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<NoteAttachment> attachments = new ArrayList<>();

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false; // For project notes - can other team members view?

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private Boolean isArchived = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods to check note type
    public boolean isPersonalNote() {
        return user != null && project == null;
    }

    public boolean isProjectNote() {
        return project != null && user == null;
    }

    // NEW: Helper methods for attachments
    public int getAttachmentCount() {
        return attachments != null ? attachments.size() : 0;
    }

    public boolean hasAttachments() {
        return getAttachmentCount() > 0;
    }

    public List<NoteAttachment> getImageAttachments() {
        if (attachments == null) return new ArrayList<>();
        return attachments.stream()
                .filter(NoteAttachment::isImage)
                .toList();
    }

    public long getTotalAttachmentSize() {
        if (attachments == null) return 0;
        return attachments.stream()
                .mapToLong(attachment -> attachment.getFileSize())
                .sum();
    }
}
