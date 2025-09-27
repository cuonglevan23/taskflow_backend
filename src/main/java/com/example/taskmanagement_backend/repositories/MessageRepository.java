package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m " +
           "WHERE m.conversation.id = :conversationId AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Page<Message> findByConversationIdOrderByCreatedAtDesc(@Param("conversationId") Long conversationId,
                                                           Pageable pageable);

    @Query("SELECT m FROM Message m " +
           "WHERE m.conversation.id = :conversationId AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC LIMIT 1")
    Optional<Message> findLastMessageByConversationId(@Param("conversationId") Long conversationId);

    @Query("SELECT COUNT(m) FROM Message m " +
           "JOIN MessageRead mr ON mr.message.id = m.id " +
           "WHERE m.conversation.id = :conversationId " +
           "AND mr.user.id = :userId " +
           "AND mr.status != 'READ' " +
           "AND m.sender.id != :userId " +
           "AND m.isDeleted = false")
    Integer countUnreadMessages(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Query("SELECT m FROM Message m " +
           "WHERE m.conversation.id IN :conversationIds " +
           "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<Message> searchMessages(@Param("conversationIds") List<Long> conversationIds,
                                @Param("keyword") String keyword);

    /**
     * Find unread messages for user (for offline sync)
     */
    @Query("SELECT m FROM Message m " +
           "JOIN MessageRead mr ON mr.message.id = m.id " +
           "WHERE mr.user.id = :userId " +
           "AND mr.status = 'SENT' " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt ASC")
    List<Message> findUnreadMessagesForUser(@Param("userId") Long userId);

    /**
     * Find messages after specific timestamp (for incremental sync)
     */
    @Query("SELECT m FROM Message m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.createdAt > :lastSyncTime " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt ASC")
    List<Message> findMessagesAfterTimestamp(@Param("conversationId") Long conversationId,
                                           @Param("lastSyncTime") java.time.LocalDateTime lastSyncTime);

    /**
     * Find full conversation history with pagination (for full chat history)
     */
    @Query("SELECT m FROM Message m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt ASC")
    Page<Message> findConversationHistoryPaginated(@Param("conversationId") Long conversationId,
                                                   Pageable pageable);

    /**
     * Count total messages in conversation
     */
    @Query("SELECT COUNT(m) FROM Message m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.isDeleted = false")
    Long countMessagesInConversation(@Param("conversationId") Long conversationId);

    /**
     * Find latest messages for user sync (after last seen message)
     */
    @Query("SELECT m FROM Message m " +
           "WHERE m.conversation.id IN :conversationIds " +
           "AND m.id > :lastSeenMessageId " +
           "AND m.isDeleted = false " +
           "ORDER BY m.createdAt ASC")
    List<Message> findMessagesAfterLastSeen(@Param("conversationIds") List<Long> conversationIds,
                                          @Param("lastSeenMessageId") Long lastSeenMessageId);

    // Thêm method để load tin nhắn từ cũ nhất (ASC order)
    @Query("SELECT m FROM Message m " +
           "WHERE m.conversation.id = :conversationId AND m.isDeleted = false " +
           "ORDER BY m.createdAt ASC")
    Page<Message> findByConversationIdOrderByCreatedAtAsc(@Param("conversationId") Long conversationId,
                                                          Pageable pageable);

    // Thêm method để load ALL tin nhắn không phân trang
    @Query("SELECT m FROM Message m " +
           "WHERE m.conversation.id = :conversationId AND m.isDeleted = false " +
           "ORDER BY m.createdAt ASC")
    List<Message> findAllByConversationIdOrderByCreatedAtAsc(@Param("conversationId") Long conversationId);
}
