package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Organization;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskJpaRepository extends JpaRepository<Task, Long> {
    
    // Find tasks by creator or where user is assigned
    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN t.assignees ta " +
           "WHERE t.creator = :user OR ta.user = :assignedUser")
    List<Task> findByCreatorOrAssignees(@Param("user") User user, @Param("assignedUser") User assignedUser);

    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN FETCH t.creator " +
           "LEFT JOIN FETCH t.project " +
           "LEFT JOIN FETCH t.team " +
           "LEFT JOIN FETCH t.checklists " +
           "LEFT JOIN t.assignees ta " +
           "WHERE t.creator = :user OR ta.user = :assignedUser")
    List<Task> findMyTasksOptimized(@Param("user") User user);

    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN t.assignees ta " +
           "WHERE t.creator = :user OR ta.user = :user")
    List<Task> findMyTasksSummary(@Param("user") User user);
    
    // Find tasks by organization (for admin/owner access)
    @Query("SELECT DISTINCT t FROM Task t " +
           "WHERE t.creator.organization = :creatorOrg OR t.project.organization = :projectOrg")
    List<Task> findByCreator_OrganizationOrProject_Organization(
            @Param("creatorOrg") Organization creatorOrg, 
            @Param("projectOrg") Organization projectOrg);
    
    // Find tasks by creator only
    List<Task> findByCreator(User creator);
    
    // Find tasks by project
    @Query("SELECT t FROM Task t WHERE t.project.id = :projectId")
    List<Task> findByProjectId(@Param("projectId") Long projectId);
    
    // Find tasks by team
    @Query("SELECT t FROM Task t WHERE t.team.id = :teamId")
    List<Task> findByTeamId(@Param("teamId") Long teamId);

    // Methods needed for DashboardService
    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN t.assignees ta " +
           "WHERE t.creator.id = :creatorId OR ta.user.id = :assigneeId")
    List<Task> findTasksByCreatorIdOrAssigneeId(@Param("creatorId") Long creatorId, @Param("assigneeId") Long assigneeId);

    // Methods needed for TaskService
    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN t.assignees ta " +
           "WHERE t.creator = :user OR ta.user = :user")
    Page<Task> findMyParticipatingTasks(@Param("user") User user, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT t) FROM Task t " +
           "LEFT JOIN t.assignees ta " +
           "WHERE t.creator = :user OR ta.user = :user")
    long countMyParticipatingTasks(@Param("user") User user);

    // Methods needed for profile page functionality
    @Query("SELECT t FROM Task t JOIN t.assignees ta WHERE ta.user = :assignee ORDER BY t.createdAt DESC")
    Page<Task> findByAssigneeOrderByCreatedAtDesc(@Param("assignee") User assignee, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Task t JOIN t.assignees ta WHERE ta.user = :assignee")
    long countByAssignee(@Param("assignee") User assignee);

    @Query("SELECT COUNT(t) FROM Task t JOIN t.assignees ta WHERE ta.user = :assignee AND t.isPublic = :isPublic")
    long countByAssigneeAndIsPublic(@Param("assignee") User assignee, @Param("isPublic") Boolean isPublic);

    @Query("SELECT COUNT(t) FROM Task t JOIN t.assignees ta WHERE ta.user = :assignee AND t.status = :status")
    long countByAssigneeAndStatus(@Param("assignee") User assignee, @Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t JOIN t.assignees ta WHERE ta.user = :assignee AND t.status = :status AND t.isPublic = :isPublic")
    long countByAssigneeAndStatusAndIsPublic(@Param("assignee") User assignee, @Param("status") TaskStatus status, @Param("isPublic") Boolean isPublic);

    @Query("SELECT t FROM Task t JOIN t.assignees ta WHERE ta.user = :assignee AND t.isPublic = :isPublic ORDER BY t.createdAt DESC")
    Page<Task> findByAssigneeAndIsPublicOrderByCreatedAtDesc(@Param("assignee") User assignee, @Param("isPublic") Boolean isPublic, Pageable pageable);

    // ✅ NEW: Methods for overdue task notifications
    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN FETCH t.assignees ta " +
           "LEFT JOIN FETCH ta.user " +
           "LEFT JOIN FETCH t.creator " +
           "WHERE t.deadline < :currentDate " +
           "AND (t.statusKey IS NULL OR (t.statusKey NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))) " +
           "AND (t.status IS NULL OR t.status NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))")
    List<Task> findOverdueTasks(@Param("currentDate") java.time.LocalDate currentDate);

    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN FETCH t.assignees ta " +
           "LEFT JOIN FETCH ta.user " +
           "LEFT JOIN FETCH t.creator " +
           "WHERE t.deadline BETWEEN :startDate AND :endDate " +
           "AND (t.statusKey IS NULL OR (t.statusKey NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))) " +
           "AND (t.status IS NULL OR t.status NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))")
    List<Task> findTasksDueBetween(@Param("startDate") java.time.LocalDate startDate,
                                   @Param("endDate") java.time.LocalDate endDate);

    @Query("SELECT COUNT(DISTINCT t) FROM Task t " +
           "LEFT JOIN t.assignees ta " +
           "WHERE (t.creator = :user OR ta.user = :user) " +
           "AND t.deadline < :currentDate " +
           "AND (t.statusKey IS NULL OR (t.statusKey NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))) " +
           "AND (t.status IS NULL OR t.status NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))")
    long countOverdueTasksForUser(@Param("user") User user, @Param("currentDate") java.time.LocalDate currentDate);

    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN t.assignees ta " +
           "WHERE (t.creator = :user OR ta.user = :user) " +
           "AND t.deadline < :currentDate " +
           "AND (t.statusKey IS NULL OR (t.statusKey NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))) " +
           "AND (t.status IS NULL OR t.status NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))")
    List<Task> findOverdueTasksForUser(@Param("user") User user, @Param("currentDate") java.time.LocalDate currentDate);

    // ✅ NEW: Methods for TaskReminderService - deadline reminders (Fixed for LocalDate)
    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN FETCH t.assignees ta " +
           "LEFT JOIN FETCH ta.user " +
           "LEFT JOIN FETCH t.creator " +
           "WHERE t.deadline BETWEEN :startDate AND :endDate " +
           "AND (t.statusKey IS NULL OR (t.statusKey NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))) " +
           "AND (t.status IS NULL OR t.status NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))")
    List<Task> findTasksWithDeadlineBetween(@Param("startDate") java.time.LocalDate startDate,
                                            @Param("endDate") java.time.LocalDate endDate);

    @Query("SELECT DISTINCT t FROM Task t " +
           "LEFT JOIN FETCH t.assignees ta " +
           "LEFT JOIN FETCH ta.user " +
           "LEFT JOIN FETCH t.creator " +
           "WHERE t.deadline < :currentDate " +
           "AND (t.statusKey IS NULL OR (t.statusKey NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))) " +
           "AND (t.status IS NULL OR t.status NOT IN ('COMPLETED', 'DONE', 'CANCELLED'))")
    List<Task> findOverdueTasksForReminder(@Param("currentDate") java.time.LocalDate currentDate);
}
