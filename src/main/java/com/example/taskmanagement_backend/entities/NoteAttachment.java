package com.example.taskmanagement_backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "note_attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName; // Original filename

    @Column(nullable = false, unique = true)
    private String storedFileName; // Unique filename on server

    @Column(nullable = false)
    private String filePath; // Path to file on server

    @Column(nullable = false)
    private String contentType; // MIME type

    @Column(nullable = false)
    private Long fileSize; // File size in bytes

    @Column(length = 500)
    private String description; // Optional description

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note; // Which note this file belongs to

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy; // Who uploaded this file

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

    // Helper methods
    public boolean isImage() {
        if (contentType == null) return false;
        return contentType.startsWith("image/");
    }

    public boolean isDocument() {
        if (contentType == null) return false;
        return contentType.equals("application/pdf") ||
               contentType.startsWith("application/msword") ||
               contentType.startsWith("application/vnd.openxmlformats");
    }

    public String getFormattedFileSize() {
        if (fileSize == null) return "0 B";

        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024 * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
    }

    public String getFileExtension() {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        return lastDot != -1 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }
}
