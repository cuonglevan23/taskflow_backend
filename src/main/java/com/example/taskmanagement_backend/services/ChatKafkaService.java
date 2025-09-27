package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ChatDto.*;
import com.example.taskmanagement_backend.dtos.MessageReactionDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Kafka service for handling real-time chat messaging
 * Produces and consumes events for direct messages, group messages, and message status updates
 */
@Slf4j
@Service
public class ChatKafkaService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ChatWebSocketService chatWebSocketService;
    private final ObjectMapper objectMapper;

    public ChatKafkaService(@Qualifier("chatKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
                           @Lazy ChatWebSocketService chatWebSocketService, // ✅ Break circular dependency with @Lazy
                           ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.chatWebSocketService = chatWebSocketService;
        this.objectMapper = objectMapper;
    }

    // Kafka topic names
    private static final String CHAT_DIRECT_TOPIC = "chat.direct";
    private static final String CHAT_GROUP_TOPIC = "chat.group";
    private static final String CHAT_MESSAGE_STATUS_TOPIC = "chat.message.status";
    private static final String CHAT_TYPING_STATUS_TOPIC = "chat.typing.status";
    private static final String CHAT_ONLINE_STATUS_TOPIC = "chat.online.status";
    private static final String CHAT_REACTION_TOPIC = "chat.reaction"; // 🆕 Topic for message reactions

    /**
     * Publish direct message to Kafka
     */
    public void publishDirectMessage(MessageResponseDto message) {
        try {
            kafkaTemplate.send(CHAT_DIRECT_TOPIC, message.getConversationId().toString(), message)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Direct message sent successfully for conversation {}", message.getConversationId());
                        } else {
                            log.error("Failed to send direct message for conversation {}: {}",
                                message.getConversationId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing direct message for conversation {}: {}", message.getConversationId(), e.getMessage());
        }
    }

    /**
     * Publish group message to Kafka
     */
    public void publishGroupMessage(MessageResponseDto message) {
        try {
            kafkaTemplate.send(CHAT_GROUP_TOPIC, message.getConversationId().toString(), message)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Group message sent successfully for conversation {}", message.getConversationId());
                        } else {
                            log.error("Failed to send group message for conversation {}: {}",
                                message.getConversationId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing group message for conversation {}: {}", message.getConversationId(), e.getMessage());
        }
    }

    /**
     * Publish message read status to Kafka
     */
    public void publishMessageReadStatus(MessageReadStatusDto readStatus) {
        try {
            kafkaTemplate.send(CHAT_MESSAGE_STATUS_TOPIC, readStatus.getMessageId().toString(), readStatus)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Message read status sent successfully for message {}", readStatus.getMessageId());
                        } else {
                            log.error("Failed to send message read status for message {}: {}",
                                readStatus.getMessageId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing message read status for message {}: {}", readStatus.getMessageId(), e.getMessage());
        }
    }

    /**
     * Publish typing status to Kafka
     */
    public void publishTypingStatus(TypingStatusDto typingStatus) {
        try {
            kafkaTemplate.send(CHAT_TYPING_STATUS_TOPIC, typingStatus.getConversationId().toString(), typingStatus)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Typing status sent successfully for conversation {}", typingStatus.getConversationId());
                        } else {
                            log.error("Failed to send typing status for conversation {}: {}",
                                typingStatus.getConversationId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing typing status for conversation {}: {}", typingStatus.getConversationId(), e.getMessage());
        }
    }

    /**
     * Publish online status to Kafka
     */
    public void publishOnlineStatus(OnlineStatusDto onlineStatus) {
        try {
            kafkaTemplate.send(CHAT_ONLINE_STATUS_TOPIC, onlineStatus.getUserId().toString(), onlineStatus)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Online status sent successfully for user {}", onlineStatus.getUserId());
                        } else {
                            log.error("Failed to send online status for user {}: {}",
                                onlineStatus.getUserId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing online status for user {}: {}", onlineStatus.getUserId(), e.getMessage());
        }
    }

    /**
     * Publish bulk message read status update to Kafka
     */
    public void publishBulkMessageReadStatus(BulkMessageReadStatusDto bulkReadStatus) {
        try {
            kafkaTemplate.send(CHAT_MESSAGE_STATUS_TOPIC, bulkReadStatus.getConversationId().toString(), bulkReadStatus)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("📡 Bulk read status published for conversation: {} ({} messages)",
                                    bulkReadStatus.getConversationId(), bulkReadStatus.getMessageIds().size());
                        } else {
                            log.error("❌ Failed to publish bulk read status: {}", ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("❌ Error publishing bulk message read status: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish message reaction event to Kafka
     */
    public void publishReactionEvent(MessageReactionDto.ReactionEventDto reactionEvent) {
        try {
            kafkaTemplate.send(CHAT_REACTION_TOPIC, reactionEvent.getConversationId().toString(), reactionEvent)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Reaction event sent successfully for message {}", reactionEvent.getMessageId());
                        } else {
                            log.error("Failed to send reaction event for message {}: {}",
                                reactionEvent.getMessageId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing reaction event for message {}: {}", reactionEvent.getMessageId(), e.getMessage());
        }
    }

    /**
     * Consumer for direct messages
     */
    @KafkaListener(topics = CHAT_DIRECT_TOPIC, containerFactory = "chatKafkaListenerContainerFactory")
    public void handleDirectMessage(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        try {
            Object messagePayload = record.value();
            log.debug("Received direct message from topic: {}, partition: {}, offset: {}, payload type: {}",
                record.topic(), record.partition(), record.offset(),
                messagePayload != null ? messagePayload.getClass().getSimpleName() : "null");

            if (messagePayload == null) {
                log.warn("Received null payload for direct message. Skipping.");
                acknowledgment.acknowledge();
                return;
            }

            // Convert the payload to MessageResponseDto
            MessageResponseDto message = objectMapper.convertValue(messagePayload, MessageResponseDto.class);

            // Validate required fields
            if (message.getConversationId() == null) {
                log.warn("Direct message missing conversationId. Skipping message.");
                acknowledgment.acknowledge();
                return;
            }

            log.debug("Processing direct message for conversation: {}", message.getConversationId());

            // Send message to WebSocket clients
            chatWebSocketService.sendDirectMessage(message);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing direct message: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent infinite retry
        }
    }

    /**
     * Consumer for group messages
     */
    @KafkaListener(topics = CHAT_GROUP_TOPIC, containerFactory = "chatKafkaListenerContainerFactory")
    public void handleGroupMessage(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        try {
            Object messagePayload = record.value();
            log.debug("Received group message from topic: {}, partition: {}, offset: {}, payload type: {}",
                record.topic(), record.partition(), record.offset(),
                messagePayload != null ? messagePayload.getClass().getSimpleName() : "null");

            if (messagePayload == null) {
                log.warn("Received null payload for group message. Skipping.");
                acknowledgment.acknowledge();
                return;
            }

            // Convert the payload to MessageResponseDto
            MessageResponseDto message = objectMapper.convertValue(messagePayload, MessageResponseDto.class);

            // Validate required fields
            if (message.getConversationId() == null) {
                log.warn("Group message missing conversationId. Skipping message.");
                acknowledgment.acknowledge();
                return;
            }

            log.debug("Processing group message for conversation: {}", message.getConversationId());

            // Send message to WebSocket clients
            chatWebSocketService.sendGroupMessage(message);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing group message: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    /**
     * Consumer for message read status updates
     */
    @KafkaListener(topics = CHAT_MESSAGE_STATUS_TOPIC, containerFactory = "chatKafkaListenerContainerFactory")
    public void handleMessageReadStatus(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        try {
            Object messagePayload = record.value();
            log.debug("Received message read status from topic: {}, partition: {}, offset: {}, payload type: {}",
                record.topic(), record.partition(), record.offset(),
                messagePayload != null ? messagePayload.getClass().getSimpleName() : "null");

            if (messagePayload == null) {
                log.warn("Received null payload for message read status. Skipping.");
                acknowledgment.acknowledge();
                return;
            }

            // Convert the payload to MessageReadStatusDto
            MessageReadStatusDto readStatus = objectMapper.convertValue(messagePayload, MessageReadStatusDto.class);

            // Validate required fields
            if (readStatus.getMessageId() == null) {
                log.warn("Message read status missing messageId. Skipping message.");
                acknowledgment.acknowledge();
                return;
            }

            log.debug("Processing message read status for message: {}", readStatus.getMessageId());

            // Send read status to WebSocket clients
            chatWebSocketService.sendMessageReadStatus(readStatus);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing message read status: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent infinite retry
        }
    }

    /**
     * Consumer for typing status updates
     */
    @KafkaListener(topics = CHAT_TYPING_STATUS_TOPIC, containerFactory = "chatKafkaListenerContainerFactory")
    public void handleTypingStatus(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        try {
            Object messagePayload = record.value();
            log.debug("Received typing status from topic: {}, partition: {}, offset: {}, payload type: {}",
                record.topic(), record.partition(), record.offset(),
                messagePayload != null ? messagePayload.getClass().getSimpleName() : "null");

            if (messagePayload == null) {
                log.warn("Received null payload for typing status. Skipping.");
                acknowledgment.acknowledge();
                return;
            }

            // Convert the payload to TypingStatusDto
            TypingStatusDto typingStatus = objectMapper.convertValue(messagePayload, TypingStatusDto.class);

            // Validate the message before processing
            if (typingStatus.getConversationId() == null || typingStatus.getUserId() == null) {
                log.warn("Invalid typing status message - missing required fields. ConversationId: {}, UserId: {}",
                        typingStatus.getConversationId(), typingStatus.getUserId());
                acknowledgment.acknowledge(); // Acknowledge to skip this message
                return;
            }

            log.debug("Processing typing status for conversation: {}, user: {}, isTyping: {}",
                typingStatus.getConversationId(), typingStatus.getUserId(), typingStatus.getIsTyping());

            // Send typing status to WebSocket clients
            chatWebSocketService.sendTypingStatus(typingStatus);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing typing status: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge even on error to prevent infinite retry
        }
    }

    /**
     * Consumer for online status updates
     */
    @KafkaListener(topics = CHAT_ONLINE_STATUS_TOPIC, containerFactory = "chatKafkaListenerContainerFactory")
    public void handleOnlineStatus(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        try {
            Object messagePayload = record.value();
            log.debug("Received online status from topic: {}, partition: {}, offset: {}, payload type: {}",
                record.topic(), record.partition(), record.offset(),
                messagePayload != null ? messagePayload.getClass().getSimpleName() : "null");

            if (messagePayload == null) {
                log.warn("Received null payload for online status. Skipping.");
                acknowledgment.acknowledge();
                return;
            }

            // Convert the payload to OnlineStatusDto
            OnlineStatusDto onlineStatus = objectMapper.convertValue(messagePayload, OnlineStatusDto.class);

            // Validate the message before processing
            if (onlineStatus.getUserId() == null || onlineStatus.getStatus() == null) {
                log.warn("Invalid online status message - missing required fields. UserId: {}, Status: {}",
                        onlineStatus.getUserId(), onlineStatus.getStatus());
                acknowledgment.acknowledge(); // Acknowledge to skip this message
                return;
            }

            log.debug("Processing online status for user: {}, status: {}",
                onlineStatus.getUserId(), onlineStatus.getStatus());

            // Send online status to WebSocket clients
            chatWebSocketService.sendOnlineStatus(onlineStatus);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing online status: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge even on error to prevent infinite retry
        }
    }

    /**
     * Consumer for message reactions
     */
    @KafkaListener(topics = CHAT_REACTION_TOPIC, containerFactory = "chatKafkaListenerContainerFactory")
    public void handleMessageReaction(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        try {
            Object messagePayload = record.value();
            log.debug("Received message reaction from topic: {}, partition: {}, offset: {}, payload type: {}",
                record.topic(), record.partition(), record.offset(),
                messagePayload != null ? messagePayload.getClass().getSimpleName() : "null");

            if (messagePayload == null) {
                log.warn("Received null payload for message reaction. Skipping.");
                acknowledgment.acknowledge();
                return;
            }

            // Convert the payload to ReactionEventDto
            MessageReactionDto.ReactionEventDto reactionEvent = objectMapper.convertValue(messagePayload, MessageReactionDto.ReactionEventDto.class);

            // Validate required fields
            if (reactionEvent.getMessageId() == null || reactionEvent.getUserId() == null) {
                log.warn("Message reaction missing messageId or userId. Skipping reaction.");
                acknowledgment.acknowledge();
                return;
            }

            log.debug("Processing message reaction for message: {}, user: {}, reaction: {}, event: {}",
                reactionEvent.getMessageId(), reactionEvent.getUserId(), reactionEvent.getReactionType(), reactionEvent.getEventType());

            // Send reaction to WebSocket clients
            chatWebSocketService.sendMessageReaction(reactionEvent);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing message reaction: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent infinite retry
        }
    }
}
