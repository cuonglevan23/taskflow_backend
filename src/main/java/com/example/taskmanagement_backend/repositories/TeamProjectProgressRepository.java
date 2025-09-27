package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.TeamProjectProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamProjectProgressRepository extends JpaRepository<TeamProjectProgress, Long> {

    // Lấy progress của team trong một project cụ thể
    Optional<TeamProjectProgress> findByTeamIdAndProjectId(Long teamId, Long projectId);

    // Lấy tất cả team progress trong một project
    List<TeamProjectProgress> findByProjectId(Long projectId);

    // Lấy tất cả project progress của một team
    List<TeamProjectProgress> findByTeamId(Long teamId);

    // Delete team project progress by project ID
    void deleteByProjectId(Long projectId);

    // Query để tính toán số TeamTask hoàn thành có liên quan đến project
    @Query("SELECT COUNT(tt) FROM TeamTask tt WHERE tt.team.id = :teamId AND tt.relatedProject.id = :projectId AND tt.status = 'DONE'")
    Long countCompletedTasksByTeamAndProject(@Param("teamId") Long teamId, @Param("projectId") Long projectId);

    // Query để tính toán tổng số TeamTask có liên quan đến project
    @Query("SELECT COUNT(tt) FROM TeamTask tt WHERE tt.team.id = :teamId AND tt.relatedProject.id = :projectId")
    Long countTotalTasksByTeamAndProject(@Param("teamId") Long teamId, @Param("projectId") Long projectId);

    // Alternative: Query để tính ProjectTask được assign cho team members
    @Query("SELECT COUNT(pt) FROM ProjectTask pt WHERE pt.project.id = :projectId AND " +
           "(pt.assignee.id IN (SELECT tm.id FROM TeamMember tm WHERE tm.team.id = :teamId) OR " +
           "EXISTS (SELECT 1 FROM pt.additionalAssignees aa WHERE aa.id IN (SELECT tm2.id FROM TeamMember tm2 WHERE tm2.team.id = :teamId)))")
    Long countProjectTasksAssignedToTeamMembers(@Param("teamId") Long teamId, @Param("projectId") Long projectId);

    @Query("SELECT COUNT(pt) FROM ProjectTask pt WHERE pt.project.id = :projectId AND pt.status = 'DONE' AND " +
           "(pt.assignee.id IN (SELECT tm.id FROM TeamMember tm WHERE tm.team.id = :teamId) OR " +
           "EXISTS (SELECT 1 FROM pt.additionalAssignees aa WHERE aa.id IN (SELECT tm2.id FROM TeamMember tm2 WHERE tm2.team.id = :teamId)))")
    Long countCompletedProjectTasksAssignedToTeamMembers(@Param("teamId") Long teamId, @Param("projectId") Long projectId);
}