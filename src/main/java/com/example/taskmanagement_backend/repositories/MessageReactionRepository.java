package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    /**
     * Find all reactions for a specific message
     */
    List<MessageReaction> findByMessageIdOrderByCreatedAtAsc(Long messageId);

    /**
     * Find specific reaction by user and message
     */
    Optional<MessageReaction> findByMessageIdAndUserIdAndReactionType(Long messageId, Long userId, String reactionType);

    /**
     * Find all reactions by a user for a specific message
     */
    List<MessageReaction> findByMessageIdAndUserId(Long messageId, Long userId);

    /**
     * Count reactions by type for a message
     */
    @Query("SELECT r.reactionType, COUNT(r) FROM MessageReaction r WHERE r.message.id = :messageId GROUP BY r.reactionType")
    List<Object[]> countReactionsByTypeForMessage(@Param("messageId") Long messageId);

    /**
     * Get reaction summary for a message with user details
     * Fixed to use UserProfile relationship for firstName, lastName, and avtUrl
     */
    @Query("SELECT r.reactionType, r.user.id, r.user.userProfile.firstName, r.user.userProfile.lastName, r.user.userProfile.avtUrl, r.createdAt " +
           "FROM MessageReaction r " +
           "WHERE r.message.id = :messageId " +
           "ORDER BY r.reactionType, r.createdAt ASC")
    List<Object[]> getReactionSummaryForMessage(@Param("messageId") Long messageId);

    /**
     * Check if user has reacted to a message with any reaction
     */
    boolean existsByMessageIdAndUserId(Long messageId, Long userId);

    /**
     * Delete all reactions by user for a message
     */
    void deleteByMessageIdAndUserId(Long messageId, Long userId);

    /**
     * Delete specific reaction
     */
    void deleteByMessageIdAndUserIdAndReactionType(Long messageId, Long userId, String reactionType);

    /**
     * Get messages with reactions in a conversation (for batch loading)
     */
    @Query("SELECT r FROM MessageReaction r WHERE r.message.conversation.id = :conversationId " +
           "AND r.message.id IN :messageIds ORDER BY r.message.id, r.reactionType, r.createdAt")
    List<MessageReaction> findReactionsForMessages(@Param("conversationId") Long conversationId,
                                                  @Param("messageIds") List<Long> messageIds);
}
