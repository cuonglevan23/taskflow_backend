package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.FileUploadDto.FileUploadResponseDto;
import com.example.taskmanagement_backend.dtos.PostDto.*;
import com.example.taskmanagement_backend.entities.*;
import com.example.taskmanagement_backend.enums.FriendshipStatus;
import com.example.taskmanagement_backend.enums.PostPrivacy;
import com.example.taskmanagement_backend.repositories.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostCommentLikeRepository postCommentLikeRepository; // ✅ NEW: For comment likes
    private final PostAttachmentRepository postAttachmentRepository; // ✅ NEW: Add PostAttachment repository
    private final UserJpaRepository userRepository;
    private final FriendRepository friendRepository;
    private final TaskJpaRepository taskRepository;
    private final ProjectJpaRepository projectRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final S3FileUploadService s3FileUploadService;

    // Redis cache keys
    private static final String NEWSFEED_CACHE_PREFIX = "feed:user:";
    private static final String USER_FRIENDS_CACHE_PREFIX = "friends:user:";
    private static final long CACHE_TTL_MINUTES = 5; // 5 minutes TTL for newsfeed
    private static final long FRIENDS_CACHE_TTL_HOURS = 1; // 1 hour TTL for friends list

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    /**
     * ✅ UPDATED: Create a new post with multiple images and files using PostAttachment
     */
    @Transactional
    public PostResponseDto createPost(CreatePostRequestDto requestDto) {
        User currentUser = getCurrentUser();

        log.info("📝 User {} creating new post with multiple files support", currentUser.getEmail());

        // Validate linked task if provided
        Task linkedTask = null;
        if (requestDto.getLinkedTaskId() != null) {
            linkedTask = taskRepository.findById(requestDto.getLinkedTaskId())
                    .orElseThrow(() -> new RuntimeException("Linked task not found"));
        }

        // Validate linked project if provided
        Project linkedProject = null;
        if (requestDto.getLinkedProjectId() != null) {
            linkedProject = projectRepository.findById(requestDto.getLinkedProjectId())
                    .orElseThrow(() -> new RuntimeException("Linked project not found"));
        }

        // ✅ NEW: Handle single image (legacy support) - store in Post entity for backward compatibility
        String imageUrl = null;
        String imageS3Key = null;
        if (requestDto.getImage() != null && !requestDto.getImage().isEmpty()) {
            try {
                String fileName = requestDto.getImage().getOriginalFilename();
                String contentType = requestDto.getImage().getContentType();

                CompletableFuture<FileUploadResponseDto> uploadFuture = s3FileUploadService.uploadFileAsync(
                    fileName,
                    contentType,
                    requestDto.getImage().getInputStream(),
                    requestDto.getImage().getSize(),
                    null
                );

                FileUploadResponseDto uploadResult = uploadFuture.get();
                imageS3Key = uploadResult.getFileKey();
                imageUrl = uploadResult.getDownloadUrl();

                log.info("📸 Single image uploaded to S3: {}", imageS3Key);
            } catch (Exception e) {
                log.error("❌ Failed to upload single image: {}", e.getMessage());
                throw new RuntimeException("Failed to upload image: " + e.getMessage());
            }
        }

        // Create post entity first
        Post post = Post.builder()
                .author(currentUser)
                .content(requestDto.getContent())
                .privacy(requestDto.getPrivacy())
                .imageUrl(imageUrl) // Keep for backward compatibility
                .imageS3Key(imageS3Key) // Keep for backward compatibility
                .linkedTask(linkedTask)
                .linkedProject(linkedProject)
                .isPinned(requestDto.getIsPinned())
                .build();

        Post savedPost = postRepository.save(post);

        // ✅ NEW: Handle multiple images using PostAttachment
        List<PostAttachment> attachments = new ArrayList<>();
        int totalImages = 0;
        int totalFiles = 0;

        // Process multiple images
        if (requestDto.getImages() != null && !requestDto.getImages().isEmpty()) {
            for (MultipartFile imageFile : requestDto.getImages()) {
                if (imageFile != null && !imageFile.isEmpty()) {
                    try {
                        String fileName = imageFile.getOriginalFilename();
                        String contentType = imageFile.getContentType();

                        CompletableFuture<FileUploadResponseDto> uploadFuture = s3FileUploadService.uploadFileAsync(
                            fileName,
                            contentType,
                            imageFile.getInputStream(),
                            imageFile.getSize(),
                            null
                        );

                        FileUploadResponseDto uploadResult = uploadFuture.get();

                        // Create PostAttachment for this image
                        PostAttachment imageAttachment = PostAttachment.builder()
                                .post(savedPost)
                                .originalFilename(fileName)
                                .s3Key(uploadResult.getFileKey())
                                .s3Url(uploadResult.getDownloadUrl())
                                .fileSize(imageFile.getSize())
                                .contentType(contentType)
                                .attachmentType(PostAttachment.AttachmentType.IMAGE)
                                .build();

                        attachments.add(imageAttachment);
                        totalImages++;

                        log.info("📸 Image {} uploaded to S3: {}", fileName, uploadResult.getFileKey());
                    } catch (Exception e) {
                        log.error("❌ Failed to upload image {}: {}", imageFile.getOriginalFilename(), e.getMessage());
                        throw new RuntimeException("Failed to upload image " + imageFile.getOriginalFilename() + ": " + e.getMessage());
                    }
                }
            }
        }

        // Process multiple files (documents, videos, etc.)
        if (requestDto.getFiles() != null && !requestDto.getFiles().isEmpty()) {
            for (MultipartFile file : requestDto.getFiles()) {
                if (file != null && !file.isEmpty()) {
                    try {
                        String fileName = file.getOriginalFilename();
                        String contentType = file.getContentType();

                        CompletableFuture<FileUploadResponseDto> uploadFuture = s3FileUploadService.uploadFileAsync(
                            fileName,
                            contentType,
                            file.getInputStream(),
                            file.getSize(),
                            null
                        );

                        FileUploadResponseDto uploadResult = uploadFuture.get();

                        // Determine attachment type based on content type
                        PostAttachment.AttachmentType attachmentType = determineAttachmentType(contentType);

                        // Create PostAttachment for this file
                        PostAttachment fileAttachment = PostAttachment.builder()
                                .post(savedPost)
                                .originalFilename(fileName)
                                .s3Key(uploadResult.getFileKey())
                                .s3Url(uploadResult.getDownloadUrl())
                                .fileSize(file.getSize())
                                .contentType(contentType)
                                .attachmentType(attachmentType)
                                .build();

                        attachments.add(fileAttachment);
                        totalFiles++;

                        log.info("📎 File {} uploaded to S3: {}", fileName, uploadResult.getFileKey());
                    } catch (Exception e) {
                        log.error("❌ Failed to upload file {}: {}", file.getOriginalFilename(), e.getMessage());
                        throw new RuntimeException("Failed to upload file " + file.getOriginalFilename() + ": " + e.getMessage());
                    }
                }
            }
        }

        // Save all attachments to database
        if (!attachments.isEmpty()) {
            postAttachmentRepository.saveAll(attachments);
            log.info("💾 Saved {} attachments to database for post {}", attachments.size(), savedPost.getId());
        }

        // Clear newsfeed cache for current user and friends
        clearNewsfeedCacheForUserAndFriends(currentUser.getId());

        log.info("✅ Post created successfully with ID: {}, {} images, {} files",
                savedPost.getId(), totalImages, totalFiles);

        // Return the response with attachment information
        PostResponseDto result = mapToPostResponseDto(savedPost, currentUser.getId());

        // Log response details for debugging
        log.info("🔄 Response contains: imageUrl={}, imageUrls={}, images={}, files={}",
                result.getImageUrl() != null,
                result.getImageUrls() != null ? result.getImageUrls().size() : 0,
                totalImages,
                totalFiles);

        return result;
    }

    /**
     * ✅ NEW: Helper method to determine attachment type based on content type
     */
    private PostAttachment.AttachmentType determineAttachmentType(String contentType) {
        if (contentType == null) {
            return PostAttachment.AttachmentType.OTHER;
        }

        if (contentType.startsWith("image/")) {
            return PostAttachment.AttachmentType.IMAGE;
        } else if (contentType.startsWith("video/")) {
            return PostAttachment.AttachmentType.VIDEO;
        } else if (contentType.equals("application/pdf") ||
                   contentType.startsWith("application/msword") ||
                   contentType.startsWith("application/vnd.openxmlformats-officedocument") ||
                   contentType.equals("text/plain") ||
                   contentType.equals("application/rtf")) {
            return PostAttachment.AttachmentType.DOCUMENT;
        } else {
            return PostAttachment.AttachmentType.OTHER;
        }
    }

    /**
     * Get newsfeed with Redis caching
     */
    /**
     * ✅ UPDATED: Get personalized newsfeed sorted by newest posts first (upload time)
     */
    public Page<PostResponseDto> getNewsfeed(int page, int size) {
        User currentUser = getCurrentUser();
        String cacheKey = NEWSFEED_CACHE_PREFIX + currentUser.getId() + ":" + page + ":" + size;

        log.info("📰 Getting newsfeed (newest first) for user: {} (page: {}, size: {})",
                currentUser.getEmail(), page, size);

        // Try to get from cache first
        try {
            String cachedFeed = redisTemplate.opsForValue().get(cacheKey);
            if (cachedFeed != null) {
                log.info("🎯 Newsfeed cache hit for user: {}", currentUser.getId());
                TypeReference<List<PostResponseDto>> typeRef = new TypeReference<List<PostResponseDto>>() {};
                List<PostResponseDto> posts = objectMapper.readValue(cachedFeed, typeRef);
                return convertListToPage(posts, PageRequest.of(page, size));
            }
        } catch (JsonProcessingException e) {
            log.warn("⚠️ Failed to parse cached newsfeed: {}", e.getMessage());
        }

        // Get from database if not in cache - use time-based sorting (newest first)
        List<Long> friendIds = getFriendIds(currentUser.getId());
        Pageable pageable = PageRequest.of(page, size);

        // ✅ UPDATED: Use time-based newsfeed to show newest posts first
        Page<Post> posts = postRepository.findNewsfeedPosts(
                currentUser.getId(), friendIds, pageable);

        List<PostResponseDto> postDtos = posts.getContent().stream()
                .map(post -> mapToPostResponseDto(post, currentUser.getId()))
                .collect(Collectors.toList());

        // Cache the result
        try {
            String jsonPosts = objectMapper.writeValueAsString(postDtos);
            redisTemplate.opsForValue().set(cacheKey, jsonPosts,
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.info("💾 Newsfeed (newest first) cached for user: {}", currentUser.getId());
        } catch (JsonProcessingException e) {
            log.warn("⚠️ Failed to cache newsfeed: {}", e.getMessage());
        }

        return convertListToPage(postDtos, pageable);
    }

    /**
     * ✅ NEW: Get trending posts (high engagement in last 24 hours)
     */
    public Page<PostResponseDto> getTrendingPosts(int page, int size) {
        User currentUser = getCurrentUser();
        List<Long> friendIds = getFriendIds(currentUser.getId());
        Pageable pageable = PageRequest.of(page, size);

        LocalDateTime since = LocalDateTime.now().minusHours(24);

        Page<Post> trendingPosts = postRepository.findTrendingPosts(
                currentUser.getId(), friendIds, since, pageable);

        return trendingPosts.map(post -> mapToPostResponseDto(post, currentUser.getId()));
    }

    /**
     * ✅ NEW: Get newsfeed with pinned posts first
     */
    public Page<PostResponseDto> getNewsfeedWithPinned(int page, int size) {
        User currentUser = getCurrentUser();
        List<Long> friendIds = getFriendIds(currentUser.getId());
        Pageable pageable = PageRequest.of(page, size);

        Page<Post> posts = postRepository.findNewsfeedWithPinnedFirst(
                currentUser.getId(), friendIds, pageable);

        return posts.map(post -> mapToPostResponseDto(post, currentUser.getId()));
    }

    /**
     * Like/Unlike a post
     */
    @Transactional
    public PostResponseDto toggleLike(Long postId) {
        User currentUser = getCurrentUser();

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        Optional<PostLike> existingLike = postLikeRepository.findByPostAndUser(post, currentUser);

        if (existingLike.isPresent()) {
            // Unlike
            postLikeRepository.delete(existingLike.get());
            log.info("👎 User {} unliked post {}", currentUser.getEmail(), postId);
        } else {
            // Like
            PostLike like = PostLike.builder()
                    .post(post)
                    .user(currentUser)
                    .build();
            postLikeRepository.save(like);
            log.info("👍 User {} liked post {}", currentUser.getEmail(), postId);
        }

        // Cập nhật likeCount thực tế từ database
        long actualLikeCount = postLikeRepository.countByPost(post);
        post.setLikeCount((int) actualLikeCount);
        postRepository.save(post);

        // Clear cache for post author and their friends
        clearNewsfeedCacheForUserAndFriends(post.getAuthor().getId());

        return mapToPostResponseDto(post, currentUser.getId());
    }

    /**
     * Add comment to a post
     */
    @Transactional
    public PostCommentDto addComment(Long postId, CreateCommentRequestDto requestDto) {
        User currentUser = getCurrentUser();

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Handle parent comment for replies
        PostComment parentComment = null;
        if (requestDto.getParentCommentId() != null) {
            parentComment = postCommentRepository.findById(requestDto.getParentCommentId())
                    .orElseThrow(() -> new RuntimeException("Parent comment not found"));
        }

        PostComment comment = PostComment.builder()
                .post(post)
                .user(currentUser)
                .content(requestDto.getContent())
                .parentComment(parentComment)
                .build();

        PostComment savedComment = postCommentRepository.save(comment);

        // Update post comment count (only for top-level comments)
        if (parentComment == null) {
            post.incrementCommentCount();
            postRepository.save(post);
        }

        // Clear cache
        clearNewsfeedCacheForUserAndFriends(post.getAuthor().getId());

        log.info("💬 User {} commented on post {}", currentUser.getEmail(), postId);

        return mapToPostCommentDto(savedComment, currentUser.getId());
    }

    /**
     * Get comments for a post with pagination
     */
    public Page<PostCommentDto> getPostComments(Long postId, int page, int size) {
        User currentUser = getCurrentUser();

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<PostComment> comments = postCommentRepository
                .findByPostAndParentCommentIsNullOrderByCreatedAtAsc(post, pageable);

        return comments.map(comment -> mapToPostCommentDto(comment, currentUser.getId()));
    }

    /**
     * Get user's own posts
     */
    public Page<PostResponseDto> getUserPosts(Long userId, int page, int size) {
        User currentUser = getCurrentUser();
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<Post> posts = postRepository.findVisiblePostsByAuthor(
                userId, currentUser.getId(), pageable);

        return posts.map(post -> mapToPostResponseDto(post, currentUser.getId()));
    }

    /**
     * Public method to convert Post entity to PostResponseDto
     * Used by other services that need to convert posts
     */
    public PostResponseDto convertToPostResponseDto(Post post, Long viewerId) {
        return mapToPostResponseDto(post, viewerId);
    }

    /**
     * ✅ NEW: Like/Unlike a comment (Facebook-style)
     */
    @Transactional
    public PostCommentDto toggleCommentLike(Long commentId) {
        User currentUser = getCurrentUser();

        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        Optional<PostCommentLike> existingLike = postCommentLikeRepository.findByCommentAndUser(comment, currentUser);

        if (existingLike.isPresent()) {
            // Unlike comment
            postCommentLikeRepository.delete(existingLike.get());
            log.info("👎 User {} unliked comment {}", currentUser.getEmail(), commentId);
        } else {
            // Like comment
            PostCommentLike like = PostCommentLike.builder()
                    .comment(comment)
                    .user(currentUser)
                    .build();
            postCommentLikeRepository.save(like);
            log.info("👍 User {} liked comment {}", currentUser.getEmail(), commentId);
        }

        // Update comment like count from actual database count
        long actualLikeCount = postCommentLikeRepository.countByComment(comment);
        comment.setLikeCount((int) actualLikeCount);
        postCommentRepository.save(comment);

        // Clear cache for post author and their friends
        clearNewsfeedCacheForUserAndFriends(comment.getPost().getAuthor().getId());

        return mapToPostCommentDto(comment, currentUser.getId());
    }

    /**
     * ✅ NEW: Get replies for a specific comment (Facebook-style)
     */
    public Page<PostCommentDto> getCommentReplies(Long commentId, int page, int size) {
        User currentUser = getCurrentUser();

        PostComment parentComment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<PostComment> replies = postCommentRepository
                .findRepliesByParentComment(parentComment, pageable);

        return replies.map(reply -> mapToPostCommentDto(reply, currentUser.getId()));
    }

    /**
     * ✅ NEW: Get people who liked a comment (Facebook-style)
     */
    public List<PostCommentDto.RecentLikeDto> getCommentLikes(Long commentId, int limit) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        List<PostCommentLike> recentLikes = postCommentLikeRepository
                .findRecentLikesByCommentId(commentId)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());

        return recentLikes.stream()
                .map(like -> {
                    UserProfile likerProfile = like.getUser().getUserProfile();
                    return PostCommentDto.RecentLikeDto.builder()
                            .userId(like.getUser().getId())
                            .username(likerProfile != null ? likerProfile.getUsername() : like.getUser().getEmail())
                            .firstName(likerProfile != null ? likerProfile.getFirstName() : "")
                            .lastName(likerProfile != null ? likerProfile.getLastName() : "")
                            .avatarUrl(likerProfile != null ? likerProfile.getAvtUrl() : null)
                            .likedAt(like.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Get a single post by ID with author details and like/comment counts
     * @param postId The ID of the post to retrieve
     * @return PostResponseDto containing post details or null if not found
     */
    public PostResponseDto getPostById(Long postId) {
        log.info("📄 Getting post by ID: {}", postId);

        Optional<Post> postOptional = postRepository.findById(postId);
        if (postOptional.isEmpty()) {
            log.warn("❌ Post not found with ID: {}", postId);
            return null;
        }

        Post post = postOptional.get();
        User currentUser = getCurrentUser();

        // Check if current user liked this post
        boolean isLiked = postLikeRepository.findByPostIdAndUserId(postId, currentUser.getId()).isPresent();

        // Get like count
        long likeCount = postLikeRepository.countByPost(post);

        // Get comment count
        long commentCount = postCommentRepository.countByPostAndParentCommentIsNull(post);

        // Get recent likes (limited to 5)
        List<PostLike> recentLikes = postLikeRepository.findByPostOrderByCreatedAtDesc(post)
                .stream().limit(5).collect(Collectors.toList());

        List<PostResponseDto.RecentLikeDto> recentLikeDtos = recentLikes.stream()
                .map(like -> {
                    UserProfile likerProfile = like.getUser().getUserProfile();
                    return PostResponseDto.RecentLikeDto.builder()
                            .userId(like.getUser().getId())
                            .username(likerProfile != null ? likerProfile.getUsername() : like.getUser().getEmail())
                            .avatarUrl(likerProfile != null ? likerProfile.getAvtUrl() : null)
                            .likedAt(like.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        // Get top comments (limited to 3)
        List<PostComment> topComments = postCommentRepository
                .findByPostAndParentCommentIsNullOrderByCreatedAtAsc(post, PageRequest.of(0, 3))
                .getContent();

        List<PostCommentDto> topCommentDtos = topComments.stream()
                .map(comment -> mapToPostCommentDto(comment, currentUser.getId()))
                .collect(Collectors.toList());

        // Get attachments for this post
        List<PostAttachment> attachments = postAttachmentRepository.findByPostOrderByCreatedAtAsc(post);

        // Separate images and files
        List<String> imageUrls = new ArrayList<>();
        List<PostResponseDto.AttachmentDto> files = new ArrayList<>();

        for (PostAttachment attachment : attachments) {
            if (attachment.getAttachmentType() == PostAttachment.AttachmentType.IMAGE) {
                imageUrls.add(attachment.getS3Url());
            } else {
                files.add(PostResponseDto.AttachmentDto.builder()
                        .id(attachment.getId())
                        .originalFilename(attachment.getOriginalFilename())
                        .s3Url(attachment.getS3Url())
                        .fileSize(attachment.getFileSize())
                        .contentType(attachment.getContentType())
                        .attachmentType(attachment.getAttachmentType().toString())
                        .uploadedAt(attachment.getCreatedAt())
                        .build());
            }
        }

        // Build the response DTO
        return PostResponseDto.builder()
                .id(post.getId())
                .content(post.getContent())
                .privacy(post.getPrivacy())
                .imageUrl(post.getImageUrl()) // Legacy single image
                .imageUrls(imageUrls) // Multiple images from attachments
                .files(files) // Non-image attachments
                .author(PostResponseDto.AuthorDto.builder()
                        .id(post.getAuthor().getId())
                        .email(post.getAuthor().getEmail())
                        .firstName(post.getAuthor().getFirstName())
                        .lastName(post.getAuthor().getLastName())
                        .username(post.getAuthor().getUserProfile() != null ?
                                  post.getAuthor().getUserProfile().getUsername() :
                                  post.getAuthor().getEmail())
                        .avatarUrl(post.getAuthor().getAvatarUrl())
                        .build())
                .linkedTask(post.getLinkedTask() != null ? PostResponseDto.LinkedTaskDto.builder()
                        .id(post.getLinkedTask().getId())
                        .title(post.getLinkedTask().getTitle())
                        .build() : null)
                .linkedProject(post.getLinkedProject() != null ? PostResponseDto.LinkedProjectDto.builder()
                        .id(post.getLinkedProject().getId())
                        .name(post.getLinkedProject().getName())
                        .build() : null)
                .likeCount((int) likeCount)
                .commentCount((int) commentCount)
                .isPinned(post.getIsPinned())
                .isLikedByCurrentUser(isLiked)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .recentLikes(recentLikeDtos)
                .topComments(topCommentDtos)
                .build();
    }

    /**
     * ✅ NEW: Delete a post with all related data (comments, likes, attachments, S3 files)
     * Only post author or ADMIN can delete
     */
    @Transactional
    public void deletePost(Long postId) {
        User currentUser = getCurrentUser();

        // Get the post
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Check authorization: only post author or ADMIN can delete
        boolean isAuthor = post.getAuthor().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getSystemRole() == com.example.taskmanagement_backend.enums.SystemRole.ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new RuntimeException("Access denied. Only the post author or system admin can delete this post.");
        }

        log.info("🗑️ User {} deleting post {} (author: {}, admin: {})",
                currentUser.getEmail(), postId, isAuthor, isAdmin);

        // 1. Delete all S3 files associated with this post
        List<PostAttachment> attachments = postAttachmentRepository.findByPostOrderByCreatedAtAsc(post);
        if (!attachments.isEmpty()) {
            for (PostAttachment attachment : attachments) {
                try {
                    // Delete from S3
                    s3FileUploadService.deleteFile(attachment.getS3Key());
                    log.info("🗑️ Deleted S3 file: {}", attachment.getS3Key());
                } catch (Exception e) {
                    log.warn("⚠️ Failed to delete S3 file {}: {}", attachment.getS3Key(), e.getMessage());
                    // Continue with deletion even if S3 deletion fails
                }
            }

            // Delete attachment records from database
            postAttachmentRepository.deleteByPost(post);
            log.info("🗑️ Deleted {} attachment records", attachments.size());
        }

        // 2. Delete legacy single image from S3 if exists
        if (post.getImageS3Key() != null && !post.getImageS3Key().isEmpty()) {
            try {
                s3FileUploadService.deleteFile(post.getImageS3Key());
                log.info("🗑️ Deleted legacy S3 image: {}", post.getImageS3Key());
            } catch (Exception e) {
                log.warn("⚠️ Failed to delete legacy S3 image {}: {}", post.getImageS3Key(), e.getMessage());
            }
        }

        // 3. Delete all comment likes first (foreign key constraint)
        List<PostComment> comments = postCommentRepository.findByPostOrderByCreatedAtAsc(post);
        for (PostComment comment : comments) {
            postCommentLikeRepository.deleteByComment(comment);
        }
        log.info("🗑️ Deleted comment likes for {} comments", comments.size());

        // 4. Delete all comments
        postCommentRepository.deleteByPost(post);
        log.info("🗑️ Deleted {} comments", comments.size());

        // 5. Delete all post likes
        postLikeRepository.deleteByPost(post);
        log.info("🗑️ Deleted post likes");

        // 6. Finally delete the post
        postRepository.delete(post);
        log.info("✅ Successfully deleted post {} and all related data", postId);

        // 7. Clear newsfeed cache for author and friends
        clearNewsfeedCacheForUserAndFriends(post.getAuthor().getId());
    }

    // ================ HELPER METHODS ================

    /**
     * Helper method to clear newsfeed cache for a user and their friends
     */
    private void clearNewsfeedCacheForUserAndFriends(Long userId) {
        try {
            // Clear cache for the user
            String userCachePattern = NEWSFEED_CACHE_PREFIX + userId + ":*";
            redisTemplate.delete(redisTemplate.keys(userCachePattern));

            // Clear cache for user's friends
            List<Long> friendIds = getFriendIds(userId);
            for (Long friendId : friendIds) {
                String friendCachePattern = NEWSFEED_CACHE_PREFIX + friendId + ":*";
                redisTemplate.delete(redisTemplate.keys(friendCachePattern));
            }

            log.info("🧹 Cleared newsfeed cache for user {} and {} friends", userId, friendIds.size());
        } catch (Exception e) {
            log.warn("⚠️ Failed to clear newsfeed cache: {}", e.getMessage());
        }
    }

    /**
     * Helper method to get friend IDs for a user with Redis caching
     */
    private List<Long> getFriendIds(Long userId) {
        String cacheKey = USER_FRIENDS_CACHE_PREFIX + userId;

        try {
            // Try to get from cache first
            String cachedFriends = redisTemplate.opsForValue().get(cacheKey);
            if (cachedFriends != null) {
                TypeReference<List<Long>> typeRef = new TypeReference<>() {};
                return objectMapper.readValue(cachedFriends, typeRef);
            }
        } catch (JsonProcessingException e) {
            log.warn("⚠️ Failed to parse cached friends: {}", e.getMessage());
        }

        // Get from database if not in cache
        List<Friend> friendships = friendRepository.findAcceptedFriends(userId, FriendshipStatus.ACCEPTED);
        List<Long> friendIds = friendships.stream()
                .map(friendship -> {
                    if (friendship.getUser().getId().equals(userId)) {
                        return friendship.getFriend().getId();
                    } else {
                        return friendship.getUser().getId();
                    }
                })
                .toList();

        // Cache the result
        try {
            String jsonFriends = objectMapper.writeValueAsString(friendIds);
            redisTemplate.opsForValue().set(cacheKey, jsonFriends,
                    FRIENDS_CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("⚠️ Failed to cache friends: {}", e.getMessage());
        }

        return friendIds;
    }

    /**
     * Helper method to convert Post entity to PostResponseDto
     */
    private PostResponseDto mapToPostResponseDto(Post post, Long viewerId) {
        // Check if current user liked this post
        boolean isLiked = postLikeRepository.findByPostIdAndUserId(post.getId(), viewerId).isPresent();

        // Get like count
        long likeCount = postLikeRepository.countByPost(post);

        // Get comment count (only top-level comments)
        long commentCount = postCommentRepository.countByPostAndParentCommentIsNull(post);

        // Get recent likes (limited to 5)
        List<PostLike> recentLikes = postLikeRepository.findByPostOrderByCreatedAtDesc(post)
                .stream().limit(5).toList();

        List<PostResponseDto.RecentLikeDto> recentLikeDtos = recentLikes.stream()
                .map(like -> {
                    UserProfile likerProfile = like.getUser().getUserProfile();
                    return PostResponseDto.RecentLikeDto.builder()
                            .userId(like.getUser().getId())
                            .username(likerProfile != null ? likerProfile.getUsername() : like.getUser().getEmail())
                            .avatarUrl(likerProfile != null ? likerProfile.getAvtUrl() : null)
                            .likedAt(like.getCreatedAt())
                            .build();
                })
                .toList();

        // Get top comments (limited to 3)
        List<PostComment> topComments = postCommentRepository
                .findByPostAndParentCommentIsNullOrderByCreatedAtAsc(post, PageRequest.of(0, 3))
                .getContent();

        List<PostCommentDto> topCommentDtos = topComments.stream()
                .map(comment -> mapToPostCommentDto(comment, viewerId))
                .toList();

        // Get attachments for this post
        List<PostAttachment> attachments = postAttachmentRepository.findByPostOrderByCreatedAtAsc(post);

        // Separate images and files
        List<String> imageUrls = new ArrayList<>();
        List<PostResponseDto.AttachmentDto> files = new ArrayList<>();

        for (PostAttachment attachment : attachments) {
            if (attachment.getAttachmentType() == PostAttachment.AttachmentType.IMAGE) {
                imageUrls.add(attachment.getS3Url());
            } else {
                files.add(PostResponseDto.AttachmentDto.builder()
                        .id(attachment.getId())
                        .originalFilename(attachment.getOriginalFilename())
                        .s3Url(attachment.getS3Url())
                        .fileSize(attachment.getFileSize())
                        .contentType(attachment.getContentType())
                        .attachmentType(attachment.getAttachmentType().toString())
                        .uploadedAt(attachment.getCreatedAt())
                        .build());
            }
        }

        // Build the response DTO
        return PostResponseDto.builder()
                .id(post.getId())
                .content(post.getContent())
                .privacy(post.getPrivacy())
                .imageUrl(post.getImageUrl()) // Legacy single image
                .imageUrls(imageUrls) // Multiple images from attachments
                .files(files) // Non-image attachments
                .author(PostResponseDto.AuthorDto.builder()
                        .id(post.getAuthor().getId())
                        .email(post.getAuthor().getEmail())
                        .firstName(post.getAuthor().getFirstName())
                        .lastName(post.getAuthor().getLastName())
                        .username(post.getAuthor().getUserProfile() != null ?
                                  post.getAuthor().getUserProfile().getUsername() :
                                  post.getAuthor().getEmail())
                        .avatarUrl(post.getAuthor().getAvatarUrl())
                        .build())
                .linkedTask(post.getLinkedTask() != null ? PostResponseDto.LinkedTaskDto.builder()
                        .id(post.getLinkedTask().getId())
                        .title(post.getLinkedTask().getTitle())
                        .build() : null)
                .linkedProject(post.getLinkedProject() != null ? PostResponseDto.LinkedProjectDto.builder()
                        .id(post.getLinkedProject().getId())
                        .name(post.getLinkedProject().getName())
                        .build() : null)
                .likeCount((int) likeCount)
                .commentCount((int) commentCount)
                .isPinned(post.getIsPinned())
                .isLikedByCurrentUser(isLiked)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .recentLikes(recentLikeDtos)
                .topComments(topCommentDtos)
                .build();
    }

    /**
     * Helper method to convert PostComment entity to PostCommentDto
     */
    private PostCommentDto mapToPostCommentDto(PostComment comment, Long viewerId) {
        // Check if current user liked this comment
        boolean isLiked = postCommentLikeRepository.findByCommentIdAndUserId(comment.getId(), viewerId).isPresent();

        // Get like count for this comment
        long likeCount = postCommentLikeRepository.countByComment(comment);

        // Get reply count for this comment
        long replyCount = postCommentRepository.findByParentCommentOrderByCreatedAtAsc(comment).size();

        // Get recent likes for this comment (limited to 3)
        List<PostCommentLike> recentLikes = postCommentLikeRepository
                .findRecentLikesByCommentId(comment.getId())
                .stream()
                .limit(3)
                .toList();

        List<PostCommentDto.RecentLikeDto> recentLikeDtos = recentLikes.stream()
                .map(like -> {
                    UserProfile likerProfile = like.getUser().getUserProfile();
                    return PostCommentDto.RecentLikeDto.builder()
                            .userId(like.getUser().getId())
                            .username(likerProfile != null ? likerProfile.getUsername() : like.getUser().getEmail())
                            .firstName(likerProfile != null ? likerProfile.getFirstName() : "")
                            .lastName(likerProfile != null ? likerProfile.getLastName() : "")
                            .avatarUrl(likerProfile != null ? likerProfile.getAvtUrl() : null)
                            .likedAt(like.getCreatedAt())
                            .build();
                })
                .toList();

        return PostCommentDto.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .author(PostCommentDto.AuthorDto.builder()
                        .id(comment.getUser().getId())
                        .email(comment.getUser().getEmail())
                        .firstName(comment.getUser().getFirstName())
                        .lastName(comment.getUser().getLastName())
                        .username(comment.getUser().getUserProfile() != null ?
                                  comment.getUser().getUserProfile().getUsername() :
                                  comment.getUser().getEmail())
                        .avatarUrl(comment.getUser().getAvatarUrl())
                        .build())
                .parentCommentId(comment.getParentComment() != null ? comment.getParentComment().getId() : null)
                .likeCount((int) likeCount)
                .replyCount((int) replyCount)
                .isLikedByCurrentUser(isLiked)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .recentLikes(recentLikeDtos)
                .build();
    }

    /**
     * Helper method to convert List to Page
     */
    private <T> Page<T> convertListToPage(List<T> list, Pageable pageable) {
        int start = Math.toIntExact(pageable.getOffset());
        int end = Math.min((start + pageable.getPageSize()), list.size());

        List<T> subList = start >= list.size() ? new ArrayList<>() : list.subList(start, end);

        return new org.springframework.data.domain.PageImpl<>(subList, pageable, list.size());
    }

    /**
     * ✅ NEW: Update/Edit a post (Only post author can edit)
     */
    @Transactional
    public PostResponseDto updatePost(Long postId, UpdatePostRequestDto requestDto) {
        User currentUser = getCurrentUser();

        // Get the post
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Check authorization: only post author can edit
        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Access denied. Only the post author can edit this post.");
        }

        log.info("✏️ User {} editing post {}", currentUser.getEmail(), postId);

        // Update basic post fields if provided
        if (requestDto.getContent() != null) {
            post.setContent(requestDto.getContent());
        }
        if (requestDto.getPrivacy() != null) {
            post.setPrivacy(requestDto.getPrivacy());
        }
        if (requestDto.getIsPinned() != null) {
            post.setIsPinned(requestDto.getIsPinned());
        }

        // Update linked task if provided
        if (requestDto.getLinkedTaskId() != null) {
            Task linkedTask = taskRepository.findById(requestDto.getLinkedTaskId())
                    .orElseThrow(() -> new RuntimeException("Linked task not found"));
            post.setLinkedTask(linkedTask);
        }

        // Update linked project if provided
        if (requestDto.getLinkedProjectId() != null) {
            Project linkedProject = projectRepository.findById(requestDto.getLinkedProjectId())
                    .orElseThrow(() -> new RuntimeException("Linked project not found"));
            post.setLinkedProject(linkedProject);
        }

        // Remove specified images and files
        if (requestDto.getRemoveImageIds() != null && !requestDto.getRemoveImageIds().isEmpty()) {
            for (Long imageId : requestDto.getRemoveImageIds()) {
                Optional<PostAttachment> attachment = postAttachmentRepository.findById(imageId);
                if (attachment.isPresent() && attachment.get().getPost().getId().equals(postId)) {
                    try {
                        // Delete from S3
                        s3FileUploadService.deleteFile(attachment.get().getS3Key());
                        // Delete from database
                        postAttachmentRepository.delete(attachment.get());
                        log.info("🗑️ Removed image attachment: {}", attachment.get().getOriginalFilename());
                    } catch (Exception e) {
                        log.warn("⚠️ Failed to remove image {}: {}", attachment.get().getS3Key(), e.getMessage());
                    }
                }
            }
        }

        if (requestDto.getRemoveFileIds() != null && !requestDto.getRemoveFileIds().isEmpty()) {
            for (Long fileId : requestDto.getRemoveFileIds()) {
                Optional<PostAttachment> attachment = postAttachmentRepository.findById(fileId);
                if (attachment.isPresent() && attachment.get().getPost().getId().equals(postId)) {
                    try {
                        // Delete from S3
                        s3FileUploadService.deleteFile(attachment.get().getS3Key());
                        // Delete from database
                        postAttachmentRepository.delete(attachment.get());
                        log.info("🗑️ Removed file attachment: {}", attachment.get().getOriginalFilename());
                    } catch (Exception e) {
                        log.warn("⚠️ Failed to remove file {}: {}", attachment.get().getS3Key(), e.getMessage());
                    }
                }
            }
        }

        // Handle new single image (legacy support)
        if (requestDto.getImage() != null && !requestDto.getImage().isEmpty()) {
            try {
                // Delete old legacy image if exists
                if (post.getImageS3Key() != null && !post.getImageS3Key().isEmpty()) {
                    s3FileUploadService.deleteFile(post.getImageS3Key());
                }

                String fileName = requestDto.getImage().getOriginalFilename();
                String contentType = requestDto.getImage().getContentType();

                CompletableFuture<FileUploadResponseDto> uploadFuture = s3FileUploadService.uploadFileAsync(
                    fileName,
                    contentType,
                    requestDto.getImage().getInputStream(),
                    requestDto.getImage().getSize(),
                    null
                );

                FileUploadResponseDto uploadResult = uploadFuture.get();
                post.setImageS3Key(uploadResult.getFileKey());
                post.setImageUrl(uploadResult.getDownloadUrl());

                log.info("📸 Updated single image for post {}", postId);
            } catch (Exception e) {
                log.error("❌ Failed to update single image: {}", e.getMessage());
                throw new RuntimeException("Failed to update image: " + e.getMessage());
            }
        }

        // Add new multiple images
        if (requestDto.getImages() != null && !requestDto.getImages().isEmpty()) {
            for (MultipartFile imageFile : requestDto.getImages()) {
                if (imageFile != null && !imageFile.isEmpty()) {
                    try {
                        String fileName = imageFile.getOriginalFilename();
                        String contentType = imageFile.getContentType();

                        CompletableFuture<FileUploadResponseDto> uploadFuture = s3FileUploadService.uploadFileAsync(
                            fileName,
                            contentType,
                            imageFile.getInputStream(),
                            imageFile.getSize(),
                            null
                        );

                        FileUploadResponseDto uploadResult = uploadFuture.get();

                        // Create PostAttachment for this image
                        PostAttachment imageAttachment = PostAttachment.builder()
                                .post(post)
                                .originalFilename(fileName)
                                .s3Key(uploadResult.getFileKey())
                                .s3Url(uploadResult.getDownloadUrl())
                                .fileSize(imageFile.getSize())
                                .contentType(contentType)
                                .attachmentType(PostAttachment.AttachmentType.IMAGE)
                                .build();

                        postAttachmentRepository.save(imageAttachment);
                        log.info("📸 Added new image {} to post {}", fileName, postId);
                    } catch (Exception e) {
                        log.error("❌ Failed to add image {}: {}", imageFile.getOriginalFilename(), e.getMessage());
                        throw new RuntimeException("Failed to add image " + imageFile.getOriginalFilename() + ": " + e.getMessage());
                    }
                }
            }
        }

        // Add new files
        if (requestDto.getFiles() != null && !requestDto.getFiles().isEmpty()) {
            for (MultipartFile file : requestDto.getFiles()) {
                if (file != null && !file.isEmpty()) {
                    try {
                        String fileName = file.getOriginalFilename();
                        String contentType = file.getContentType();

                        CompletableFuture<FileUploadResponseDto> uploadFuture = s3FileUploadService.uploadFileAsync(
                            fileName,
                            contentType,
                            file.getInputStream(),
                            file.getSize(),
                            null
                        );

                        FileUploadResponseDto uploadResult = uploadFuture.get();

                        // Determine attachment type based on content type
                        PostAttachment.AttachmentType attachmentType = determineAttachmentType(contentType);

                        // Create PostAttachment for this file
                        PostAttachment fileAttachment = PostAttachment.builder()
                                .post(post)
                                .originalFilename(fileName)
                                .s3Key(uploadResult.getFileKey())
                                .s3Url(uploadResult.getDownloadUrl())
                                .fileSize(file.getSize())
                                .contentType(contentType)
                                .attachmentType(attachmentType)
                                .build();

                        postAttachmentRepository.save(fileAttachment);
                        log.info("📎 Added new file {} to post {}", fileName, postId);
                    } catch (Exception e) {
                        log.error("❌ Failed to add file {}: {}", file.getOriginalFilename(), e.getMessage());
                        throw new RuntimeException("Failed to add file " + file.getOriginalFilename() + ": " + e.getMessage());
                    }
                }
            }
        }

        // Save updated post
        post.setUpdatedAt(LocalDateTime.now());
        Post savedPost = postRepository.save(post);

        // Clear newsfeed cache for current user and friends
        clearNewsfeedCacheForUserAndFriends(currentUser.getId());

        log.info("✅ Successfully updated post {}", postId);

        return mapToPostResponseDto(savedPost, currentUser.getId());
    }
}
