package com.example.taskmanagement_backend.agent.memory;

import com.example.taskmanagement_backend.agent.entity.ChatMessage;
import com.example.taskmanagement_backend.agent.enums.SenderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ChatMessage Repository - JDBC/MySQL/JPA repository
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByMessageIdAndIsDeletedFalse(String messageId);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.conversationId = :conversationId AND m.isDeleted = false ORDER BY m.createdAt ASC")
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(@Param("conversationId") String conversationId);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.conversationId = :conversationId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    Page<ChatMessage> findByConversationIdOrderByCreatedAtDesc(@Param("conversationId") String conversationId, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.conversationId = :conversationId AND m.senderType = :senderType AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<ChatMessage> findByConversationIdAndSenderType(@Param("conversationId") String conversationId, @Param("senderType") SenderType senderType);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.conversation.conversationId = :conversationId AND m.isDeleted = false")
    Long countByConversationId(@Param("conversationId") String conversationId);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.conversationId = :conversationId AND m.isDeleted = false ORDER BY m.createdAt DESC LIMIT :limit")
    List<ChatMessage> findRecentMessages(@Param("conversationId") String conversationId, @Param("limit") int limit);

    @Query("SELECT m FROM ChatMessage m WHERE m.userId = :userId AND m.sessionId = :sessionId AND m.createdAt BETWEEN :startDate AND :endDate ORDER BY m.createdAt DESC")
    Page<ChatMessage> findByUserIdAndSessionIdAndCreatedAtBetween(@Param("userId") Long userId,
                                                                 @Param("sessionId") String sessionId,
                                                                 @Param("startDate") LocalDateTime startDate,
                                                                 @Param("endDate") LocalDateTime endDate,
                                                                 Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.userId = :userId AND m.createdAt BETWEEN :startDate AND :endDate ORDER BY m.createdAt DESC")
    Page<ChatMessage> findByUserIdAndCreatedAtBetween(@Param("userId") Long userId,
                                                      @Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate,
                                                      Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.createdAt BETWEEN :startDate AND :endDate ORDER BY m.createdAt DESC")
    Page<ChatMessage> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate,
                                           Pageable pageable);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.createdAt BETWEEN :startDate AND :endDate")
    Long countByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(DISTINCT m.userId) FROM ChatMessage m WHERE m.createdAt BETWEEN :startDate AND :endDate")
    Long countDistinctUsersByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.content LIKE %:content% AND m.createdAt BETWEEN :startDate AND :endDate")
    Long countByContentContainingAndCreatedAtBetween(@Param("content") String content,
                                                    @Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate);

    @Query("SELECT m FROM ChatMessage m WHERE LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY m.createdAt DESC")
    Page<ChatMessage> findByContentContainingIgnoreCase(@Param("query") String query, Pageable pageable);

    /**
     * Find unread messages for user (for offline sync)
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.userId = :userId AND m.createdAt >= :since AND m.isDeleted = false")
    List<ChatMessage> findUserMessagesSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
