package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.ProjectProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectProgressRepository extends JpaRepository<ProjectProgress, Long> {

    // Lấy progress của project
    Optional<ProjectProgress> findByProjectId(Long projectId);

    // Delete project progress by project ID
    void deleteByProjectId(Long projectId);

    // Query để tính toán số ProjectTask hoàn thành của project
    @Query("SELECT COALESCE(COUNT(pt), 0) FROM ProjectTask pt WHERE pt.project.id = :projectId AND (pt.status = 'DONE' OR pt.status = 'COMPLETED')")
    Long countCompletedTasksByProject(@Param("projectId") Long projectId);

    // Query để tính toán tổng số ProjectTask của project
    @Query("SELECT COALESCE(COUNT(pt), 0) FROM ProjectTask pt WHERE pt.project.id = :projectId")
    Long countTotalTasksByProject(@Param("projectId") Long projectId);

    // Query để đếm số teams trong project
    @Query("SELECT COALESCE(COUNT(p), 0) FROM Project p WHERE p.id = :projectId AND p.team IS NOT NULL")
    Long countTeamsByProject(@Param("projectId") Long projectId);
}