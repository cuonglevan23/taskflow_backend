package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.ProjectTask;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.User;
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
public interface ProjectTaskJpaRepository extends JpaRepository<ProjectTask, Long> {

    // Find by project
    List<ProjectTask> findByProject(Project project);
    List<ProjectTask> findByProjectId(Long projectId);
    Page<ProjectTask> findByProjectId(Long projectId, Pageable pageable);

    // Find by creator
    List<ProjectTask> findByCreator(User creator);
    Page<ProjectTask> findByCreator(User creator, Pageable pageable);

    // Find by assignee (primary or additional)
    List<ProjectTask> findByAssignee(User assignee);

    @Query("SELECT pt FROM ProjectTask pt WHERE pt.assignee = :user OR :user MEMBER OF pt.additionalAssignees")
    List<ProjectTask> findByAssigneeOrAdditionalAssignees(@Param("user") User user);

    @Query("SELECT pt FROM ProjectTask pt WHERE pt.assignee = :user OR :user MEMBER OF pt.additionalAssignees")
    Page<ProjectTask> findByAssigneeOrAdditionalAssignees(@Param("user") User user, Pageable pageable);

    // Find by status
    List<ProjectTask> findByStatus(TaskStatus status);
    List<ProjectTask> findByProjectIdAndStatus(Long projectId, TaskStatus status);

    // Find by priority
    List<ProjectTask> findByPriority(TaskPriority priority);
    List<ProjectTask> findByProjectIdAndPriority(Long projectId, TaskPriority priority);

    // Find by deadline
    List<ProjectTask> findByDeadlineBefore(LocalDate date);
    List<ProjectTask> findByDeadlineBetween(LocalDate startDate, LocalDate endDate);

    // Find overdue tasks
    @Query("SELECT pt FROM ProjectTask pt WHERE pt.deadline < CURRENT_DATE AND pt.status != 'COMPLETED'")
    List<ProjectTask> findOverdueTasks();

    @Query("SELECT pt FROM ProjectTask pt WHERE pt.project.id = :projectId AND pt.deadline < CURRENT_DATE AND pt.status != 'COMPLETED'")
    List<ProjectTask> findOverdueTasksByProject(@Param("projectId") Long projectId);

    // Find user's project tasks (creator, assignee, or additional assignee)
    @Query("SELECT DISTINCT pt FROM ProjectTask pt WHERE " +
           "pt.creator = :user OR pt.assignee = :user OR :user MEMBER OF pt.additionalAssignees")
    List<ProjectTask> findUserProjectTasks(@Param("user") User user);

    @Query("SELECT DISTINCT pt FROM ProjectTask pt WHERE " +
           "pt.creator = :user OR pt.assignee = :user OR :user MEMBER OF pt.additionalAssignees")
    Page<ProjectTask> findUserProjectTasks(@Param("user") User user, Pageable pageable);

    // Find subtasks
    List<ProjectTask> findByParentTask(ProjectTask parentTask);

    // Count methods
    long countByProjectId(Long projectId);
    long countByProjectIdAndStatus(Long projectId, TaskStatus status);
    long countByCreator(User creator);

    @Query("SELECT COUNT(pt) FROM ProjectTask pt WHERE pt.creator = :user OR pt.assignee = :user OR :user MEMBER OF pt.additionalAssignees")
    long countUserProjectTasks(@Param("user") User user);

    // Find tasks by project and user participation
    @Query("SELECT DISTINCT pt FROM ProjectTask pt WHERE pt.project.id = :projectId AND " +
           "(pt.creator = :user OR pt.assignee = :user OR :user MEMBER OF pt.additionalAssignees)")
    List<ProjectTask> findProjectTasksByUserParticipation(@Param("projectId") Long projectId, @Param("user") User user);

    @Query("SELECT DISTINCT pt FROM ProjectTask pt WHERE pt.project.id = :projectId AND " +
           "(pt.creator = :user OR pt.assignee = :user OR :user MEMBER OF pt.additionalAssignees)")
    Page<ProjectTask> findProjectTasksByUserParticipation(@Param("projectId") Long projectId, @Param("user") User user, Pageable pageable);

    // Complex queries for filtering
    @Query("SELECT pt FROM ProjectTask pt WHERE " +
           "(:projectId IS NULL OR pt.project.id = :projectId) AND " +
           "(:status IS NULL OR pt.status = :status) AND " +
           "(:priority IS NULL OR pt.priority = :priority) AND " +
           "(:assigneeId IS NULL OR pt.assignee.id = :assigneeId) AND " +
           "(:creatorId IS NULL OR pt.creator.id = :creatorId)")
    Page<ProjectTask> findProjectTasksWithFilters(
        @Param("projectId") Long projectId,
        @Param("status") TaskStatus status,
        @Param("priority") TaskPriority priority,
        @Param("assigneeId") Long assigneeId,
        @Param("creatorId") Long creatorId,
        Pageable pageable
    );
}