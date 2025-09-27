package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    // ===== BASIC QUERIES =====

    /**
     * Find audit logs by user ID
     */
    List<AuditLog> findByUserId(Long userId);

    /**
     * Find audit logs by user ID (for entity relationship)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.user.id = :userId")
    List<AuditLog> findByUserIdEntity(@Param("userId") Long userId);

    // ===== ADMIN FILTERING QUERIES =====

    /**
     * Find audit logs created after a specific date
     */
    List<AuditLog> findByCreatedAtAfter(LocalDateTime date);

    /**
     * Find audit logs created before a specific date
     */
    List<AuditLog> findByCreatedAtBefore(LocalDateTime date);

    /**
     * Find audit logs created between dates
     */
    List<AuditLog> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find recent audit logs with limit (ordered by creation date descending)
     */
    @Query("SELECT a FROM AuditLog a ORDER BY a.createdAt DESC")
    List<AuditLog> findTopByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Default method for getting recent activities with limit
     */
    default List<AuditLog> findTopByOrderByCreatedAtDesc(int limit) {
        return findTopByOrderByCreatedAtDesc(Pageable.ofSize(limit));
    }

    /**
     * Find audit logs by action containing text (case insensitive)
     */
    @Query("SELECT a FROM AuditLog a WHERE LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%'))")
    List<AuditLog> findByActionContainingIgnoreCase(@Param("action") String action);

    /**
     * Find audit logs by entity type
     */
    List<AuditLog> findByEntityType(String entityType);

    /**
     * Find audit logs by entity ID
     */
    List<AuditLog> findByEntityId(String entityId);

    /**
     * Find audit logs by IP address
     */
    List<AuditLog> findByIpAddress(String ipAddress);

    // ===== ADMIN STATISTICS QUERIES =====

    /**
     * Count audit logs by user in date range
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.user.id = :userId AND a.createdAt >= :startDate")
    Long countByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);

    /**
     * Count audit logs by action type in date range
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%')) AND a.createdAt >= :startDate")
    Long countByActionContainingAndCreatedAtAfter(@Param("action") String action, @Param("startDate") LocalDateTime startDate);

    /**
     * Find failed login attempts
     */
    @Query("SELECT a FROM AuditLog a WHERE (LOWER(a.action) LIKE '%failed%' OR LOWER(a.action) LIKE '%login%') AND a.createdAt >= :startDate")
    List<AuditLog> findFailedLoginAttempts(@Param("startDate") LocalDateTime startDate);

    /**
     * Get audit logs grouped by user for suspicious activity detection
     */
    @Query("SELECT a.user.id, COUNT(a) FROM AuditLog a WHERE a.createdAt >= :startDate GROUP BY a.user.id HAVING COUNT(a) > :threshold")
    List<Object[]> findSuspiciousUserActivities(@Param("startDate") LocalDateTime startDate, @Param("threshold") Long threshold);

    /**
     * Get most active users in time range
     */
    @Query("SELECT a.user.id, COUNT(a) as activityCount FROM AuditLog a WHERE a.createdAt >= :startDate GROUP BY a.user.id ORDER BY activityCount DESC")
    List<Object[]> findMostActiveUsers(@Param("startDate") LocalDateTime startDate, Pageable pageable);

    /**
     * Get activity breakdown by action type
     */
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.createdAt >= :startDate GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> getActivityBreakdownByAction(@Param("startDate") LocalDateTime startDate);

    /**
     * Get daily activity counts
     */
    @Query("SELECT DATE(a.createdAt), COUNT(a) FROM AuditLog a WHERE a.createdAt >= :startDate GROUP BY DATE(a.createdAt) ORDER BY DATE(a.createdAt)")
    List<Object[]> getDailyActivityCounts(@Param("startDate") LocalDateTime startDate);

    // ===== ADMIN MAINTENANCE QUERIES =====

    /**
     * Delete audit logs older than specified date
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    void deleteByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count audit logs older than specified date (for cleanup preview)
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    Long countByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ===== ADVANCED SEARCH QUERIES =====

    /**
     * Search audit logs with multiple criteria
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.user.id = :userId) AND " +
           "(:action IS NULL OR LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%'))) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate) " +
           "ORDER BY a.createdAt DESC")
    List<AuditLog> searchAuditLogs(
        @Param("userId") Long userId,
        @Param("action") String action,
        @Param("entityType") String entityType,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find audit logs by user and time range
     */
    @Query("SELECT a FROM AuditLog a WHERE a.user.id = :userId AND a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    List<AuditLog> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get unique action types for filtering dropdown
     */
    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> findDistinctActions();

    /**
     * Get unique entity types for filtering dropdown
     */
    @Query("SELECT DISTINCT a.entityType FROM AuditLog a WHERE a.entityType IS NOT NULL ORDER BY a.entityType")
    List<String> findDistinctEntityTypes();

    /**
     * Get unique IP addresses for security analysis
     */
    @Query("SELECT DISTINCT a.ipAddress FROM AuditLog a WHERE a.ipAddress IS NOT NULL ORDER BY a.ipAddress")
    List<String> findDistinctIpAddresses();
}
