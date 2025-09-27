package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.TeamTask;
import com.example.taskmanagement_backend.entities.Team;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.enums.TaskPriority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TeamTaskJpaRepository extends JpaRepository<TeamTask, Long> {

    // Find by team
    List<TeamTask> findByTeam(Team team);
    List<TeamTask> findByTeamId(Long teamId);
    Page<TeamTask> findByTeamId(Long teamId, Pageable pageable);

    // Find by creator
    List<TeamTask> findByCreator(User creator);
    Page<TeamTask> findByCreator(User creator, Pageable pageable);

    // Find by assignee or assigned members
    List<TeamTask> findByAssignee(User assignee);

    @Query("SELECT tt FROM TeamTask tt WHERE tt.assignee = :user OR :user MEMBER OF tt.assignedMembers")
    List<TeamTask> findByAssigneeOrAssignedMembers(@Param("user") User user);

    @Query("SELECT tt FROM TeamTask tt WHERE tt.assignee = :user OR :user MEMBER OF tt.assignedMembers")
    Page<TeamTask> findByAssigneeOrAssignedMembers(@Param("user") User user, Pageable pageable);

    // Find by task category
    List<TeamTask> findByTaskCategory(String taskCategory);
    List<TeamTask> findByTeamIdAndTaskCategory(Long teamId, String taskCategory);

    // Find by status
    List<TeamTask> findByStatus(TaskStatus status);
    List<TeamTask> findByTeamIdAndStatus(Long teamId, TaskStatus status);

    // Find by priority
    List<TeamTask> findByPriority(TaskPriority priority);
    List<TeamTask> findByTeamIdAndPriority(Long teamId, TaskPriority priority);

    // Find by related project
    List<TeamTask> findByRelatedProject(Project relatedProject);
    List<TeamTask> findByRelatedProjectId(Long relatedProjectId);

    // Find recurring tasks
    List<TeamTask> findByIsRecurringTrue();
    List<TeamTask> findByTeamIdAndIsRecurringTrue(Long teamId);
    List<TeamTask> findByRecurrencePattern(String recurrencePattern);

    // Find by deadline
    List<TeamTask> findByDeadlineBefore(LocalDate date);
    List<TeamTask> findByDeadlineBetween(LocalDate startDate, LocalDate endDate);

    // Find overdue tasks
    @Query("SELECT tt FROM TeamTask tt WHERE tt.deadline < CURRENT_DATE AND tt.status != 'COMPLETED'")
    List<TeamTask> findOverdueTasks();

    @Query("SELECT tt FROM TeamTask tt WHERE tt.team.id = :teamId AND tt.deadline < CURRENT_DATE AND tt.status != 'COMPLETED'")
    List<TeamTask> findOverdueTasksByTeam(@Param("teamId") Long teamId);

    // Find user's team tasks (creator, assignee, or assigned member)
    @Query("SELECT DISTINCT tt FROM TeamTask tt WHERE " +
           "tt.creator = :user OR tt.assignee = :user OR :user MEMBER OF tt.assignedMembers")
    List<TeamTask> findUserTeamTasks(@Param("user") User user);

    @Query("SELECT DISTINCT tt FROM TeamTask tt WHERE " +
           "tt.creator = :user OR tt.assignee = :user OR :user MEMBER OF tt.assignedMembers")
    Page<TeamTask> findUserTeamTasks(@Param("user") User user, Pageable pageable);

    // Find subtasks
    List<TeamTask> findByParentTask(TeamTask parentTask);

    // Count methods
    long countByTeamId(Long teamId);
    long countByTeamIdAndStatus(Long teamId, TaskStatus status);
    long countByTeamIdAndTaskCategory(Long teamId, String taskCategory);
    long countByCreator(User creator);

    @Query("SELECT COUNT(tt) FROM TeamTask tt WHERE tt.creator = :user OR tt.assignee = :user OR :user MEMBER OF tt.assignedMembers")
    long countUserTeamTasks(@Param("user") User user);

    // Find tasks by team and user participation
    @Query("SELECT DISTINCT tt FROM TeamTask tt WHERE tt.team.id = :teamId AND " +
           "(tt.creator = :user OR tt.assignee = :user OR :user MEMBER OF tt.assignedMembers)")
    List<TeamTask> findTeamTasksByUserParticipation(@Param("teamId") Long teamId, @Param("user") User user);

    @Query("SELECT DISTINCT tt FROM TeamTask tt WHERE tt.team.id = :teamId AND " +
           "(tt.creator = :user OR tt.assignee = :user OR :user MEMBER OF tt.assignedMembers)")
    Page<TeamTask> findTeamTasksByUserParticipation(@Param("teamId") Long teamId, @Param("user") User user, Pageable pageable);

    // Find tasks for current week (useful for meetings, recurring tasks)
    @Query("SELECT tt FROM TeamTask tt WHERE tt.team.id = :teamId AND " +
           "tt.startDate BETWEEN :weekStart AND :weekEnd")
    List<TeamTask> findTeamTasksForWeek(@Param("teamId") Long teamId,
                                       @Param("weekStart") LocalDate weekStart,
                                       @Param("weekEnd") LocalDate weekEnd);

    // Complex queries for filtering
    @Query("SELECT tt FROM TeamTask tt WHERE " +
           "(:teamId IS NULL OR tt.team.id = :teamId) AND " +
           "(:status IS NULL OR tt.status = :status) AND " +
           "(:priority IS NULL OR tt.priority = :priority) AND " +
           "(:taskCategory IS NULL OR tt.taskCategory = :taskCategory) AND " +
           "(:assigneeId IS NULL OR tt.assignee.id = :assigneeId) AND " +
           "(:creatorId IS NULL OR tt.creator.id = :creatorId) AND " +
           "(:relatedProjectId IS NULL OR tt.relatedProject.id = :relatedProjectId)")
    Page<TeamTask> findTeamTasksWithFilters(
        @Param("teamId") Long teamId,
        @Param("status") TaskStatus status,
        @Param("priority") TaskPriority priority,
        @Param("taskCategory") String taskCategory,
        @Param("assigneeId") Long assigneeId,
        @Param("creatorId") Long creatorId,
        @Param("relatedProjectId") Long relatedProjectId,
        Pageable pageable
    );

    // Find team tasks by multiple categories
    @Query("SELECT tt FROM TeamTask tt WHERE tt.team.id = :teamId AND tt.taskCategory IN :categories")
    List<TeamTask> findTeamTasksByCategories(@Param("teamId") Long teamId, @Param("categories") List<String> categories);

    // Find upcoming recurring tasks
    @Query("SELECT tt FROM TeamTask tt WHERE tt.isRecurring = true AND " +
           "tt.startDate BETWEEN CURRENT_DATE AND :futureDate AND tt.team.id = :teamId")
    List<TeamTask> findUpcomingRecurringTasks(@Param("teamId") Long teamId, @Param("futureDate") LocalDate futureDate);
}