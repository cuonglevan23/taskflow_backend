package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.UserTaskPriority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTaskPriorityRepository extends JpaRepository<UserTaskPriority, Long> {

    /**
     * Find all task priorities for a specific user, ordered by sortOrder
     */
    List<UserTaskPriority> findByUserIdAndIsActiveTrueOrderBySortOrder(Long userId);

    /**
     * Find all task priorities for a specific user (including inactive)
     */
    List<UserTaskPriority> findByUserIdOrderBySortOrder(Long userId);

    /**
     * Find a specific priority by user and priority key
     */
    Optional<UserTaskPriority> findByUserIdAndPriorityKey(Long userId, String priorityKey);

    /**
     * Check if user has any custom priorities
     */
    boolean existsByUserId(Long userId);

    /**
     * Get default priorities (system-wide defaults)
     */
    @Query("SELECT utp FROM UserTaskPriority utp WHERE utp.isDefault = true ORDER BY utp.sortOrder")
    List<UserTaskPriority> findDefaultPriorities();

    /**
     * Delete all priorities for a user
     */
    void deleteByUserId(Long userId);
}