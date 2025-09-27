package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    @Query("SELECT cm FROM ConversationMember cm " +
           "WHERE cm.conversation.id = :conversationId AND cm.isActive = true")
    List<ConversationMember> findActiveByConversationId(@Param("conversationId") Long conversationId);

    @Query("SELECT cm FROM ConversationMember cm " +
           "WHERE cm.conversation.id = :conversationId AND cm.user.id = :userId AND cm.isActive = true")
    Optional<ConversationMember> findByConversationIdAndUserId(@Param("conversationId") Long conversationId,
                                                               @Param("userId") Long userId);

    @Query("SELECT COUNT(cm) FROM ConversationMember cm " +
           "WHERE cm.conversation.id = :conversationId AND cm.isActive = true")
    Integer countActiveByConversationId(@Param("conversationId") Long conversationId);

    @Query("SELECT cm.user.id FROM ConversationMember cm " +
           "WHERE cm.conversation.id = :conversationId AND cm.isActive = true")
    List<Long> findUserIdsByConversationId(@Param("conversationId") Long conversationId);

    @Query("SELECT cm.conversation.id FROM ConversationMember cm " +
           "WHERE cm.user.id = :userId AND cm.isActive = true")
    List<Long> findConversationIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT cm FROM ConversationMember cm " +
           "WHERE cm.conversation.id = :conversationId AND cm.isActive = true")
    List<ConversationMember> findByConversationId(@Param("conversationId") Long conversationId);
}
