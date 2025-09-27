package com.example.taskmanagement_backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTOs for message reaction functionality
 */
public class MessageReactionDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddReactionRequestDto {
        private String reactionType; // LIKE, LOVE, LAUGH, WOW, SAD, ANGRY
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemoveReactionRequestDto {
        private String reactionType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionResponseDto {
        private Long id;
        private Long messageId;
        private Long userId;
        private String userName;
        private String userAvatar;
        private String reactionType;
        private String emoji;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageReactionSummaryDto {
        private Long messageId;
        private Map<String, Integer> reactionCounts; // {"LIKE": 5, "LOVE": 2}
        private Map<String, List<ReactionUserDto>> reactionUsers; // Users who reacted with each type
        private List<String> currentUserReactions; // Reactions by current user
        private Integer totalReactions;
        private Boolean hasReactions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionUserDto {
        private Long userId;
        private String userName;
        private String userAvatar;
        private LocalDateTime reactedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionEventDto {
        private String eventType; // REACTION_ADDED, REACTION_REMOVED, REACTION_UPDATED
        private Long messageId;
        private Long conversationId;
        private Long userId;
        private String userName;
        private String userAvatar;
        private String reactionType;
        private String emoji;
        private MessageReactionSummaryDto updatedSummary;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkReactionSummaryDto {
        private Long conversationId;
        private Map<Long, MessageReactionSummaryDto> messageReactions; // messageId -> summary
        private LocalDateTime generatedAt;
    }
}
