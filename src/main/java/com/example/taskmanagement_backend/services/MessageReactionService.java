package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.MessageReactionDto.*;
import com.example.taskmanagement_backend.entities.Message;
import com.example.taskmanagement_backend.entities.MessageReaction;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.ReactionType;
import com.example.taskmanagement_backend.repositories.MessageReactionRepository;
import com.example.taskmanagement_backend.repositories.MessageRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.repositories.ConversationMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for handling message reactions
 * Supports adding, removing, and querying reactions for chat messages
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageReactionService {

    private final MessageReactionRepository reactionRepository;
    private final MessageRepository messageRepository;
    private final UserJpaRepository userRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ChatKafkaService chatKafkaService;

    /**
     * Add reaction to a message
     */
    @Transactional
    public ReactionEventDto addReaction(Long messageId, Long userId, String reactionType) {
        try {
            // Validate reaction type
            if (!isValidReactionType(reactionType)) {
                throw new IllegalArgumentException("Invalid reaction type: " + reactionType);
            }

            // Get message and verify access
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message not found"));

            // Verify user is member of conversation
            conversationMemberRepository.findByConversationIdAndUserId(message.getConversation().getId(), userId)
                    .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if reaction already exists
            Optional<MessageReaction> existingReaction = reactionRepository
                    .findByMessageIdAndUserIdAndReactionType(messageId, userId, reactionType);

            if (existingReaction.isPresent()) {
                log.debug("Reaction already exists for user {} on message {} with type {}",
                         userId, messageId, reactionType);
                return createReactionEvent("REACTION_UNCHANGED", message, user, reactionType);
            }

            // Remove any existing reactions by this user for this message (one reaction per user per message)
            reactionRepository.deleteByMessageIdAndUserId(messageId, userId);

            // Create new reaction
            MessageReaction reaction = MessageReaction.builder()
                    .message(message)
                    .user(user)
                    .reactionType(reactionType)
                    .build();

            reactionRepository.save(reaction);

            log.info("✅ Added reaction {} by user {} to message {}", reactionType, userId, messageId);

            // Create and publish event
            ReactionEventDto event = createReactionEvent("REACTION_ADDED", message, user, reactionType);
            publishReactionEvent(event);

            return event;

        } catch (Exception e) {
            log.error("❌ Error adding reaction: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add reaction: " + e.getMessage());
        }
    }

    /**
     * Remove reaction from a message
     */
    @Transactional
    public ReactionEventDto removeReaction(Long messageId, Long userId, String reactionType) {
        try {
            // Get message and verify access
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message not found"));

            // Verify user is member of conversation
            conversationMemberRepository.findByConversationIdAndUserId(message.getConversation().getId(), userId)
                    .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Find and remove reaction
            Optional<MessageReaction> reaction = reactionRepository
                    .findByMessageIdAndUserIdAndReactionType(messageId, userId, reactionType);

            if (reaction.isEmpty()) {
                log.debug("No reaction found to remove for user {} on message {} with type {}",
                         userId, messageId, reactionType);
                return createReactionEvent("REACTION_NOT_FOUND", message, user, reactionType);
            }

            reactionRepository.delete(reaction.get());

            log.info("✅ Removed reaction {} by user {} from message {}", reactionType, userId, messageId);

            // Create and publish event
            ReactionEventDto event = createReactionEvent("REACTION_REMOVED", message, user, reactionType);
            publishReactionEvent(event);

            return event;

        } catch (Exception e) {
            log.error("❌ Error removing reaction: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to remove reaction: " + e.getMessage());
        }
    }

    /**
     * Toggle reaction (add if not exists, remove if exists)
     */
    @Transactional
    public ReactionEventDto toggleReaction(Long messageId, Long userId, String reactionType) {
        Optional<MessageReaction> existingReaction = reactionRepository
                .findByMessageIdAndUserIdAndReactionType(messageId, userId, reactionType);

        if (existingReaction.isPresent()) {
            return removeReaction(messageId, userId, reactionType);
        } else {
            return addReaction(messageId, userId, reactionType);
        }
    }

    /**
     * Get reaction summary for a message
     */
    @Transactional(readOnly = true)
    public MessageReactionSummaryDto getMessageReactionSummary(Long messageId, Long currentUserId) {
        try {
            // Get all reactions for the message
            List<MessageReaction> reactions = reactionRepository.findByMessageIdOrderByCreatedAtAsc(messageId);

            // Build reaction counts
            Map<String, Integer> reactionCounts = new HashMap<>();
            Map<String, List<ReactionUserDto>> reactionUsers = new HashMap<>();
            List<String> currentUserReactions = new ArrayList<>();

            for (MessageReaction reaction : reactions) {
                String type = reaction.getReactionType();

                // Count reactions
                reactionCounts.merge(type, 1, Integer::sum);

                // Track users who reacted
                ReactionUserDto userDto = ReactionUserDto.builder()
                        .userId(reaction.getUser().getId())
                        .userName(getUserFullName(reaction.getUser()))
                        .userAvatar(getUserAvatar(reaction.getUser()))
                        .reactedAt(reaction.getCreatedAt())
                        .build();

                reactionUsers.computeIfAbsent(type, k -> new ArrayList<>()).add(userDto);

                // Track current user's reactions
                if (reaction.getUser().getId().equals(currentUserId)) {
                    currentUserReactions.add(type);
                }
            }

            int totalReactions = reactions.size();

            return MessageReactionSummaryDto.builder()
                    .messageId(messageId)
                    .reactionCounts(reactionCounts)
                    .reactionUsers(reactionUsers)
                    .currentUserReactions(currentUserReactions)
                    .totalReactions(totalReactions)
                    .hasReactions(totalReactions > 0)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error getting reaction summary for message {}: {}", messageId, e.getMessage());
            return MessageReactionSummaryDto.builder()
                    .messageId(messageId)
                    .reactionCounts(Map.of())
                    .reactionUsers(Map.of())
                    .currentUserReactions(List.of())
                    .totalReactions(0)
                    .hasReactions(false)
                    .build();
        }
    }

    /**
     * Get bulk reaction summaries for multiple messages
     */
    @Transactional(readOnly = true)
    public BulkReactionSummaryDto getBulkReactionSummaries(Long conversationId, List<Long> messageIds, Long currentUserId) {
        try {
            Map<Long, MessageReactionSummaryDto> messageReactions = new HashMap<>();

            // Get all reactions for the messages in one query
            List<MessageReaction> reactions = reactionRepository.findReactionsForMessages(conversationId, messageIds);

            // Group reactions by message ID
            Map<Long, List<MessageReaction>> reactionsByMessage = reactions.stream()
                    .collect(Collectors.groupingBy(r -> r.getMessage().getId()));

            // Build summary for each message
            for (Long messageId : messageIds) {
                List<MessageReaction> messageReactions_ = reactionsByMessage.getOrDefault(messageId, List.of());
                MessageReactionSummaryDto summary = buildReactionSummary(messageId, messageReactions_, currentUserId);
                messageReactions.put(messageId, summary);
            }

            return BulkReactionSummaryDto.builder()
                    .conversationId(conversationId)
                    .messageReactions(messageReactions)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("❌ Error getting bulk reaction summaries: {}", e.getMessage());
            return BulkReactionSummaryDto.builder()
                    .conversationId(conversationId)
                    .messageReactions(Map.of())
                    .generatedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Get available reaction types
     */
    public Map<String, String> getAvailableReactionTypes() {
        Map<String, String> reactions = new LinkedHashMap<>();
        for (ReactionType type : ReactionType.values()) {
            reactions.put(type.name(), type.getEmoji());
        }
        return reactions;
    }

    private boolean isValidReactionType(String reactionType) {
        try {
            ReactionType.valueOf(reactionType);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private ReactionEventDto createReactionEvent(String eventType, Message message, User user, String reactionType) {
        ReactionType type = ReactionType.valueOf(reactionType);
        MessageReactionSummaryDto summary = getMessageReactionSummary(message.getId(), user.getId());

        return ReactionEventDto.builder()
                .eventType(eventType)
                .messageId(message.getId())
                .conversationId(message.getConversation().getId())
                .userId(user.getId())
                .userName(getUserFullName(user))
                .userAvatar(getUserAvatar(user))
                .reactionType(reactionType)
                .emoji(type.getEmoji())
                .updatedSummary(summary)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private MessageReactionSummaryDto buildReactionSummary(Long messageId, List<MessageReaction> reactions, Long currentUserId) {
        Map<String, Integer> reactionCounts = new HashMap<>();
        Map<String, List<ReactionUserDto>> reactionUsers = new HashMap<>();
        List<String> currentUserReactions = new ArrayList<>();

        for (MessageReaction reaction : reactions) {
            String type = reaction.getReactionType();

            reactionCounts.merge(type, 1, Integer::sum);

            ReactionUserDto userDto = ReactionUserDto.builder()
                    .userId(reaction.getUser().getId())
                    .userName(getUserFullName(reaction.getUser()))
                    .userAvatar(getUserAvatar(reaction.getUser()))
                    .reactedAt(reaction.getCreatedAt())
                    .build();

            reactionUsers.computeIfAbsent(type, k -> new ArrayList<>()).add(userDto);

            if (reaction.getUser().getId().equals(currentUserId)) {
                currentUserReactions.add(type);
            }
        }

        int totalReactions = reactions.size();

        return MessageReactionSummaryDto.builder()
                .messageId(messageId)
                .reactionCounts(reactionCounts)
                .reactionUsers(reactionUsers)
                .currentUserReactions(currentUserReactions)
                .totalReactions(totalReactions)
                .hasReactions(totalReactions > 0)
                .build();
    }

    private void publishReactionEvent(ReactionEventDto event) {
        try {
            chatKafkaService.publishReactionEvent(event);
            log.debug("📡 Published reaction event: {} for message {}", event.getEventType(), event.getMessageId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish reaction event: {}", e.getMessage());
        }
    }

    private String getUserFullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }

    private String getUserAvatar(User user) {
        return user.getAvatarUrl();
    }
}
