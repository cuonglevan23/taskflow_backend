package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.PostDto.*;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.services.PostService;
import com.example.taskmanagement_backend.services.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Social Posts", description = "APIs for social networking posts and newsfeed")
public class PostController {

    private final PostService postService;
    private final NotificationService notificationService;
    private final UserJpaRepository userRepository;

    /**
     * Create a new post with optional images and files
     * POST /api/posts
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create post", description = "Create a new post with optional images, files and task/project links")
    public ResponseEntity<Map<String, Object>> createPost(
            @RequestParam("content") String content,
            @RequestParam(value = "privacy", defaultValue = "FRIENDS") String privacy,
            @RequestParam(value = "linkedTaskId", required = false) Long linkedTaskId,
            @RequestParam(value = "linkedProjectId", required = false) Long linkedProjectId,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "isPinned", defaultValue = "false") Boolean isPinned) {

        try {
            log.info("📝 Creating new post with content length: {}, images: {}, files: {}",
                    content != null ? content.length() : 0,
                    images != null ? images.size() : 0,
                    files != null ? files.size() : 0);

            CreatePostRequestDto requestDto = CreatePostRequestDto.builder()
                    .content(content)
                    .privacy(com.example.taskmanagement_backend.enums.PostPrivacy.valueOf(privacy.toUpperCase()))
                    .linkedTaskId(linkedTaskId)
                    .linkedProjectId(linkedProjectId)
                    .image(image)
                    .images(images)
                    .files(files)
                    .isPinned(isPinned)
                    .build();

            PostResponseDto result = postService.createPost(requestDto);

            // ✅ SIMPLIFIED: No WebSocket broadcasting, just return the created post
            log.info("✅ Successfully created new post {} by user {}",
                    result.getId(), result.getAuthor().getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Post created successfully");
            response.put("data", result);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error creating post: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get newsfeed with pagination - Updated to show newest posts first (sorted by upload time)
     * GET /api/posts/feed
     */
    @GetMapping("/feed")
    @Operation(summary = "Get newsfeed", description = "Get personalized newsfeed with posts from friends and public posts (sorted by newest first)")
    public ResponseEntity<Map<String, Object>> getNewsfeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            log.info("📰 Getting newsfeed sorted by newest first - page: {}, size: {}", page, size);

