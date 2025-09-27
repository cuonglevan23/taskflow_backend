package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.ChatDto.*;
import com.example.taskmanagement_backend.services.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * 💬 WebSocket controller for chat-related real-time messaging
 * Handles subscription to conversations and auto-marking messages as read
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;

    /**
     * 🔔 Subscribe to conversation and auto-mark messages as read
     * When user subscribes to a conversation via WebSocket, automatically mark all unread messages as read
     */
    @SubscribeMapping("/topic/conversation/{conversationId}")
    public void subscribeToConversation(@DestinationVariable Long conversationId,
                                       SimpMessageHeaderAccessor headerAccessor) {
        try {
            // Get user ID from WebSocket session attributes
            Long userId = getUserIdFromSession(headerAccessor);
            if (userId == null) {
                log.warn("⚠️ User not authenticated for conversation subscription: {}", conversationId);
                return;
            }

            log.info("📱 User {} subscribing to conversation {} via WebSocket", userId, conversationId);

            // Auto-mark all unread messages as read when user joins conversation
            chatService.autoMarkMessagesAsReadOnJoinConversation(userId, conversationId);

            log.debug("✅ Successfully processed subscription for user {} in conversation {}", userId, conversationId);

        } catch (Exception e) {
            log.error("❌ Error processing conversation subscription for conversation {}: {}",
                    conversationId, e.getMessage(), e);
        }
    }

    /**
     * 📨 Send message via WebSocket
     */
    @MessageMapping("/chat/send")
    public void sendMessage(@Payload SendMessageRequestDto message, SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long userId = getUserIdFromSession(headerAccessor);
            if (userId == null) {
                log.warn("⚠️ User not authenticated for sending message");
                return;
            }

            log.debug("📤 Sending message from user {} to conversation {}", userId, message.getConversationId());

            // Send message through ChatService (which handles Kafka publishing)
            chatService.sendMessage(userId, message);

        } catch (Exception e) {
            log.error("❌ Error sending message via WebSocket: {}", e.getMessage(), e);
        }
    }

    /**
     * ✅ Mark specific message as read via WebSocket
     */
    @MessageMapping("/chat/markAsRead/{messageId}")
    public void markMessageAsRead(@DestinationVariable Long messageId, SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long userId = getUserIdFromSession(headerAccessor);
            if (userId == null) {
                log.warn("⚠️ User not authenticated for marking message as read");
                return;
            }

            log.debug("✅ Marking message {} as read for user {}", messageId, userId);

            chatService.markMessageAsRead(userId, messageId);

        } catch (Exception e) {
            log.error("❌ Error marking message as read via WebSocket: {}", e.getMessage(), e);
        }
    }

    /**
     * ⌨️ Handle typing status via WebSocket
     */
    @MessageMapping("/chat/typing")
    public void handleTypingStatus(@Payload TypingStatusDto typingStatus, SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long userId = getUserIdFromSession(headerAccessor);
            if (userId == null) {
                log.warn("⚠️ User not authenticated for typing status");
                return;
            }

            // Set user ID from session
            typingStatus.setUserId(userId);

            log.debug("⌨️ Typing status from user {} in conversation {}: {}",
                    userId, typingStatus.getConversationId(), typingStatus.getIsTyping());

            // TODO: Publish typing status via Kafka if needed
            // chatKafkaService.publishTypingStatus(typingStatus);

        } catch (Exception e) {
            log.error("❌ Error handling typing status via WebSocket: {}", e.getMessage(), e);
        }
    }

    /**
     * 📊 Get conversation stats via WebSocket
     */
    @MessageMapping("/chat/conversation/{conversationId}/stats")
    public void getConversationStats(@DestinationVariable Long conversationId,
                                    SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long userId = getUserIdFromSession(headerAccessor);
            if (userId == null) {
                log.warn("⚠️ User not authenticated for conversation stats");
                return;
            }

            log.debug("📊 Getting stats for conversation {} requested by user {}", conversationId, userId);

            // Get unread count and other stats
            Integer unreadCount = chatService.getUnreadMessageCount(userId, conversationId);
            boolean hasUnread = chatService.hasUnreadMessages(userId, conversationId);

            log.debug("📈 Conversation {} stats for user {}: unread={}, hasUnread={}",
                    conversationId, userId, unreadCount, hasUnread);

        } catch (Exception e) {
            log.error("❌ Error getting conversation stats via WebSocket: {}", e.getMessage(), e);
        }
    }

    /**
     * 🔐 Extract user ID from WebSocket session attributes
     */
    private Long getUserIdFromSession(SimpMessageHeaderAccessor headerAccessor) {
        try {
            Object userIdObj = headerAccessor.getSessionAttributes().get("userId");
            if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            } else if (userIdObj instanceof Integer) {
                return ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof String) {
                return Long.parseLong((String) userIdObj);
            }

            log.warn("⚠️ Invalid or missing userId in WebSocket session attributes: {}", userIdObj);
            return null;

        } catch (Exception e) {
            log.error("❌ Error extracting userId from WebSocket session: {}", e.getMessage());
            return null;
        }
    }
}
