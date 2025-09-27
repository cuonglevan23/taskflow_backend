package com.example.taskmanagement_backend.agent.memory;

import com.example.taskmanagement_backend.agent.entity.Conversation;
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
 * Agent Conversation Repository - JDBC/MySQL/JPA repository for AI agent conversations
 * Persistent storage for audit logging (separate from main app conversations)
 */
@Repository("agentConversationRepository")
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByConversationIdAndIsDeletedFalse(String conversationId);

    List<Conversation> findByUserIdAndIsDeletedFalseOrderByUpdatedAtDesc(Long userId);

    @Query("SELECT c FROM AgentConversation c WHERE c.isDeleted = false AND c.status IN :statuses ORDER BY c.updatedAt DESC")
    Page<Conversation> findActiveConversations(@Param("statuses") List<Conversation.ConversationStatus> statuses, Pageable pageable);

    @Query("SELECT c FROM AgentConversation c WHERE c.agentActive = :agentActive AND c.isDeleted = false ORDER BY c.updatedAt DESC")
    List<Conversation> findByAgentActiveStatus(@Param("agentActive") Boolean agentActive);

    @Query("SELECT c FROM AgentConversation c WHERE c.supervisorId = :supervisorId AND c.isDeleted = false ORDER BY c.takenOverAt DESC")
    List<Conversation> findBySupervisorId(@Param("supervisorId") Long supervisorId);

    @Query("SELECT COUNT(c) FROM AgentConversation c WHERE c.userId = :userId AND c.status = :status AND c.isDeleted = false")
    Long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Conversation.ConversationStatus status);

    @Query("SELECT c FROM AgentConversation c WHERE c.lastActivityAt < :cutoffTime AND c.status = 'ACTIVE' AND c.isDeleted = false")
    List<Conversation> findInactiveConversations(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("SELECT c FROM AgentConversation c WHERE c.tags LIKE :tags")
    Page<Conversation> findByTagsContaining(@Param("tags") String tags, Pageable pageable);

    @Query("SELECT c FROM AgentConversation c WHERE c.userId = :userId AND c.tags LIKE :tags")
    Page<Conversation> findByUserIdAndTagsContaining(@Param("userId") Long userId, @Param("tags") String tags, Pageable pageable);

    @Query("SELECT COUNT(c) FROM AgentConversation c WHERE c.createdAt BETWEEN :startDate AND :endDate")
    Long countByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT c FROM AgentConversation c WHERE c.category = :category AND c.isDeleted = false ORDER BY c.createdAt DESC")
    Page<Conversation> findByCategory(@Param("category") String category, Pageable pageable);

    @Query("SELECT c FROM AgentConversation c WHERE c.userId = :userId AND c.category = :category AND c.isDeleted = false ORDER BY c.updatedAt DESC")
    List<Conversation> findByUserIdAndCategory(@Param("userId") Long userId, @Param("category") String category);
}
