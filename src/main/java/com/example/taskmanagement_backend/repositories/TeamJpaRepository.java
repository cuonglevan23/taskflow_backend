package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Team;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamJpaRepository extends JpaRepository<Team, Long> {
    List<Team> findByProjects_Id(Long projectId);
    
    /**
     * Find all teams where user is either creator or member
     * @param user The user to search for
     * @return List of teams user created or joined
     */
    @Query("SELECT DISTINCT t FROM Team t " +
           "LEFT JOIN TeamMember tm ON tm.team = t " +
           "WHERE t.createdBy = :user OR tm.user = :user")
    List<Team> findTeamsByUserCreatedOrJoined(@Param("user") User user);
    
    /**
     * Find teams created by specific user
     * @param createdBy The user who created the teams
     * @return List of teams created by the user
     */
    List<Team> findByCreatedBy(User createdBy);
}
