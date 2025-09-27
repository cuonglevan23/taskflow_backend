package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.UserTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTaskStatusRepository extends JpaRepository<UserTaskStatus, Long> {

    /**
     * Find all task statuses for a specific user, ordered by sortOrder
     */
    List<UserTaskStatus> findByUserIdAndIsActiveTrueOrderBySortOrder(Long userId);

    /**
     * Find all task statuses for a specific user (including inactive)
     */
    List<UserTaskStatus> findByUserIdOrderBySortOrder(Long userId);

    /**
     * Find a specific status by user and status key
     */
    Optional<UserTaskStatus> findByUserIdAndStatusKey(Long userId, String statusKey);

    /**
     * Check if user has any custom statuses
     */
    boolean existsByUserId(Long userId);

    /**
     * Get default statuses (system-wide defaults)
     */
    @Query("SELECT uts FROM UserTaskStatus uts WHERE uts.isDefault = true ORDER BY uts.sortOrder")
    List<UserTaskStatus> findDefaultStatuses();

    /**
     * Delete all statuses for a user
     */
    void deleteByUserId(Long userId);
}