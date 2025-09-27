package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.ProjectMember;
import com.example.taskmanagement_backend.entities.Team;
import com.example.taskmanagement_backend.entities.TeamMember;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberJpaRepository extends JpaRepository<TeamMember, Long> {
    List<TeamMember> findByTeamId(Long teamId);

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);
    
    Optional<TeamMember> findByTeamAndUser(Team team, User user);

    /**
     * 🔒 AUTHORIZATION: Find team memberships by user ID
     * Used for search authorization at database layer
     * @param userId The ID of the user
     * @return List of team memberships for the user
     */
    List<TeamMember> findByUser_Id(Long userId);
}
