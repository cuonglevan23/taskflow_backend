package com.example.taskmanagement_backend.dtos.PostDto;

import com.example.taskmanagement_backend.enums.PostPrivacy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponseDto {
    private Long id;
    private String content;
    private PostPrivacy privacy;
    private String imageUrl; // ✅ Keep for backward compatibility
    private List<String> imageUrls; // ✅ NEW: Multiple images from PostAttachment
    private List<FileAttachmentDto> fileAttachments; // ✅ NEW: Non-image files from PostAttachment
    private List<AttachmentDto> files; // ✅ NEW: Generic files field for backward compatibility
    private AuthorDto author;
    private LinkedTaskDto linkedTask;
    private LinkedProjectDto linkedProject;
    private Integer likeCount;
    private Integer commentCount;
    private Boolean isPinned;
    private Boolean isLikedByCurrentUser;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PostAttachmentDto> attachments; // ✅ Keep for backward compatibility
    private List<RecentLikeDto> recentLikes; // Show 3-5 recent likes
    private List<PostCommentDto> topComments; // Show 2-3 top comments

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorDto {
        private Long id;
        private String email;
        private String firstName;
        private String lastName;
        private String username;
        private String avatarUrl;
        private String jobTitle;
        private String department;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedTaskDto {
        private Long id;
        private String title;
        private String status;
        private String priority;
        private LocalDateTime deadline;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedProjectDto {
        private Long id;
        private String name;
        private String status;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostAttachmentDto {
        private Long id;
        private String originalFilename;
        private String s3Url;
        private String contentType;
        private Long fileSize;
        private String attachmentType;
    }

    /**
     * ✅ NEW: Dedicated DTO for file attachments (non-images)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileAttachmentDto {
        private Long id;
        private String originalFilename;
        private String downloadUrl;
        private String contentType;
        private Long fileSize;
        private String attachmentType;
        private LocalDateTime createdAt;
    }

    /**
     * ✅ NEW: Generic attachment DTO for backward compatibility
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentDto {
        private Long id;
        private String originalFilename;
        private String s3Url;
        private String contentType;
        private Long fileSize;
        private String attachmentType;
        private LocalDateTime uploadedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentLikeDto {
        private Long userId;
        private String username;
        private String avatarUrl;
        private LocalDateTime likedAt;
    }
}
