package com.example.taskmanagement_backend.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class PostAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "s3_key", nullable = false, length = 1000)
    private String s3Key;

    @Column(name = "s3_url", length = 2000)
    private String s3Url;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "attachment_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AttachmentType attachmentType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Business methods
    public boolean isImage() {
        return attachmentType == AttachmentType.IMAGE;
    }

    public boolean isDocument() {
        return attachmentType == AttachmentType.DOCUMENT;
    }

    public boolean isVideo() {
        return attachmentType == AttachmentType.VIDEO;
    }

    public enum AttachmentType {
        IMAGE, DOCUMENT, VIDEO, OTHER
    }
}
