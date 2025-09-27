package com.example.taskmanagement_backend.dtos;

import com.example.taskmanagement_backend.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ChatDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SendMessageRequestDto {
        private Long conversationId;
        private String content;
        private MessageType type;
        private String fileName;
        private String fileUrl;
        private Long fileSize;
        private Long replyToId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageResponseDto {
        private Long id;
        private Long conversationId;
        private Long senderId;
        private String senderName;
        private String senderAvatar;
        private MessageType type;
        private String content;
        private String fileName;
        private String fileUrl;
        private Long fileSize;
        private Long replyToId;
        private String replyToContent;
        private String replyToSenderName;
        private Boolean isEdited;
        private Boolean isDeleted;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Integer deliveredCount;
        private Integer readCount;
        private Boolean isDelivered;
        private Boolean isRead;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConversationResponseDto {
        private Long id;
        private String type;
        private String name;
        private String description;
        private String avatarUrl;
        private Integer memberCount;
        private MessageResponseDto lastMessage;
        private Integer unreadCount;
        private Boolean isOnline;
        private LocalDateTime lastActivity;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreateGroupRequestDto {
        private String name;
        private String description;
        private String avatarUrl;
        private java.util.List<Long> memberIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageReadStatusDto {
        private Long messageId;
        private Long userId;
        private String userName;
        private String userAvatar;
        private String status;
        private LocalDateTime deliveredAt;
        private LocalDateTime readAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OnlineStatusDto {
        private Long userId;
        private String status; // ONLINE, OFFLINE, AWAY
        private LocalDateTime lastSeen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TypingStatusDto {
        private Long conversationId;
        private Long userId;
        private String userName;
        private Boolean isTyping;
    }

    /**
     * ChatHistoryResponseDto - Response for chunked chat history loading
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatHistoryResponseDto {
        private Long conversationId;
        private List<MessageResponseDto> messages;
        private Integer currentPage;
        private Integer pageSize;
        private Long totalMessages;
        private Integer totalPages;
        private Boolean hasMoreMessages;
        private String loadedFrom; // "MYSQL", "CACHE", etc.
        private LocalDateTime loadedAt;
    }

    /**
     * OfflineSyncResponseDto - Response for offline message synchronization
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OfflineSyncResponseDto {
        private Long userId;
        private Integer totalMissedMessages;
        private Integer conversationCount;
        private Map<Long, List<MessageResponseDto>> messagesByConversation;
        private LocalDateTime syncedAt;
        private String syncType; // "MESSAGE_ID_BASED", "TIMESTAMP_BASED", "FULL_UNREAD"
    }

    /**
     * ChatFileUploadResponseDto - Response for file upload operations
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatFileUploadResponseDto {
        private String fileName;
        private String fileUrl;
        private String previewUrl;
        private Long fileSize;
        private String contentType;
        private String category; // IMAGE, VIDEO, DOCUMENT, OTHER
        private String s3Key;
        private LocalDateTime uploadedAt;
    }

    /**
     * ChatFileMetadataDto - File metadata information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatFileMetadataDto {
        private String s3Key;
        private String fileName;
        private String contentType;
        private Long fileSize;
        private String downloadUrl;
        private String previewUrl;
        private String category;
        private LocalDateTime uploadedAt;
        private LocalDateTime lastAccessedAt;
    }

    /**
     * ChatFileStatsDto - File statistics for conversation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatFileStatsDto {
        private Long conversationId;
        private Integer totalFiles;
        private Long totalSize;
        private Integer imageCount;
        private Integer videoCount;
        private Integer documentCount;
        private Integer otherCount;
        private LocalDateTime lastFileUploadedAt;
    }

    /**
     * FileUploadRequestDto - Request for file upload
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FileUploadRequestDto {
        private Long conversationId;
        private String description; // Optional description for the file
        private Boolean isPrivate; // Whether file should be private (future feature)
    }

    /**
     * CreateGroupFromFriendsRequestDto - Request for creating group chat from friends list
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreateGroupFromFriendsRequestDto {
        private String name;
        private String description;
        private String avatarUrl;
        private List<Long> friendIds; // List of friend IDs to add to the group
    }

    /**
     * FriendForGroupChatDto - Friend information for group chat creation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FriendForGroupChatDto {
        private Long id;
        private String name;
        private String email;
        private String avatarUrl;
        private Boolean isOnline;
        private LocalDateTime lastSeen;
        private Boolean isSelected; // For UI selection state
    }

    /**
     * ConversationMemberDto - Member information for group conversations
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConversationMemberDto {
        private Long userId;
        private String userName;
        private String userAvatar;
        private String role; // "ADMIN" or "MEMBER"
        private LocalDateTime joinedAt;
        private Boolean isOnline;
    }

    /**
     * BulkMessageReadStatusDto - For bulk marking messages as read
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BulkMessageReadStatusDto {
        private Long conversationId;
        private Long userId;
        private String userName;
        private String userAvatar;
        private List<Long> messageIds;
        private String status; // "READ"
        private LocalDateTime readAt;
        private Boolean autoMarked; // Flag to indicate this was auto-marked via WebSocket
    }

    /**
     * DetailedMessageReadStatusDto - Enhanced read status with formatted time display
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DetailedMessageReadStatusDto {
        private Long messageId;
        private Long userId;
        private String userName;
        private String userAvatar;
        private String status;
        private LocalDateTime deliveredAt;
        private LocalDateTime readAt;

        // Formatted time strings for frontend display
        private String deliveredAtFormatted; // "14:30"
        private String readAtFormatted; // "14:35"
        private String deliveredDateFormatted; // "10/09/2025"
        private String readDateFormatted; // "10/09/2025"

        // Relative time for better UX
        private String readAtRelative; // "5 minutes ago", "Just now", etc.
        private String deliveredAtRelative;

        // Additional useful info
        private Boolean isToday; // true if read today
        private Boolean isRecent; // true if read within last 5 minutes
        private Long readDelayMinutes; // minutes between delivery and read
    }
}
