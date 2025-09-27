package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.ChatDto.*;
import com.example.taskmanagement_backend.dtos.MessageReactionDto;
import com.example.taskmanagement_backend.entities.Message;
import com.example.taskmanagement_backend.enums.MessageType;
import com.example.taskmanagement_backend.repositories.ConversationMemberRepository;
import com.example.taskmanagement_backend.repositories.MessageRepository;
import com.example.taskmanagement_backend.services.ChatService;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.services.ChatFileService;
import com.example.taskmanagement_backend.services.MessageReactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.List;

/**
 * REST API controller for chat functionality
 * Provides HTTP endpoints for chat operations that don't require real-time communication
 * NOTE: Online status is managed separately in profile endpoints using DB-based approach
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserJpaRepository userRepository;
    private final MessageRepository messageRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ChatFileService chatFileService; // Add ChatFileService dependency
    private final MessageReactionService messageReactionService; // 🆕 Add MessageReactionService

    /**
     * Get user's conversations with pagination
     */
    @GetMapping("/conversations")
    public ResponseEntity<Page<ConversationResponseDto>> getUserConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<ConversationResponseDto> conversations = chatService.getUserConversations(userId, page, size);
        return ResponseEntity.ok(conversations);
    }

    /**
     * Get messages from a specific conversation
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> getConversationMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<MessageResponseDto> messagesPage = chatService.getConversationMessages(userId, conversationId, page, size);

        // Tạo custom response object với field "messages" để frontend có thể access đúng
        Map<String, Object> response = Map.of(
                "messages", messagesPage.getContent(),           // List<MessageResponseDto>
                "totalElements", messagesPage.getTotalElements(), // Tổng số tin nhắn
                "totalPages", messagesPage.getTotalPages(),       // Tổng số trang
                "currentPage", messagesPage.getNumber(),          // Trang hiện tại
                "pageSize", messagesPage.getSize(),               // Kích thước trang
                "hasNext", messagesPage.hasNext(),                // Có trang tiếp theo?
                "hasPrevious", messagesPage.hasPrevious(),        // Có trang trước?
                "isFirst", messagesPage.isFirst(),                // Trang đầu tiên?
                "isLast", messagesPage.isLast()                   // Trang cuối cùng?
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Send a message (alternative to WebSocket)
     */
    @PostMapping("/messages")
    public ResponseEntity<MessageResponseDto> sendMessage(
            @RequestBody SendMessageRequestDto request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        MessageResponseDto response = chatService.sendMessage(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Create a direct conversation with another user
     */
    @PostMapping("/conversations/direct")
    public ResponseEntity<ConversationResponseDto> createDirectConversation(
            @RequestBody CreateDirectConversationDto request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        ConversationResponseDto response = chatService.createDirectConversation(userId, request.getOtherUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * Create a group conversation
     */
    @PostMapping("/conversations/group")
    public ResponseEntity<ConversationResponseDto> createGroupConversation(
            @RequestBody CreateGroupRequestDto request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        ConversationResponseDto response = chatService.createGroupConversation(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark message as read (alternative to WebSocket)
     */
    @PutMapping("/messages/{messageId}/read")
    public ResponseEntity<Void> markMessageAsRead(
            @PathVariable Long messageId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        chatService.markMessageAsRead(userId, messageId);
        return ResponseEntity.ok().build();
    }

    /**
     * Search conversations and messages
     */
    @GetMapping("/search")
    public ResponseEntity<ChatSearchResultDto> searchChat(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        // TODO: Implement search functionality
        ChatSearchResultDto results = ChatSearchResultDto.builder()
                .conversations(Page.empty())
                .messages(Page.empty())
                .build();
        return ResponseEntity.ok(results);
    }

    /**
     * Get conversation details
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationDetailDto> getConversationDetails(
            @PathVariable Long conversationId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        ConversationDetailDto details = chatService.getConversationDetails(userId, conversationId);
        return ResponseEntity.ok(details);
    }

    /**
     * Add members to group conversation
     */
    @PostMapping("/conversations/{conversationId}/members")
    public ResponseEntity<List<ConversationMemberDto>> addMembers(
            @PathVariable Long conversationId,
            @RequestBody AddMembersRequestDto request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);

        try {
            List<ConversationMemberDto> addedMembers = chatService.addMembersToConversation(
                userId, conversationId, request.getUserIds());

            log.info("✅ Successfully added {} members to conversation {}",
                addedMembers.size(), conversationId);

            return ResponseEntity.ok(addedMembers);
        } catch (Exception e) {
            log.error("❌ Error adding members to conversation {}: {}", conversationId, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Remove member from group conversation
     */
    @DeleteMapping("/conversations/{conversationId}/members/{memberId}")
    public ResponseEntity<Map<String, String>> removeMember(
            @PathVariable Long conversationId,
            @PathVariable Long memberId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);

        try {
            chatService.removeMemberFromConversation(userId, conversationId, memberId);

            log.info("✅ Successfully removed member {} from conversation {}", memberId, conversationId);

            return ResponseEntity.ok(Map.of(
                "message", "Member removed successfully",
                "removedMemberId", memberId.toString(),
                "conversationId", conversationId.toString()
            ));
        } catch (Exception e) {
            log.error("❌ Error removing member {} from conversation {}: {}",
                memberId, conversationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Leave group conversation (self-remove)
     */
    @PostMapping("/conversations/{conversationId}/leave")
    public ResponseEntity<Map<String, String>> leaveConversation(
            @PathVariable Long conversationId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);

        try {
            // Use the same removeMember logic but user removes themselves
            chatService.removeMemberFromConversation(userId, conversationId, userId);

            log.info("✅ User {} successfully left conversation {}", userId, conversationId);

            return ResponseEntity.ok(Map.of(
                "message", "Successfully left the conversation",
                "conversationId", conversationId.toString()
            ));
        } catch (Exception e) {
            log.error("❌ Error leaving conversation {}: {}", conversationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update group conversation details
     */
    @PutMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationResponseDto> updateConversation(
            @PathVariable Long conversationId,
            @RequestBody UpdateConversationRequestDto request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        // TODO: Implement update conversation functionality
        return ResponseEntity.ok().build();
    }

    /**
     * Get ALL messages from a conversation (từ cũ nhất đến mới nhất, không phân trang)
     */
    @GetMapping("/conversations/{conversationId}/messages/all")
    public ResponseEntity<Map<String, Object>> getAllConversationMessages(
            @PathVariable Long conversationId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        List<MessageResponseDto> allMessages = chatService.getAllConversationMessages(userId, conversationId);

        Map<String, Object> response = Map.of(
                "messages", allMessages,
                "totalMessages", allMessages.size(),
                "fromOldest", true,
                "loadedAt", java.time.LocalDateTime.now()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get messages from oldest with pagination
     */
    @GetMapping("/conversations/{conversationId}/messages/from-oldest")
    public ResponseEntity<Map<String, Object>> getConversationMessagesFromOldest(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<MessageResponseDto> messagesPage = chatService.getConversationMessagesFromOldest(userId, conversationId, page, size);

        Map<String, Object> response = Map.of(
                "messages", messagesPage.getContent(),
                "totalElements", messagesPage.getTotalElements(),
                "totalPages", messagesPage.getTotalPages(),
                "currentPage", messagesPage.getNumber(),
                "pageSize", messagesPage.getSize(),
                "hasNext", messagesPage.hasNext(),
                "hasPrevious", messagesPage.hasPrevious(),
                "isFirst", messagesPage.isFirst(),
                "isLast", messagesPage.isLast(),
                "fromOldest", true
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Reply to a specific message in a conversation
     */
    @PostMapping("/messages/{messageId}/reply")
    public ResponseEntity<MessageResponseDto> replyToMessage(
            @PathVariable Long messageId,
            @RequestBody ReplyToMessageRequestDto request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);

        // Create a SendMessageRequestDto with the reply information
        SendMessageRequestDto sendRequest = SendMessageRequestDto.builder()
                .conversationId(request.getConversationId())
                .content(request.getContent())
                .type(request.getType() != null ? request.getType() : MessageType.TEXT)
                .fileName(request.getFileName())
                .fileUrl(request.getFileUrl())
                .fileSize(request.getFileSize())
                .replyToId(messageId) // Set the message being replied to
                .build();

        MessageResponseDto response = chatService.sendMessage(userId, sendRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Get message details for reply context
     */
    @GetMapping("/messages/{messageId}")
    public ResponseEntity<MessageResponseDto> getMessageDetails(
            @PathVariable Long messageId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);

        // Get the message
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Verify user is member of the conversation
        conversationMemberRepository.findByConversationIdAndUserId(message.getConversation().getId(), userId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

        // Convert to DTO (reuse the service's conversion method)
        MessageResponseDto messageDto = chatService.convertToMessageResponseDto(message);

        return ResponseEntity.ok(messageDto);
    }

    /**
     * Upload file for chat message
     */
    @PostMapping("/files/upload")
    public ResponseEntity<ChatFileUploadResponseDto> uploadChatFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("conversationId") Long conversationId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);

        // Verify user is member of the conversation
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

        ChatFileUploadResponseDto response = chatFileService.uploadChatFile(file, userId, conversationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Send message with file attachment
     */
    @PostMapping("/messages/with-file")
    public ResponseEntity<MessageResponseDto> sendMessageWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("conversationId") Long conversationId,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "replyToId", required = false) Long replyToId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);

        // Upload file first
        ChatFileUploadResponseDto fileUpload = chatFileService.uploadChatFile(file, userId, conversationId);

        // Create message with file attachment
        SendMessageRequestDto messageRequest = SendMessageRequestDto.builder()
                .conversationId(conversationId)
                .content(content != null ? content : "") // Allow empty content for file-only messages
                .type(determineMessageType(fileUpload.getContentType()))
                .fileName(fileUpload.getFileName())
                .fileUrl(fileUpload.getFileUrl())
                .fileSize(fileUpload.getFileSize())
                .replyToId(replyToId)
                .build();

        MessageResponseDto response = chatService.sendMessage(userId, messageRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Get file download URL
     */
    @GetMapping("/files/{s3Key}/download")
    public ResponseEntity<Map<String, String>> getFileDownloadUrl(
            @PathVariable String s3Key,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);

        // TODO: Add permission check to ensure user can access this file

        String downloadUrl = chatFileService.getFileDownloadUrl(s3Key);

        Map<String, String> response = Map.of(
                "downloadUrl", downloadUrl,
                "s3Key", s3Key
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 🔧 Generate fresh presigned URL for file attachment (fixes 403 Forbidden errors)
     * This endpoint allows the frontend to request fresh URLs when existing ones expire
     */
    @PostMapping("/files/generate-url")
    public ResponseEntity<Map<String, String>> generateFreshFileUrl(
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        String s3Key = request.get("s3Key");
        String fileUrl = request.get("fileUrl");

        if (s3Key == null && fileUrl == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Either s3Key or fileUrl is required"));
        }

        try {
            // Extract S3 key if fileUrl is provided
            if (s3Key == null && fileUrl != null) {
                s3Key = extractS3KeyFromUrl(fileUrl);
            }

            if (s3Key == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid file URL or S3 key"));
            }

            // TODO: Add permission check to ensure user can access this file

            // Generate fresh presigned URL
            String freshUrl = chatFileService.getFileDownloadUrl(s3Key);

            Map<String, String> response = Map.of(
                    "downloadUrl", freshUrl,
                    "s3Key", s3Key,
                    "originalUrl", fileUrl != null ? fileUrl : s3Key,
                    "generatedAt", java.time.LocalDateTime.now().toString()
            );

            log.debug("🔧 Generated fresh presigned URL for user {} and S3 key: {}", userId, s3Key);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error generating fresh presigned URL: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to generate download URL"));
        }
    }

    /**
     * 🔧 Helper method to extract S3 key from URL
     * Improved version to handle URL encoding and various URL formats
     */
    private String extractS3KeyFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        try {
            // If the URL looks like an S3 key (doesn't start with http), use it directly
            if (!fileUrl.startsWith("http")) {
                return java.net.URLDecoder.decode(fileUrl, "UTF-8");
            }

            // Extract S3 key from presigned URL
            // Format: https://bucket-name.s3.region.amazonaws.com/key?signature...
            java.net.URI uri = new java.net.URI(fileUrl);
            String path = uri.getPath();

            // Remove leading slash
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            // 🔧 Decode URL-encoded characters (e.g., %20 -> space, %28 -> (, %29 -> ))
            String decodedPath = java.net.URLDecoder.decode(path, "UTF-8");

            log.debug("🔧 Extracted S3 key: '{}' from URL: '{}'", decodedPath, fileUrl);
            return decodedPath.isEmpty() ? null : decodedPath;

        } catch (Exception e) {
            log.warn("⚠️ Could not extract S3 key from URL: {}, error: {}", fileUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Get friends list for creating group chat
     */
    @GetMapping("/friends/for-group-chat")
    public ResponseEntity<List<FriendForGroupChatDto>> getFriendsForGroupChat(
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        List<FriendForGroupChatDto> friends = chatService.getFriendsForGroupChat(userId);
        return ResponseEntity.ok(friends);
    }

    /**
     * Create group conversation from friends list
     */
    @PostMapping("/conversations/group/from-friends")
    public ResponseEntity<ConversationResponseDto> createGroupFromFriends(
            @RequestBody CreateGroupFromFriendsRequestDto request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        ConversationResponseDto response = chatService.createGroupFromFriends(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed read status for a specific message with formatted time
     */
    @GetMapping("/messages/{messageId}/read-status/detailed")
    public ResponseEntity<List<DetailedMessageReadStatusDto>> getDetailedMessageReadStatus(
            @PathVariable Long messageId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        List<DetailedMessageReadStatusDto> readStatus = chatService.getDetailedMessageReadStatus(messageId, userId);
        return ResponseEntity.ok(readStatus);
    }

    /**
     * Get read status summary for conversation - shows who has read recent messages with time
     */
    @GetMapping("/conversations/{conversationId}/read-status/summary")
    public ResponseEntity<Map<String, Object>> getConversationReadStatusSummary(
            @PathVariable Long conversationId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Map<String, Object> summary = chatService.getConversationReadStatusSummary(conversationId, userId);
        return ResponseEntity.ok(summary);
    }

    /**
     * Add reaction to a message
     * Supports optimistic updates for better UX
     */
    @PostMapping("/messages/{messageId}/reactions")
    public ResponseEntity<MessageReactionDto.ReactionEventDto> addReaction(
            @PathVariable Long messageId,
            @RequestBody MessageReactionDto.AddReactionRequestDto request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        MessageReactionDto.ReactionEventDto event = messageReactionService.addReaction(messageId, userId, request.getReactionType());
        return ResponseEntity.ok(event);
    }

    /**
     * Remove reaction from a message
     */
    @DeleteMapping("/messages/{messageId}/reactions/{reactionType}")
    public ResponseEntity<MessageReactionDto.ReactionEventDto> removeReaction(
            @PathVariable Long messageId,
            @PathVariable String reactionType,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        MessageReactionDto.ReactionEventDto event = messageReactionService.removeReaction(messageId, userId, reactionType);
        return ResponseEntity.ok(event);
    }

    /**
     * Toggle reaction (add if not exists, remove if exists)
     * Recommended for single-click reaction functionality
     */
    @PostMapping("/messages/{messageId}/reactions/{reactionType}/toggle")
    public ResponseEntity<MessageReactionDto.ReactionEventDto> toggleReaction(
            @PathVariable Long messageId,
            @PathVariable String reactionType,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        MessageReactionDto.ReactionEventDto event = messageReactionService.toggleReaction(messageId, userId, reactionType);
        return ResponseEntity.ok(event);
    }

    /**
     * Get reaction summary for a specific message
     */
    @GetMapping("/messages/{messageId}/reactions")
    public ResponseEntity<MessageReactionDto.MessageReactionSummaryDto> getMessageReactions(
            @PathVariable Long messageId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        MessageReactionDto.MessageReactionSummaryDto summary = messageReactionService.getMessageReactionSummary(messageId, userId);
        return ResponseEntity.ok(summary);
    }

    /**
     * Get bulk reaction summaries for multiple messages (useful for loading conversation with reactions)
     */
    @PostMapping("/conversations/{conversationId}/messages/reactions/bulk")
    public ResponseEntity<MessageReactionDto.BulkReactionSummaryDto> getBulkReactionSummaries(
            @PathVariable Long conversationId,
            @RequestBody List<Long> messageIds,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        MessageReactionDto.BulkReactionSummaryDto bulkSummary = messageReactionService.getBulkReactionSummaries(conversationId, messageIds, userId);
        return ResponseEntity.ok(bulkSummary);
    }

    /**
     * Get available reaction types and their emojis
     */
    @GetMapping("/reactions/types")
    public ResponseEntity<Map<String, String>> getAvailableReactionTypes() {
        Map<String, String> reactionTypes = messageReactionService.getAvailableReactionTypes();
        return ResponseEntity.ok(reactionTypes);
    }

    /**
     * Determine message type based on file content type
     */
    private MessageType determineMessageType(String contentType) {
        if (contentType == null) return MessageType.FILE;

        String lowerType = contentType.toLowerCase();
        if (lowerType.startsWith("image/")) {
            return MessageType.IMAGE;
        } else if (lowerType.startsWith("video/")) {
            return MessageType.VIDEO;
        } else {
            return MessageType.FILE;
        }
    }

    private Long getUserIdFromAuth(Authentication authentication) {
        try {
            // Tận dụng hệ thống authentication có sẵn
            if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                // Lấy email từ authentication
                String email = userDetails.getUsername();

                // Tìm user từ UserJpaRepository có sẵn
                return userRepository.findByEmail(email)
                        .map(user -> user.getId())
                        .orElse(null);
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting user ID from authentication: {}", e.getMessage());
            return null;
        }
    }

    // Additional DTOs for chat operations
    public static class CreateDirectConversationDto {
        private Long otherUserId;

        public Long getOtherUserId() { return otherUserId; }
        public void setOtherUserId(Long otherUserId) { this.otherUserId = otherUserId; }
    }

    public static class ChatSearchResultDto {
        private Page<ConversationResponseDto> conversations;
        private Page<MessageResponseDto> messages;

        public static ChatSearchResultDtoBuilder builder() {
            return new ChatSearchResultDtoBuilder();
        }

        public static class ChatSearchResultDtoBuilder {
            private Page<ConversationResponseDto> conversations;
            private Page<MessageResponseDto> messages;

            public ChatSearchResultDtoBuilder conversations(Page<ConversationResponseDto> conversations) {
                this.conversations = conversations;
                return this;
            }

            public ChatSearchResultDtoBuilder messages(Page<MessageResponseDto> messages) {
                this.messages = messages;
                return this;
            }

            public ChatSearchResultDto build() {
                ChatSearchResultDto dto = new ChatSearchResultDto();
                dto.conversations = this.conversations;
                dto.messages = this.messages;
                return dto;
            }
        }

        public Page<ConversationResponseDto> getConversations() { return conversations; }
        public Page<MessageResponseDto> getMessages() { return messages; }
    }

    public static class ConversationDetailDto {
        private Long id;
        private String type;
        private String name;
        private String description;
        private String avatarUrl;
        private java.util.List<ConversationMemberDto> members;
        private Integer messageCount;
        private java.time.LocalDateTime createdAt;

        public static ConversationDetailDtoBuilder builder() {
            return new ConversationDetailDtoBuilder();
        }

        public static class ConversationDetailDtoBuilder {
            private Long id;

            public ConversationDetailDtoBuilder id(Long id) {
                this.id = id;
                return this;
            }

            public ConversationDetailDto build() {
                ConversationDetailDto dto = new ConversationDetailDto();
                dto.id = this.id;
                return dto;
            }
        }

        public Long getId() { return id; }
    }


    public static class AddMembersRequestDto {
        private java.util.List<Long> userIds;

        public java.util.List<Long> getUserIds() { return userIds; }
        public void setUserIds(java.util.List<Long> userIds) { this.userIds = userIds; }
    }

    public static class UpdateConversationRequestDto {
        private String name;
        private String description;
        private String avatarUrl;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }

    public static class ReplyToMessageRequestDto {
        private Long conversationId;
        private String content;
        private MessageType type;
        private String fileName;
        private String fileUrl;
        private Long fileSize;

        public Long getConversationId() { return conversationId; }
        public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public MessageType getType() { return type; }
        public void setType(MessageType type) { this.type = type; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    }
}
