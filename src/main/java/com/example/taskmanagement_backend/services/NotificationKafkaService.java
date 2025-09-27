package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.NotificationDto.CreateNotificationRequestDto;
import com.example.taskmanagement_backend.dtos.ChatDto.*;
import com.example.taskmanagement_backend.enums.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Enhanced Kafka service for handling both notification and chat events
 * Integrates with the new chat system for real-time messaging
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationKafkaService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final NotificationService notificationService;
    private final ChatWebSocketService chatWebSocketService;
    private final ObjectMapper objectMapper;

    // Kafka topic names
    private static final String NOTIFICATION_EVENTS_TOPIC = "notification.events";
    private static final String CHAT_MESSAGES_TOPIC = "chat.messages";
    private static final String SYSTEM_NOTIFICATIONS_TOPIC = "system.notifications";

    /**
     * Publish notification event to Kafka
     */
    public void publishNotificationEvent(CreateNotificationRequestDto notification) {
        try {
            kafkaTemplate.send(NOTIFICATION_EVENTS_TOPIC, notification.getUserId().toString(), notification)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Notification event sent successfully for user {}", notification.getUserId());
                        } else {
                            log.error("Failed to send notification event for user {}: {}",
                                notification.getUserId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing notification event for user {}: {}", notification.getUserId(), e.getMessage());
        }
    }

    /**
     * Publish chat message event to Kafka (integrated with new chat system)
     */
    public void publishChatMessageEvent(MessageResponseDto chatMessage) {
        try {
            kafkaTemplate.send(CHAT_MESSAGES_TOPIC, chatMessage.getConversationId().toString(), chatMessage)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Chat message event sent successfully for conversation {}", chatMessage.getConversationId());
                        } else {
                            log.error("Failed to send chat message event for conversation {}: {}",
                                chatMessage.getConversationId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing chat message event for conversation {}: {}", chatMessage.getConversationId(), e.getMessage());
        }
    }

    /**
     * Publish system notification event to Kafka
     */
    public void publishSystemNotificationEvent(CreateNotificationRequestDto notification) {
        try {
            kafkaTemplate.send(SYSTEM_NOTIFICATIONS_TOPIC, notification.getUserId().toString(), notification)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("System notification event sent successfully for user {}", notification.getUserId());
                        } else {
                            log.error("Failed to send system notification event for user {}: {}",
                                notification.getUserId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing system notification event for user {}: {}", notification.getUserId(), e.getMessage());
        }
    }

    /**
     * Consumer for notification events
     */
    @KafkaListener(topics = NOTIFICATION_EVENTS_TOPIC, containerFactory = "notificationKafkaListenerContainerFactory")
    public void handleNotificationEvent(
            @Payload CreateNotificationRequestDto notification,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.debug("Received notification event from topic: {}, partition: {}, offset: {}, user: {}",
                topic, partition, offset, notification.getUserId());

            // Process the notification
            notificationService.createAndSendNotification(notification);

            // Send real-time notification via WebSocket
            chatWebSocketService.sendNotificationToUser(notification.getUserId(), notification);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing notification event for user {}: {}", notification.getUserId(), e.getMessage());
            acknowledgment.acknowledge();
        }
    }

    /**
     * Consumer for chat message events (enhanced for new chat system)
     */
    @KafkaListener(topics = CHAT_MESSAGES_TOPIC, containerFactory = "notificationKafkaListenerContainerFactory")
    public void handleChatMessageEvent(
            @Payload MessageResponseDto chatMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.debug("Received chat message event from topic: {}, partition: {}, offset: {}, conversation: {}",
                topic, partition, offset, chatMessage.getConversationId());

            // Send message via WebSocket to conversation members
            chatWebSocketService.sendDirectMessage(chatMessage);

            // Create notification for mentioned users or important messages
            createChatNotificationIfNeeded(chatMessage);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing chat message event for conversation {}: {}", chatMessage.getConversationId(), e.getMessage());
            acknowledgment.acknowledge();
        }
    }

    /**
     * Consumer for system notification events
     */
    @KafkaListener(topics = SYSTEM_NOTIFICATIONS_TOPIC, containerFactory = "notificationKafkaListenerContainerFactory")
    public void handleSystemNotificationEvent(
            @Payload CreateNotificationRequestDto notification,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.debug("Received system notification event from topic: {}, partition: {}, offset: {}, user: {}",
                topic, partition, offset, notification.getUserId());

            // Process system notifications (maintenance, updates, announcements)
            notificationService.createAndSendNotification(notification);

            // Send real-time system notification via WebSocket
            chatWebSocketService.sendNotificationToUser(notification.getUserId(), notification);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing system notification event for user {}: {}", notification.getUserId(), e.getMessage());
            acknowledgment.acknowledge();
        }
    }

    /**
     * Create notification for chat messages when needed (mentions, important messages, etc.)
     */
    private void createChatNotificationIfNeeded(MessageResponseDto chatMessage) {
        try {
            // Check if message contains mentions (@username)
            if (chatMessage.getContent() != null && chatMessage.getContent().contains("@")) {
                // Parse mentions and create notifications
                // This is a simplified implementation - you'd want more sophisticated mention parsing
                CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                        .userId(chatMessage.getSenderId()) // This would be the mentioned user's ID
                        .title("You were mentioned in a chat")
                        .content(String.format("%s mentioned you: %s",
                            chatMessage.getSenderName(),
                            chatMessage.getContent().substring(0, Math.min(50, chatMessage.getContent().length()))))
                        .type(NotificationType.CHAT_MENTION)
                        .build();

                publishNotificationEvent(notification);
            }

        } catch (Exception e) {
            log.error("Error creating chat notification for message {}: {}", chatMessage.getId(), e.getMessage());
        }
    }
}
