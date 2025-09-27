package com.example.taskmanagement_backend.repositories;


import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.TaskAssignee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface TasksAssigneeJpaRepository extends JpaRepository<TaskAssignee, Long> {

    // Find specific task assignee by task and user ID
    @Query("SELECT ta FROM TaskAssignee ta WHERE ta.task = :task AND ta.user.id = :userId")
    Optional<TaskAssignee> findByTaskAndUser_Id(@Param("task") Task task, @Param("userId") Long userId);

    // Delete all assignees for a specific task
    @Modifying
    @Transactional
    @Query("DELETE FROM TaskAssignee ta WHERE ta.task = :task")
    void deleteByTask(@Param("task") Task task);

    // Delete all assignees by task ID (bulk operation)
    @Modifying
    @Transactional
    @Query("DELETE FROM TaskAssignee ta WHERE ta.task.id = :taskId")
    void deleteByTaskId(@Param("taskId") Long taskId);

    // Check if user is assigned to task
    @Query("SELECT COUNT(ta) > 0 FROM TaskAssignee ta WHERE ta.task = :task AND ta.user.id = :userId")
    boolean existsByTaskAndUserId(@Param("task") Task task, @Param("userId") Long userId);

    // Find all assignees for a specific task
    @Query("SELECT ta FROM TaskAssignee ta WHERE ta.task = :task")
    List<TaskAssignee> findByTask(@Param("task") Task task);
}
