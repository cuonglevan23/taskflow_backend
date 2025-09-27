package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.ProjectTask;
import com.example.taskmanagement_backend.entities.ProjectTaskActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectTaskActivityRepository extends JpaRepository<ProjectTaskActivity, Long> {

    /**
     * Find all activities for a project task ordered by creation date descending
     */
    List<ProjectTaskActivity> findByProjectTaskIdOrderByCreatedAtDesc(Long projectTaskId);

    /**
     * Find activities for a project task with pagination ordered by creation date descending
     */
    Page<ProjectTaskActivity> findByProjectTaskIdOrderByCreatedAtDesc(Long projectTaskId, Pageable pageable);

    /**
     * Find recent activities for a project task (limited)
     */
    @Query("SELECT pta FROM ProjectTaskActivity pta WHERE pta.projectTask.id = :projectTaskId ORDER BY pta.createdAt DESC")
    List<ProjectTaskActivity> findRecentActivityByProjectTaskId(@Param("projectTaskId") Long projectTaskId, Pageable pageable);

    /**
     * Count activities for a project task
     */
    Long countByProjectTaskId(Long projectTaskId);

    /**
     * Delete all activities for a project task
     */
    void deleteByProjectTaskId(Long projectTaskId);

    /**
     * Find activities by project task entity
     */
    List<ProjectTaskActivity> findByProjectTaskOrderByCreatedAtDesc(ProjectTask projectTask);

    /**
     * Find activities by project task entity with pagination
     */
    Page<ProjectTaskActivity> findByProjectTaskOrderByCreatedAtDesc(ProjectTask projectTask, Pageable pageable);
}
