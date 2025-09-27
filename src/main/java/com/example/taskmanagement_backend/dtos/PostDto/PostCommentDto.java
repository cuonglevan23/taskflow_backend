package com.example.taskmanagement_backend.dtos.PostDto;

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
public class PostCommentDto {
    private Long id;
    private String content;
    private AuthorDto author;
    private Long parentCommentId;
    private Integer likeCount;
    private Integer replyCount;
    private Boolean isLikedByCurrentUser;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<RecentLikeDto> recentLikes;

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
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentLikeDto {
        private Long userId;
        private String username;
        private String firstName;
        private String lastName;
        private String avatarUrl;
        private LocalDateTime likedAt;
    }
}
