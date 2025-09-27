package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.TeamProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamProgressRepository extends JpaRepository<TeamProgress, Long> {

    // Lấy progress của team (across all projects)
    Optional<TeamProgress> findByTeamId(Long teamId);

    // Delete team progress by team ID
    void deleteByTeamId(Long teamId);

    // ✅ FIX: Query để tính toán số task hoàn thành của team thông qua projects
    // Handle case where team has no projects yet
    @Query("SELECT COALESCE(COUNT(pt), 0) FROM ProjectTask pt " +
           "JOIN pt.project p " +
           "WHERE p.team.id = :teamId AND pt.status = 'DONE'")
    Long countCompletedTasksByTeam(@Param("teamId") Long teamId);

    // ✅ FIX: Query để tính toán tổng số task của team thông qua projects
    // Handle case where team has no projects yet
    @Query("SELECT COALESCE(COUNT(pt), 0) FROM ProjectTask pt " +
           "JOIN pt.project p " +
           "WHERE p.team.id = :teamId")
    Long countTotalTasksByTeam(@Param("teamId") Long teamId);
}