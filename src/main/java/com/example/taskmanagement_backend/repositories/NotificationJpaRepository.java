package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Notification;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {

    // Find all notifications for a user, ordered by creation time (newest first)
    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // Find unread notifications for a user
    Page<Notification> findByUserAndIsReadFalseOrderByCreatedAtDesc(User user, Pageable pageable);

    // Count unread notifications for a user
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user = :user AND n.isRead = false")
    Long countUnreadByUser(@Param("user") User user);

    // Count unread notifications by type for a user
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user = :user AND n.isRead = false AND n.type = :type")
    Long countUnreadByUserAndType(@Param("user") User user, @Param("type") NotificationType type);

    // Find notifications by type for a user
    Page<Notification> findByUserAndTypeOrderByCreatedAtDesc(User user, NotificationType type, Pageable pageable);

    // Mark all notifications as read for a user
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.user = :user AND n.isRead = false")
    int markAllAsReadByUser(@Param("user") User user, @Param("readAt") LocalDateTime readAt);

    // Mark notifications as read by IDs
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.id IN :ids AND n.user = :user")
    int markAsReadByIds(@Param("ids") List<Long> ids, @Param("user") User user, @Param("readAt") LocalDateTime readAt);

    // Delete expired notifications
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.expiresAt IS NOT NULL AND n.expiresAt < :now")
    int deleteExpiredNotifications(@Param("now") LocalDateTime now);

    // ✅ NEW: Find bookmarked notifications for a user
    Page<Notification> findByUserAndIsBookmarkedTrueOrderByBookmarkedAtDesc(User user, Pageable pageable);

    // ✅ NEW: Find archived notifications for a user
    Page<Notification> findByUserAndIsArchivedTrueOrderByArchivedAtDesc(User user, Pageable pageable);

    // ✅ NEW: Find active (non-archived) notifications for a user
    Page<Notification> findByUserAndIsArchivedFalseOrderByCreatedAtDesc(User user, Pageable pageable);

    // ✅ NEW: Find active unread notifications (main inbox view)
    Page<Notification> findByUserAndIsReadFalseAndIsArchivedFalseOrderByCreatedAtDesc(User user, Pageable pageable);

    // ✅ NEW: Count bookmarked notifications for a user
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user = :user AND n.isBookmarked = true")
    Long countBookmarkedByUser(@Param("user") User user);

    // ✅ NEW: Count archived notifications for a user
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user = :user AND n.isArchived = true")
    Long countArchivedByUser(@Param("user") User user);

    // ✅ NEW: Toggle bookmark status
    @Modifying
    @Query("UPDATE Notification n SET n.isBookmarked = :bookmarked, n.bookmarkedAt = :bookmarkedAt WHERE n.id = :id AND n.user = :user")
    int updateBookmarkStatus(@Param("id") Long id, @Param("user") User user, @Param("bookmarked") Boolean bookmarked, @Param("bookmarkedAt") LocalDateTime bookmarkedAt);

    // ✅ NEW: Archive notifications by IDs
    @Modifying
    @Query("UPDATE Notification n SET n.isArchived = true, n.archivedAt = :archivedAt WHERE n.id IN :ids AND n.user = :user")
    int archiveByIds(@Param("ids") List<Long> ids, @Param("user") User user, @Param("archivedAt") LocalDateTime archivedAt);

    // ✅ NEW: Unarchive notifications by IDs
    @Modifying
    @Query("UPDATE Notification n SET n.isArchived = false, n.archivedAt = null WHERE n.id IN :ids AND n.user = :user")
    int unarchiveByIds(@Param("ids") List<Long> ids, @Param("user") User user);

    // Find notifications by reference (for updating related notifications)
    List<Notification> findByReferenceIdAndReferenceType(Long referenceId, String referenceType);

    // Find recent notifications for real-time sync
    @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.createdAt > :since ORDER BY n.createdAt DESC")
    List<Notification> findRecentNotifications(@Param("user") User user, @Param("since") LocalDateTime since);

    // Get unread count breakdown by type
    @Query("""
        SELECT n.type, COUNT(n) 
        FROM Notification n 
        WHERE n.user = :user AND n.isRead = false 
        GROUP BY n.type
    """)
    List<Object[]> getUnreadCountByType(@Param("user") User user);

    // Find high priority unread notifications
    @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.isRead = false AND n.priority > 0 ORDER BY n.priority DESC, n.createdAt DESC")
    List<Notification> findHighPriorityUnreadNotifications(@Param("user") User user);

    // 🚨 NEW: Count notifications by user, type and date range (for duplicate checking)
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.type = :type AND n.createdAt BETWEEN :startDate AND :endDate")
    long countByUserIdAndTypeAndCreatedAtBetween(@Param("userId") Long userId,
                                                @Param("type") NotificationType type,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);
}
