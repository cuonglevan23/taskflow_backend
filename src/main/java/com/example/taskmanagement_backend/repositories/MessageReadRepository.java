package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.MessageRead;
import com.example.taskmanagement_backend.enums.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReadRepository extends JpaRepository<MessageRead, Long> {

    @Query("SELECT mr FROM MessageRead mr " +
           "WHERE mr.message.id = :messageId")
    List<MessageRead> findByMessageId(@Param("messageId") Long messageId);

    @Query("SELECT mr FROM MessageRead mr " +
           "WHERE mr.message.id = :messageId AND mr.user.id = :userId")
    Optional<MessageRead> findByMessageIdAndUserId(@Param("messageId") Long messageId, @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE MessageRead mr SET mr.status = :status, mr.readAt = :readAt " +
           "WHERE mr.message.id = :messageId AND mr.user.id = :userId")
    void updateMessageStatus(@Param("messageId") Long messageId,
                           @Param("userId") Long userId,
                           @Param("status") MessageStatus status,
                           @Param("readAt") LocalDateTime readAt);

    @Query("SELECT COUNT(mr) FROM MessageRead mr " +
           "WHERE mr.message.id = :messageId AND mr.status = :status")
    Integer countByMessageIdAndStatus(@Param("messageId") Long messageId, @Param("status") MessageStatus status);

    @Query("SELECT mr FROM MessageRead mr " +
           "WHERE mr.user.id = :userId AND mr.message.conversation.id = :conversationId AND mr.status = :status")
    List<MessageRead> findUnreadMessagesByUserAndConversation(@Param("userId") Long userId,
                                                              @Param("conversationId") Long conversationId,
                                                              @Param("status") MessageStatus status);
}
