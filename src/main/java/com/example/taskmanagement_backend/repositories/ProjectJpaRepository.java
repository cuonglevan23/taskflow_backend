package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.Team;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectJpaRepository extends JpaRepository<Project,Long> {
    
    /**
     * Find all projects where user is either creator, owner or member
     * @param user The user to search for
     * @return List of projects user created, owns or joined
     */
    @Query("SELECT DISTINCT p FROM Project p " +
           "LEFT JOIN ProjectMember pm ON pm.project = p " +
           "WHERE p.createdBy = :user OR p.owner = :user OR pm.user = :user")
    List<Project> findProjectsByUserCreatedOwnedOrJoined(@Param("user") User user);
    
    /**
     * Find projects created by specific user
     * @param createdBy The user who created the projects
     * @return List of projects created by the user
     */
    List<Project> findByCreatedBy(User createdBy);
    
    /**
     * Find projects owned by specific user
     * @param owner The user who owns the projects
     * @return List of projects owned by the user
     */
    List<Project> findByOwner(User owner);

    /**
     * Find projects by owner ID
     * @param ownerId The ID of the user who owns the projects
     * @return List of projects owned by the user
     */
    @Query("SELECT p FROM Project p WHERE p.owner.id = :ownerId")
    List<Project> findByOwner_Id(@Param("ownerId") Long ownerId);

    /**
     * Find projects assigned to specific team
     * @param team The team the projects are assigned to
     * @return List of projects assigned to the team
     */
    List<Project> findByTeam(Team team);

    /**
     * Find projects by list of IDs
     * @param ids List of project IDs to find
     * @return List of projects matching the IDs
     */
    List<Project> findByIdIn(List<Long> ids);

    /**
     * Find projects assigned to any team in a list
     * @param teamIds List of team IDs to find projects for
     * @return List of projects assigned to any of the teams
     */
    List<Project> findByTeamIdIn(List<Long> teamIds);

    /**
     * 🔒 AUTHORIZATION: Find projects by creator ID
     * Used for search authorization at database layer
     * @param creatorId The ID of the creator
     * @return List of projects created by the user
     */
    @Query("SELECT p FROM Project p WHERE p.createdBy.id = :creatorId")
    List<Project> findByCreatedById(@Param("creatorId") Long creatorId);
}
