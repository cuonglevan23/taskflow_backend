package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.EmailLog;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.EmailDirection;
import com.example.taskmanagement_backend.enums.EmailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    // Find emails by user and direction
    Page<EmailLog> findByUserAndDirectionOrderByCreatedAtDesc(User user, EmailDirection direction, Pageable pageable);

    // Find emails by user
    Page<EmailLog> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // Find by Gmail message ID
    Optional<EmailLog> findByGmailMessageId(String gmailMessageId);

    // Find by thread ID
    List<EmailLog> findByThreadIdOrderByCreatedAtAsc(String threadId);

    // Search emails by subject or body
    @Query("SELECT e FROM EmailLog e WHERE e.user = :user AND " +
           "(LOWER(e.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(e.body) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY e.createdAt DESC")
    Page<EmailLog> searchEmailsByContent(@Param("user") User user,
                                        @Param("searchTerm") String searchTerm,
                                        Pageable pageable);

    // Find emails by status
    Page<EmailLog> findByUserAndStatusOrderByCreatedAtDesc(User user, EmailStatus status, Pageable pageable);

    // Find emails sent between dates
    @Query("SELECT e FROM EmailLog e WHERE e.user = :user AND " +
           "e.sentAt BETWEEN :startDate AND :endDate " +
           "ORDER BY e.sentAt DESC")
    List<EmailLog> findEmailsBetweenDates(@Param("user") User user,
                                         @Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);

    // Find sent emails by user (for SENT emails functionality)
    @Query("SELECT e FROM EmailLog e WHERE e.user = :user AND " +
           "e.direction = 'OUT' AND e.status = 'SENT' " +
           "ORDER BY e.sentAt DESC")
    Page<EmailLog> findSentEmailsByUser(@Param("user") User user, Pageable pageable);

    // Count unread emails (assuming RECEIVED status means unread)
    @Query("SELECT COUNT(e) FROM EmailLog e WHERE e.user = :user AND " +
           "e.direction = 'IN' AND e.status = 'RECEIVED'")
    Long countUnreadEmails(@Param("user") User user);
}
