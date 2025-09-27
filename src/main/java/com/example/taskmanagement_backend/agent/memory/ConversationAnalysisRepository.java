package com.example.taskmanagement_backend.agent.memory;

import com.example.taskmanagement_backend.agent.entity.ConversationAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationAnalysisRepository extends JpaRepository<ConversationAnalysis, Long> {

    /**
     * Find analysis by conversation ID where not deleted
     */
    Optional<ConversationAnalysis> findByConversationIdAndIsDeletedFalse(String conversationId);

    /**
     * Find analysis by user ID where not deleted
     */
    java.util.List<ConversationAnalysis> findByUserIdAndIsDeletedFalse(Long userId);

    /**
     * Check if analysis exists for a conversation
     */
    boolean existsByConversationIdAndIsDeletedFalse(String conversationId);

    /**
     * Mark analysis as deleted
     */
    void deleteByConversationId(String conversationId);
}
