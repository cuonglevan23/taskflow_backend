package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.ProjectTask;
import com.example.taskmanagement_backend.entities.ProjectTaskComment;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectTaskCommentRepository extends JpaRepository<ProjectTaskComment, Long> {

    /**
     * Find comments by project task ordered by creation date ascending
     */
    List<ProjectTaskComment> findByProjectTaskOrderByCreatedAtAsc(ProjectTask projectTask);

    /**
     * Find comments by project task ordered by creation date descending with pagination
     */
    Page<ProjectTaskComment> findByProjectTaskOrderByCreatedAtDesc(ProjectTask projectTask, Pageable pageable);

    /**
     * Count comments by project task
     */
    long countByProjectTask(ProjectTask projectTask);

    /**
     * Delete all comments by project task ID
     */
    void deleteByProjectTaskId(Long projectTaskId);

    /**
     * Find comments by project task and user ordered by creation date descending
     */
    List<ProjectTaskComment> findByProjectTaskAndUserOrderByCreatedAtDesc(ProjectTask projectTask, User user);

    /**
     * Search comments by content containing keyword (case insensitive)
     */
    List<ProjectTaskComment> findByProjectTaskAndContentContainingIgnoreCaseOrderByCreatedAtDesc(ProjectTask projectTask, String keyword);
}
