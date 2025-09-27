package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Conversation;
import com.example.taskmanagement_backend.enums.ConversationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c " +
           "JOIN c.members cm " +
           "WHERE cm.user.id = :userId AND cm.isActive = true AND c.isActive = true " +
           "ORDER BY c.updatedAt DESC")
    Page<Conversation> findByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT c FROM Conversation c " +
           "JOIN c.members cm1 " +
           "JOIN c.members cm2 " +
           "WHERE c.type = :type " +
           "AND cm1.user.id = :userId1 AND cm1.isActive = true " +
           "AND cm2.user.id = :userId2 AND cm2.isActive = true " +
           "AND c.isActive = true")
    Optional<Conversation> findDirectConversation(@Param("type") ConversationType type,
                                                  @Param("userId1") Long userId1,
                                                  @Param("userId2") Long userId2);

    @Query("SELECT c FROM Conversation c " +
           "JOIN c.members cm " +
           "WHERE cm.user.id = :userId AND cm.isActive = true " +
           "AND c.isActive = true " +
           "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR EXISTS (SELECT m FROM c.messages m WHERE LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%'))))")
    List<Conversation> searchConversations(@Param("userId") Long userId, @Param("keyword") String keyword);
}
