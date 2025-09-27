package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.ProjectTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectTimelineRepository extends JpaRepository<ProjectTimeline, Long> {

    @Query("SELECT pt FROM ProjectTimeline pt " +
           "WHERE pt.project.id = :projectId " +
           "ORDER BY pt.createdAt DESC")
    List<ProjectTimeline> findByProjectIdOrderByCreatedAtDesc(@Param("projectId") Long projectId);

    @Query("SELECT pt FROM ProjectTimeline pt " +
           "JOIN FETCH pt.changedBy " +
           "WHERE pt.project.id = :projectId " +
           "ORDER BY pt.createdAt DESC")
    List<ProjectTimeline> findByProjectIdWithUserOrderByCreatedAtDesc(@Param("projectId") Long projectId);
}
