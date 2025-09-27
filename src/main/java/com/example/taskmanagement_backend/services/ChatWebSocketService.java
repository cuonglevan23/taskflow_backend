package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ChatDto.*;
import com.example.taskmanagement_backend.dtos.MessageReactionDto;
import com.example.taskmanagement_backend.repositories.ConversationMemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * WebSocket service for real-time chat communication
 * Handles sending messages, status updates, and notifications to connected clients
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRedisService chatRedisService;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ObjectMapper objectMapper;
    private final @Lazy ChatService chatService; // ✅ Break circular dependency with @Lazy

    // WebSocket destinations
    private static final String USER_QUEUE_PREFIX = "/queue/user/";
    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversation/";

    /**
     * Send direct message to conversation members
     */
    public void sendDirectMessage(MessageResponseDto message) {
        try {
            // Get conversation members from cache or database
            Set<Object> cachedMembers = chatRedisService.getCachedConversationMembers(message.getConversationId());
            List<Long> memberIds;

            if (cachedMembers.isEmpty()) {
                memberIds = conversationMemberRepository.findUserIdsByConversationId(message.getConversationId());
                chatRedisService.cacheConversationMembers(message.getConversationId(), Set.copyOf(memberIds));
            } else {
                memberIds = cachedMembers.stream()
                        .map(obj -> Long.valueOf(obj.toString()))
                        .toList();
            }

            // Send to each member's personal queue
            for (Long memberId : memberIds) {
                String destination = USER_QUEUE_PREFIX + memberId + "/messages";
                messagingTemplate.convertAndSend(destination, message);
                log.debug("Sent direct message to user {} at destination {}", memberId, destination);
            }

            // Also send to conversation topic for real-time updates
            String conversationDestination = CONVERSATION_TOPIC_PREFIX + message.getConversationId() + "/messages";
            messagingTemplate.convertAndSend(conversationDestination, message);

        } catch (Exception e) {
            log.error("Error sending direct message for conversation {}: {}", message.getConversationId(), e.getMessage());
        }
    }

    /**
     * Send group message to conversation members
     */
    public void sendGroupMessage(MessageResponseDto message) {
        try {
            // Get conversation members from cache or database
            Set<Object> cachedMembers = chatRedisService.getCachedConversationMembers(message.getConversationId());
            List<Long> memberIds;

            if (cachedMembers.isEmpty()) {
                memberIds = conversationMemberRepository.findUserIdsByConversationId(message.getConversationId());
                chatRedisService.cacheConversationMembers(message.getConversationId(), Set.copyOf(memberIds));
            } else {
                memberIds = cachedMembers.stream()
                        .map(obj -> Long.valueOf(obj.toString()))
                        .toList();
            }

            // Send to each member's personal queue
            for (Long memberId : memberIds) {
                String destination = USER_QUEUE_PREFIX + memberId + "/messages";
                messagingTemplate.convertAndSend(destination, message);
                log.debug("Sent group message to user {} at destination {}", memberId, destination);
            }

            // Also send to conversation topic for real-time updates
            String conversationDestination = CONVERSATION_TOPIC_PREFIX + message.getConversationId() + "/messages";
            messagingTemplate.convertAndSend(conversationDestination, message);

        } catch (Exception e) {
            log.error("Error sending group message for conversation {}: {}", message.getConversationId(), e.getMessage());
        }
    }

    /**
     * Send message read status update
     */
    public void sendMessageReadStatus(MessageReadStatusDto readStatus) {
        try {
            // Send read status to all conversation members
            // First, we need to get the conversation ID from the message
            // For now, we'll send to a general message status topic
            String destination = "/topic/message/" + readStatus.getMessageId() + "/status";
            messagingTemplate.convertAndSend(destination, readStatus);

            log.debug("Sent message read status for message {} to {}", readStatus.getMessageId(), destination);

        } catch (Exception e) {
            log.error("Error sending message read status for message {}: {}", readStatus.getMessageId(), e.getMessage());
        }
    }

    /**
     * Send typing status to conversation members
     */
    public void sendTypingStatus(TypingStatusDto typingStatus) {
        try {
            // Send typing status to conversation topic
            String destination = CONVERSATION_TOPIC_PREFIX + typingStatus.getConversationId() + "/typing";
            messagingTemplate.convertAndSend(destination, typingStatus);

            log.debug("Sent typing status for user {} in conversation {} to {}",
                typingStatus.getUserId(), typingStatus.getConversationId(), destination);

        } catch (Exception e) {
            log.error("Error sending typing status for conversation {}: {}", typingStatus.getConversationId(), e.getMessage());
        }
    }

    /**
     * Send online status update
     */
    public void sendOnlineStatus(OnlineStatusDto onlineStatus) {
        try {
            // Send online status to global topic and user-specific topic
            String globalDestination = "/topic/online-status";
            String userDestination = "/topic/user/" + onlineStatus.getUserId() + "/status";

            messagingTemplate.convertAndSend(globalDestination, onlineStatus);
            messagingTemplate.convertAndSend(userDestination, onlineStatus);

            log.debug("Sent online status for user {} to global and user topics", onlineStatus.getUserId());

        } catch (Exception e) {
            log.error("Error sending online status for user {}: {}", onlineStatus.getUserId(), e.getMessage());
        }
    }

    /**
     * Send notification to specific user
     */
    public void sendNotificationToUser(Long userId, Object notification) {
        try {
            String destination = USER_QUEUE_PREFIX + userId + "/notifications";
            messagingTemplate.convertAndSend(destination, notification);

            log.debug("Sent notification to user {} at destination {}", userId, destination);

        } catch (Exception e) {
            log.error("Error sending notification to user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Send conversation update to members
     */
    public void sendConversationUpdate(Long conversationId, Object update) {
        try {
            String destination = CONVERSATION_TOPIC_PREFIX + conversationId + "/updates";
            messagingTemplate.convertAndSend(destination, update);

            log.debug("Sent conversation update to conversation {} at destination {}", conversationId, destination);

        } catch (Exception e) {
            log.error("Error sending conversation update for conversation {}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * Send unread count update to user
     */
    public void sendUnreadCountUpdate(Long userId, Long conversationId, Integer unreadCount) {
        try {
            UnreadCountUpdateDto update = UnreadCountUpdateDto.builder()
                    .conversationId(conversationId)
                    .unreadCount(unreadCount)
                    .build();

            String destination = USER_QUEUE_PREFIX + userId + "/unread-counts";
            messagingTemplate.convertAndSend(destination, update);

            log.debug("Sent unread count update to user {} for conversation {}: {}", userId, conversationId, unreadCount);

        } catch (Exception e) {
            log.error("Error sending unread count update to user {} for conversation {}: {}", userId, conversationId, e.getMessage());
        }
    }

    /**
     * Broadcast message to all online users in conversation
     */
    public void broadcastToConversation(Long conversationId, String type, Object payload) {
        try {
            BroadcastMessageDto broadcast = BroadcastMessageDto.builder()
                    .type(type)
                    .conversationId(conversationId)
                    .payload(payload)
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            String destination = CONVERSATION_TOPIC_PREFIX + conversationId + "/broadcasts";
            messagingTemplate.convertAndSend(destination, broadcast);

            log.debug("Broadcasted {} message to conversation {}", type, conversationId);

        } catch (Exception e) {
            log.error("Error broadcasting {} message to conversation {}: {}", type, conversationId, e.getMessage());
        }
    }

    /**
     * Handle user connection - sync offline messages
     * Called when user connects to WebSocket
     */
    public void handleUserConnected(Long userId, String sessionId) {
        try {
            log.info("🔗 User {} connected - starting smart offline sync from MySQL", userId);

            // Get enhanced offline sync data from MySQL
            OfflineSyncResponseDto syncData = chatService.getMessagesForOfflineSync(userId, null, null);

            if (syncData.getTotalMissedMessages() > 0) {
                log.info("📬 Found {} missed messages across {} conversations for user {}",
                        syncData.getTotalMissedMessages(), syncData.getConversationCount(), userId);

                // Send sync data organized by conversation
                String syncDestination = USER_QUEUE_PREFIX + userId + "/offline-sync";
                messagingTemplate.convertAndSend(syncDestination, syncData);

                // Also send individual messages to main queue for immediate display
                String messagesDestination = USER_QUEUE_PREFIX + userId + "/messages";
                for (List<MessageResponseDto> conversationMessages : syncData.getMessagesByConversation().values()) {
                    for (MessageResponseDto message : conversationMessages) {
                        messagingTemplate.convertAndSend(messagesDestination, message);
                    }
                }

                log.info("✅ Smart offline sync complete: {} messages synced from MySQL", syncData.getTotalMissedMessages());
            } else {
                // Send sync complete notification even if no messages
                SyncCompleteDto syncComplete = SyncCompleteDto.builder()
                        .userId(userId)
                        .messageCount(0)
                        .syncedAt(java.time.LocalDateTime.now())
                        .build();

                messagingTemplate.convertAndSend(USER_QUEUE_PREFIX + userId + "/sync", syncComplete);
                log.info("✅ No offline messages to sync for user {}", userId);
            }

        } catch (Exception e) {
            log.error("❌ Error in smart offline sync for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Send chat history chunk to user (for scroll-up loading)
     */
    public void sendChatHistoryChunk(Long userId, ChatHistoryResponseDto historyData) {
        try {
            String destination = USER_QUEUE_PREFIX + userId + "/chat-history";
            messagingTemplate.convertAndSend(destination, historyData);

            log.info("📋 Sent chat history chunk: {} messages (page {}/{}) from {} to user {}",
                    historyData.getMessages().size(),
                    historyData.getCurrentPage() + 1,
                    historyData.getTotalPages(),
                    historyData.getLoadedFrom(),
                    userId);

        } catch (Exception e) {
            log.error("❌ Error sending chat history chunk to user {}: {}", userId, e.getMessage());
        }
    }


    // Helper DTOs for WebSocket messages
    public static class UnreadCountUpdateDto {
        private Long conversationId;
        private Integer unreadCount;

        public static UnreadCountUpdateDtoBuilder builder() {
            return new UnreadCountUpdateDtoBuilder();
        }

        public static class UnreadCountUpdateDtoBuilder {
            private Long conversationId;
            private Integer unreadCount;

            public UnreadCountUpdateDtoBuilder conversationId(Long conversationId) {
                this.conversationId = conversationId;
                return this;
            }

            public UnreadCountUpdateDtoBuilder unreadCount(Integer unreadCount) {
                this.unreadCount = unreadCount;
                return this;
            }

            public UnreadCountUpdateDto build() {
                UnreadCountUpdateDto dto = new UnreadCountUpdateDto();
                dto.conversationId = this.conversationId;
                dto.unreadCount = this.unreadCount;
                return dto;
            }
        }

        public Long getConversationId() { return conversationId; }
        public Integer getUnreadCount() { return unreadCount; }
    }

    public static class BroadcastMessageDto {
        private String type;
        private Long conversationId;
        private Object payload;
        private java.time.LocalDateTime timestamp;

        public static BroadcastMessageDtoBuilder builder() {
            return new BroadcastMessageDtoBuilder();
        }

        public static class BroadcastMessageDtoBuilder {
            private String type;
            private Long conversationId;
            private Object payload;
            private java.time.LocalDateTime timestamp;

            public BroadcastMessageDtoBuilder type(String type) {
                this.type = type;
                return this;
            }

            public BroadcastMessageDtoBuilder conversationId(Long conversationId) {
                this.conversationId = conversationId;
                return this;
            }

            public BroadcastMessageDtoBuilder payload(Object payload) {
                this.payload = payload;
                return this;
            }

            public BroadcastMessageDtoBuilder timestamp(java.time.LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public BroadcastMessageDto build() {
                BroadcastMessageDto dto = new BroadcastMessageDto();
                dto.type = this.type;
                dto.conversationId = this.conversationId;
                dto.payload = this.payload;
                dto.timestamp = this.timestamp;
                return dto;
            }
        }

        public String getType() { return type; }
        public Long getConversationId() { return conversationId; }
        public Object getPayload() { return payload; }
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * SyncComplete DTO - sent to client when offline message sync is complete
     */
    public static class SyncCompleteDto {
        private Long userId;
        private Integer messageCount;
        private java.time.LocalDateTime syncedAt;

        public static SyncCompleteDtoBuilder builder() {
            return new SyncCompleteDtoBuilder();
        }

        public static class SyncCompleteDtoBuilder {
            private Long userId;
            private Integer messageCount;
            private java.time.LocalDateTime syncedAt;

            public SyncCompleteDtoBuilder userId(Long userId) {
                this.userId = userId;
                return this;
            }

            public SyncCompleteDtoBuilder messageCount(Integer messageCount) {
                this.messageCount = messageCount;
                return this;
            }

            public SyncCompleteDtoBuilder syncedAt(java.time.LocalDateTime syncedAt) {
                this.syncedAt = syncedAt;
                return this;
            }

            public SyncCompleteDto build() {
                SyncCompleteDto dto = new SyncCompleteDto();
                dto.userId = this.userId;
                dto.messageCount = this.messageCount;
                dto.syncedAt = this.syncedAt;
                return dto;
            }
        }

        public Long getUserId() { return userId; }
        public Integer getMessageCount() { return messageCount; }
        public java.time.LocalDateTime getSyncedAt() { return syncedAt; }
    }

    /**
     * Send message reaction event to conversation members
     */
    public void sendMessageReaction(MessageReactionDto.ReactionEventDto reactionEvent) {
        try {
            log.debug("📡 Broadcasting reaction event: {} for message {} in conversation {}",
                    reactionEvent.getEventType(), reactionEvent.getMessageId(), reactionEvent.getConversationId());

            // Get conversation members
            List<Long> memberIds = conversationMemberRepository.findUserIdsByConversationId(reactionEvent.getConversationId());

            // Send to each conversation member
            for (Long memberId : memberIds) {
                // Check if user is online
                if (chatRedisService.isUserOnline(memberId)) {
                    String destination = USER_QUEUE_PREFIX + memberId + "/reaction";
                    messagingTemplate.convertAndSend(destination, reactionEvent);
                    log.debug("✅ Reaction event sent to user {} via {}", memberId, destination);
                }
            }

            // Also broadcast to conversation topic for real-time updates
            String conversationTopic = CONVERSATION_TOPIC_PREFIX + reactionEvent.getConversationId() + "/reaction";
            messagingTemplate.convertAndSend(conversationTopic, reactionEvent);
            log.debug("✅ Reaction event broadcasted to conversation topic: {}", conversationTopic);

        } catch (Exception e) {
            log.error("❌ Error sending reaction event for message {}: {}",
                    reactionEvent.getMessageId(), e.getMessage(), e);
        }
    }
}
