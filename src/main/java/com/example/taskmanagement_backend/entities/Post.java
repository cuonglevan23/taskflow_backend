package com.example.taskmanagement_backend.entities;

import com.example.taskmanagement_backend.enums.PostPrivacy;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "privacy", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PostPrivacy privacy = PostPrivacy.FRIENDS;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_s3_key", length = 500)
    private String imageS3Key;

    // Liên kết với task (optional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_task_id")
    private Task linkedTask;

    // Liên kết với project (optional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_project_id")
    private Project linkedProject;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private Integer commentCount = 0;

    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private Boolean isPinned = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // One-to-many relationships
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostLike> likes = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostAttachment> attachments = new ArrayList<>();

    // Business methods
    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void incrementCommentCount() {
        this.commentCount++;
    }

    public void decrementCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }

    public boolean isPublic() {
        return privacy == PostPrivacy.PUBLIC;
    }

    public boolean isFriendsOnly() {
        return privacy == PostPrivacy.FRIENDS;
    }

    public boolean isPrivate() {
        return privacy == PostPrivacy.PRIVATE;
    }
}