            Page<PostResponseDto> posts = postService.getNewsfeed(page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Newsfeed retrieved successfully (sorted by newest first)");
            response.put("data", posts.getContent());
            response.put("pagination", Map.of(
                    "currentPage", posts.getNumber(),
                    "totalPages", posts.getTotalPages(),
                    "totalElements", posts.getTotalElements(),
                    "hasNext", posts.hasNext(),
                    "hasPrevious", posts.hasPrevious()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting newsfeed: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Like/Unlike a post (SIMPLIFIED - No WebSocket)
     * POST /api/posts/{postId}/like
     */
    @PostMapping("/{postId}/like")
    @Operation(summary = "Toggle like", description = "Like or unlike a post")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable Long postId,
            org.springframework.security.core.Authentication authentication) {
        try {
            log.info("👍 Toggling like for post: {}", postId);

            PostResponseDto result = postService.toggleLike(postId);

            // Get current user information
            String currentUserEmail = authentication.getName();

            // Get current user details for notification
            User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

            // Only send notification if post was liked (not unliked)
            if (result.getIsLikedByCurrentUser() != null && result.getIsLikedByCurrentUser()) {
                // Get post owner ID from the result
                Long postOwnerId = result.getAuthor().getId();

                // Don't send notification if user is liking their own post
                if (!currentUserEmail.equals(result.getAuthor().getEmail())) {

                    // Get current user's display name
                    String currentUserDisplayName = getCurrentUserDisplayName(currentUser);

                    // Get post content preview (first 50 characters)
                    String postContentPreview = getPostContentPreview(result.getContent());

                    // Create enhanced notification title and content
                    String notificationTitle = String.format("💖 %s thích bài viết của bạn", currentUserDisplayName);
                    String notificationContent = String.format("%s đã thích bài viết: \"%s\"",
                        currentUserDisplayName, postContentPreview);

                    // Create notification for post owner
                    com.example.taskmanagement_backend.dtos.NotificationDto.CreateNotificationRequestDto notificationRequest =
                        com.example.taskmanagement_backend.dtos.NotificationDto.CreateNotificationRequestDto.builder()
                            .userId(postOwnerId)
                            .title(notificationTitle)
                            .content(notificationContent)
                            .type(com.example.taskmanagement_backend.enums.NotificationType.POST_LIKE)
                            .referenceId(postId)
                            .referenceType("POST_LIKE")
                            .senderName(currentUserDisplayName)
                            .avatarUrl(getCurrentUserAvatarUrl(currentUser))
                            .actionUrl("/posts/" + postId) // URL to view the post
                            .metadata(Map.of(
                                "postId", postId.toString(),
                                "likedBy", currentUserEmail,
                                "likedByName", currentUserDisplayName,
                                "postContentPreview", postContentPreview,
                                "authorName", result.getAuthor().getFirstName() + " " + result.getAuthor().getLastName()
                            ))
                            .build();

                    // Send notification asynchronously
                    notificationService.createAndSendNotification(notificationRequest);

                    log.info("✅ Sent enhanced notification to user {} about new like from {} on their post",
                        postOwnerId, currentUserDisplayName);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Like toggled successfully");
            response.put("data", result);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error toggling like: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Add comment to a post (SIMPLIFIED - No WebSocket)
     * POST /api/posts/{postId}/comment
     */
    @PostMapping("/{postId}/comment")
    @Operation(summary = "Add comment", description = "Add a comment to a post")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable Long postId,
            @RequestBody CreateCommentRequestDto requestDto,
            org.springframework.security.core.Authentication authentication) {

        try {
            log.info("💬 Adding comment to post: {}", postId);

            PostCommentDto result = postService.addComment(postId, requestDto);

            // Get current user information
            String currentUserEmail = authentication.getName();

            // Get current user details for notification
            User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

            // Get post details to identify the post owner
            PostResponseDto post = postService.getPostById(postId);

            // Don't send notification if user is commenting on their own post
            if (post != null && post.getAuthor() != null &&
                !currentUserEmail.equals(post.getAuthor().getEmail())) {

                // Get current user's display name
                String currentUserDisplayName = getCurrentUserDisplayName(currentUser);

                // Get post content preview (first 50 characters)
                String postContentPreview = getPostContentPreview(post.getContent());

                // Get comment content preview
                String commentContentPreview = getPostContentPreview(requestDto.getContent());

                // Create enhanced notification title and content
                String notificationTitle = String.format("💬 %s đã bình luận bài viết của bạn", currentUserDisplayName);
                String notificationContent = String.format("%s đã bình luận: \"%s\" trên bài viết: \"%s\"",
                    currentUserDisplayName, commentContentPreview, postContentPreview);

                // Create notification for post owner
                com.example.taskmanagement_backend.dtos.NotificationDto.CreateNotificationRequestDto notificationRequest =
                    com.example.taskmanagement_backend.dtos.NotificationDto.CreateNotificationRequestDto.builder()
                        .userId(post.getAuthor().getId())
                        .title(notificationTitle)
                        .content(notificationContent)
                        .type(com.example.taskmanagement_backend.enums.NotificationType.POST_COMMENT)
                        .referenceId(postId)
                        .referenceType("POST_COMMENT")
                        .senderName(currentUserDisplayName)
                        .avatarUrl(getCurrentUserAvatarUrl(currentUser))
                        .actionUrl("/posts/" + postId) // URL to view the post
                        .metadata(Map.of(
                            "postId", postId.toString(),
                            "commentId", result.getId().toString(),
                            "commentedBy", currentUserEmail,
                            "commentedByName", currentUserDisplayName,
                            "commentContent", commentContentPreview,
                            "postContentPreview", postContentPreview,
                            "authorName", post.getAuthor().getFirstName() + " " + post.getAuthor().getLastName()
                        ))
                        .build();

                // Send notification asynchronously
                notificationService.createAndSendNotification(notificationRequest);

                log.info("✅ Sent enhanced notification to user {} about new comment from {} on their post",
                    post.getAuthor().getId(), currentUserDisplayName);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Comment added successfully");
            response.put("data", result);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error adding comment: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get comments for a post
     * GET /api/posts/{postId}/comments
     */
    @GetMapping("/{postId}/comments")
    @Operation(summary = "Get comments", description = "Get comments for a post with pagination")
    public ResponseEntity<Map<String, Object>> getPostComments(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            log.info("💬 Getting comments for post: {} - page: {}, size: {}", postId, page, size);

            Page<PostCommentDto> comments = postService.getPostComments(postId, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Comments retrieved successfully");
            response.put("data", comments.getContent());
            response.put("pagination", Map.of(
                    "currentPage", comments.getNumber(),
                    "totalPages", comments.getTotalPages(),
                    "totalElements", comments.getTotalElements(),
                    "hasNext", comments.hasNext(),
                    "hasPrevious", comments.hasPrevious()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting comments: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get posts by user (for profile view) - Updated to support multiple images/files
     * GET /api/posts/user/{userId}
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user posts", description = "Get posts by specific user with multiple images/files support (respects privacy settings)")
    public ResponseEntity<Map<String, Object>> getUserPosts(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            log.info("👤 Getting posts with multiple files support for user: {} - page: {}, size: {}", userId, page, size);

            Page<PostResponseDto> posts = postService.getUserPosts(userId, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User posts retrieved successfully with multiple files support");
            response.put("data", posts.getContent());
            response.put("pagination", Map.of(
                    "currentPage", posts.getNumber(),
                    "totalPages", posts.getTotalPages(),
                    "totalElements", posts.getTotalElements(),
                    "hasNext", posts.hasNext(),
                    "hasPrevious", posts.hasPrevious()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting user posts: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get single post details - Updated to support multiple images/files
     * GET /api/posts/{postId}
     */
    @GetMapping("/{postId}")
    @Operation(summary = "Get post details", description = "Get detailed information about a specific post with multiple images/files support")
    public ResponseEntity<Map<String, Object>> getPost(@PathVariable Long postId) {
        try {
            log.info("📄 Getting post details with multiple files support: {}", postId);

            PostResponseDto post = postService.getPostById(postId);

            if (post == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Post not found");
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Post retrieved successfully with multiple files support");
            response.put("data", post);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting post: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * ✅ NEW: Like/Unlike a comment (Facebook-style)
     * POST /api/posts/comments/{commentId}/like
     */
    @PostMapping("/comments/{commentId}/like")
    @Operation(summary = "Like/Unlike comment", description = "Toggle like on a comment (Facebook-style)")
    public ResponseEntity<Map<String, Object>> toggleCommentLike(@PathVariable Long commentId) {
        try {
            log.info("👍 Toggling like for comment: {}", commentId);

            PostCommentDto result = postService.toggleCommentLike(commentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Comment like toggled successfully");
            response.put("data", result);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error toggling comment like: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * ✅ NEW: Get replies for a comment (Facebook-style)
     * GET /api/posts/comments/{commentId}/replies
     */
    @GetMapping("/comments/{commentId}/replies")
    @Operation(summary = "Get comment replies", description = "Get replies for a specific comment with pagination")
    public ResponseEntity<Map<String, Object>> getCommentReplies(
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            log.info("💬 Getting replies for comment: {} - page: {}, size: {}", commentId, page, size);

            Page<PostCommentDto> replies = postService.getCommentReplies(commentId, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Comment replies retrieved successfully");
            response.put("data", replies.getContent());
            response.put("pagination", Map.of(
                    "currentPage", replies.getNumber(),
                    "totalPages", replies.getTotalPages(),
                    "totalElements", replies.getTotalElements(),
                    "hasNext", replies.hasNext(),
                    "hasPrevious", replies.hasPrevious()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting comment replies: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * ✅ NEW: Get people who liked a comment (Facebook-style)
     * GET /api/posts/comments/{commentId}/likes
     */
    @GetMapping("/comments/{commentId}/likes")
    @Operation(summary = "Get comment likes", description = "Get people who liked a comment")
    public ResponseEntity<Map<String, Object>> getCommentLikes(
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            log.info("👍 Getting likes for comment: {} - limit: {}", commentId, limit);

            List<PostCommentDto.RecentLikeDto> likes = postService.getCommentLikes(commentId, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Comment likes retrieved successfully");
            response.put("data", likes);
            response.put("totalCount", likes.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting comment likes: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * ✅ NEW: Get trending posts (high engagement in last 24 hours)
     * GET /api/posts/trending
     */
    @GetMapping("/trending")
    @Operation(summary = "Get trending posts", description = "Get posts with high engagement in the last 24 hours (supports multiple images/files)")
    public ResponseEntity<Map<String, Object>> getTrendingPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            log.info("🔥 Getting trending posts with multiple files support - page: {}, size: {}", page, size);

            Page<PostResponseDto> posts = postService.getTrendingPosts(page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Trending posts retrieved successfully with multiple files support");
            response.put("data", posts.getContent());
            response.put("pagination", Map.of(
                    "currentPage", posts.getNumber(),
                    "totalPages", posts.getTotalPages(),
                    "totalElements", posts.getTotalElements(),
                    "hasNext", posts.hasNext(),
                    "hasPrevious", posts.hasPrevious()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting trending posts: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * ✅ NEW: Get newsfeed with pinned posts prioritized
     * GET /api/posts/feed/pinned
     */
    @GetMapping("/feed/pinned")
    @Operation(summary = "Get newsfeed with pinned posts", description = "Get newsfeed with pinned posts shown first")
    public ResponseEntity<Map<String, Object>> getNewsfeedWithPinned(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            log.info("📌 Getting newsfeed with pinned posts - page: {}, size: {}", page, size);

            Page<PostResponseDto> posts = postService.getNewsfeedWithPinned(page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Newsfeed with pinned posts retrieved successfully");
            response.put("data", posts.getContent());
            response.put("pagination", Map.of(
                    "currentPage", posts.getNumber(),
                    "totalPages", posts.getTotalPages(),
                    "totalElements", posts.getTotalElements(),
                    "hasNext", posts.hasNext(),
                    "hasPrevious", posts.hasPrevious()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting newsfeed with pinned posts: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * ✅ NEW: Delete a post (Only post author or ADMIN can delete)
     * DELETE /api/posts/{postId}
     */
    @DeleteMapping("/{postId}")
    @Operation(summary = "Delete post", description = "Delete a post and all related data (only post author or system admin can delete)")
    public ResponseEntity<Map<String, Object>> deletePost(
            @PathVariable Long postId,
            org.springframework.security.core.Authentication authentication) {

        try {
            log.info("🗑️ Attempting to delete post: {} by user: {}", postId, authentication.getName());

            // Call service to delete post with all cascade operations
            postService.deletePost(postId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Post and all related data deleted successfully");

            log.info("✅ Successfully deleted post: {}", postId);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ Error deleting post {}: {}", postId, e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).body(response); // Forbidden
            } else if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(404).body(response); // Not Found
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("❌ Unexpected error deleting post {}: {}", postId, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "An unexpected error occurred while deleting the post");

            return ResponseEntity.status(500).body(response); // Internal Server Error
        }
    }

    /**
     * ✅ NEW: Edit/Update a post (Only post author can edit)
     * PUT /api/posts/{postId}
     */
    @PutMapping(value = "/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Edit post", description = "Edit/update a post content, privacy, and files (only post author can edit)")
    public ResponseEntity<Map<String, Object>> editPost(
            @PathVariable Long postId,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "privacy", required = false) String privacy,
            @RequestParam(value = "linkedTaskId", required = false) Long linkedTaskId,
            @RequestParam(value = "linkedProjectId", required = false) Long linkedProjectId,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "isPinned", required = false) Boolean isPinned,
            @RequestParam(value = "removeImageIds", required = false) List<Long> removeImageIds,
            @RequestParam(value = "removeFileIds", required = false) List<Long> removeFileIds,
            org.springframework.security.core.Authentication authentication) {

        try {
            log.info("✏️ Attempting to edit post: {} by user: {}", postId, authentication.getName());

            // Build update request DTO
            UpdatePostRequestDto requestDto = UpdatePostRequestDto.builder()
                    .content(content)
                    .privacy(privacy != null ? com.example.taskmanagement_backend.enums.PostPrivacy.valueOf(privacy.toUpperCase()) : null)
                    .linkedTaskId(linkedTaskId)
                    .linkedProjectId(linkedProjectId)
                    .image(image)
                    .images(images)
                    .files(files)
                    .isPinned(isPinned)
                    .removeImageIds(removeImageIds)
                    .removeFileIds(removeFileIds)
                    .build();

            // Call service to update post
            PostResponseDto result = postService.updatePost(postId, requestDto);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Post updated successfully");
            response.put("data", result);

            log.info("✅ Successfully updated post: {}", postId);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ Error updating post {}: {}", postId, e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).body(response); // Forbidden
            } else if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(404).body(response); // Not Found
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("❌ Unexpected error updating post {}: {}", postId, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "An unexpected error occurred while updating the post");

            return ResponseEntity.status(500).body(response); // Internal Server Error
        }
    }

    /**
     * ✅ NEW: Edit post with JSON payload (no file upload)
     * PATCH /api/posts/{postId}
     */
    @PatchMapping(value = "/{postId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Edit post (JSON)", description = "Edit/update post content, privacy and settings without file upload (only post author can edit)")
    public ResponseEntity<Map<String, Object>> editPostJson(
            @PathVariable Long postId,
            @RequestBody UpdatePostJsonRequestDto requestDto,
            org.springframework.security.core.Authentication authentication) {

        try {
            log.info("✏️ Attempting to edit post (JSON): {} by user: {}", postId, authentication.getName());

            // Convert JSON DTO to regular DTO
            UpdatePostRequestDto updateDto = UpdatePostRequestDto.builder()
                    .content(requestDto.getContent())
                    .privacy(requestDto.getPrivacy())
                    .linkedTaskId(requestDto.getLinkedTaskId())
                    .linkedProjectId(requestDto.getLinkedProjectId())
                    .isPinned(requestDto.getIsPinned())
                    .removeImageIds(requestDto.getRemoveImageIds())
                    .removeFileIds(requestDto.getRemoveFileIds())
                    .build();

            // Call service to update post
            PostResponseDto result = postService.updatePost(postId, updateDto);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Post updated successfully");
            response.put("data", result);

            log.info("✅ Successfully updated post (JSON): {}", postId);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ Error updating post (JSON) {}: {}", postId, e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).body(response); // Forbidden
            } else if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(404).body(response); // Not Found
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("❌ Unexpected error updating post (JSON) {}: {}", postId, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "An unexpected error occurred while updating the post");

            return ResponseEntity.status(500).body(response); // Internal Server Error
        }
    }

    /**
     * 🆕 Helper method to get user's display name
     */
    private String getCurrentUserDisplayName(User user) {
        if (user.getUserProfile() != null) {
            String firstName = user.getUserProfile().getFirstName();
            String lastName = user.getUserProfile().getLastName();

            if (firstName != null && lastName != null) {
                return firstName + " " + lastName;
            } else if (firstName != null) {
                return firstName;
            } else if (lastName != null) {
                return lastName;
            } else if (user.getUserProfile().getUsername() != null) {
                return user.getUserProfile().getUsername();
            }
        }

        // Fallback to email prefix if no profile info
        return user.getEmail().split("@")[0];
    }

    /**
     * 🆕 Helper method to get user's avatar URL
     */
    private String getCurrentUserAvatarUrl(User user) {
        // Use getAvatarUrl() which handles URL conversion instead of directly accessing the raw S3 key
        return user.getAvatarUrl();
    }

    /**
     * 🆕 Helper method to get post content preview
     */
    private String getPostContentPreview(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "bài viết không có nội dung";
        }

        String cleanContent = content.trim();
        if (cleanContent.length() <= 50) {
            return cleanContent;
        }

        return cleanContent.substring(0, 47) + "...";
    }
}
