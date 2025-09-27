package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.entities.TeamTask;
import com.example.taskmanagement_backend.entities.Team;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.enums.TaskPriority;
import com.example.taskmanagement_backend.repositories.TeamTaskJpaRepository;
import com.example.taskmanagement_backend.repositories.TeamJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.repositories.ProjectJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Transactional
public class TeamTaskService {

    private final TeamTaskJpaRepository teamTaskRepository;
    private final TeamJpaRepository teamRepository;
    private final UserJpaRepository userRepository;
    private final ProjectJpaRepository projectRepository;
    private final ProgressUpdateService progressUpdateService;

    // Task categories constants
    public static final List<String> TASK_CATEGORIES = Arrays.asList(
            "MEETING", "PLANNING", "REVIEW", "ADMIN", "TRAINING", "RESEARCH", "OTHER"
    );

    // ===== CRUD Operations =====

    /**
     * Create a new team task
     */
    public TeamTask createTeamTask(TeamTask teamTask) {
        // Validate team exists
        if (teamTask.getTeam() == null || teamTask.getTeam().getId() == null) {
            throw new IllegalArgumentException("Team is required for team task");
        }

        Team team = teamRepository.findById(teamTask.getTeam().getId())
                .orElseThrow(() -> new EntityNotFoundException("Team not found with id: " + teamTask.getTeam().getId()));

        // Set current user as creator if not set
        if (teamTask.getCreator() == null) {
            teamTask.setCreator(getCurrentUser());
        }

        // Validate task category
        if (teamTask.getTaskCategory() != null && !TASK_CATEGORIES.contains(teamTask.getTaskCategory())) {
            throw new IllegalArgumentException("Invalid task category. Must be one of: " + TASK_CATEGORIES);
        }

        teamTask.setTeam(team);
        TeamTask savedTask = teamTaskRepository.save(teamTask);

        // ✅ AUTO-UPDATE: Update progress when new team task is created
        progressUpdateService.updateProgressOnTeamTaskChange(savedTask);

        return savedTask;
    }

    /**
     * Get all team tasks with pagination
     */
    @Transactional(readOnly = true)
    public Page<TeamTask> getAllTeamTasks(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                   Sort.by(sortBy).descending() :
                   Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return teamTaskRepository.findAll(pageable);
    }

    /**
     * Get team task by ID
     */
    @Transactional(readOnly = true)
    public TeamTask getTeamTaskById(Long id) {
        return teamTaskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team task not found with id: " + id));
    }

    /**
     * Update team task
     */
    public TeamTask updateTeamTask(Long id, TeamTask updateData) {
        TeamTask existingTask = getTeamTaskById(id);

        // Update fields if provided
        if (updateData.getTitle() != null) {
            existingTask.setTitle(updateData.getTitle());
        }
        if (updateData.getDescription() != null) {
            existingTask.setDescription(updateData.getDescription());
        }
        if (updateData.getStatus() != null) {
            existingTask.setStatus(updateData.getStatus());
        }
        if (updateData.getPriority() != null) {
            existingTask.setPriority(updateData.getPriority());
        }
        if (updateData.getStartDate() != null) {
            existingTask.setStartDate(updateData.getStartDate());
        }
        if (updateData.getDeadline() != null) {
            existingTask.setDeadline(updateData.getDeadline());
        }
        if (updateData.getEstimatedHours() != null) {
            existingTask.setEstimatedHours(updateData.getEstimatedHours());
        }
        if (updateData.getActualHours() != null) {
            existingTask.setActualHours(updateData.getActualHours());
        }
        if (updateData.getProgressPercentage() != null) {
            existingTask.setProgressPercentage(updateData.getProgressPercentage());
        }
        if (updateData.getTaskCategory() != null) {
            if (!TASK_CATEGORIES.contains(updateData.getTaskCategory())) {
                throw new IllegalArgumentException("Invalid task category. Must be one of: " + TASK_CATEGORIES);
            }
            existingTask.setTaskCategory(updateData.getTaskCategory());
        }
        if (updateData.getAssignee() != null) {
            existingTask.setAssignee(updateData.getAssignee());
        }
        if (updateData.getAssignedMembers() != null) {
            existingTask.setAssignedMembers(updateData.getAssignedMembers());
        }
        if (updateData.getRelatedProject() != null) {
            existingTask.setRelatedProject(updateData.getRelatedProject());
        }
        if (updateData.getIsRecurring() != null) {
            existingTask.setIsRecurring(updateData.getIsRecurring());
        }
        if (updateData.getRecurrencePattern() != null) {
            existingTask.setRecurrencePattern(updateData.getRecurrencePattern());
        }
        if (updateData.getRecurrenceEndDate() != null) {
            existingTask.setRecurrenceEndDate(updateData.getRecurrenceEndDate());
        }

        TeamTask savedTask = teamTaskRepository.save(existingTask);

        // ✅ AUTO-UPDATE: Update progress when team task is updated (especially status changes)
        progressUpdateService.updateProgressOnTeamTaskChange(savedTask);

        return savedTask;
    }

    /**
     * Delete team task
     */
    public void deleteTeamTask(Long id) {
        TeamTask task = getTeamTaskById(id);
        teamTaskRepository.delete(task);

        // ✅ AUTO-UPDATE: Update progress when team task is deleted
        progressUpdateService.updateProgressOnTeamTaskChange(task);
    }

    // ===== Query Methods =====

    /**
     * Get tasks by team ID
     */
    @Transactional(readOnly = true)
    public Page<TeamTask> getTasksByTeamId(Long teamId, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                   Sort.by(sortBy).descending() :
                   Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return teamTaskRepository.findByTeamId(teamId, pageable);
    }

    /**
     * Get tasks by team ID (all)
     */
    @Transactional(readOnly = true)
    public List<TeamTask> getTasksByTeamId(Long teamId) {
        return teamTaskRepository.findByTeamId(teamId);
    }

    /**
     * Get user's team tasks (where user is creator, assignee, or assigned member)
     */
    @Transactional(readOnly = true)
    public Page<TeamTask> getUserTeamTasks(Long userId, int page, int size, String sortBy, String sortDir) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                   Sort.by(sortBy).descending() :
                   Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return teamTaskRepository.findUserTeamTasks(user, pageable);
    }

    /**
     * Get current user's team tasks
     */
    @Transactional(readOnly = true)
    public Page<TeamTask> getCurrentUserTeamTasks(int page, int size, String sortBy, String sortDir) {
        User currentUser = getCurrentUser();
        return getUserTeamTasks(currentUser.getId(), page, size, sortBy, sortDir);
    }

    /**
     * Get tasks with filters
     */
    @Transactional(readOnly = true)
    public Page<TeamTask> getTeamTasksWithFilters(
            Long teamId, TaskStatus status, TaskPriority priority, String taskCategory,
            Long assigneeId, Long creatorId, Long relatedProjectId,
            int page, int size, String sortBy, String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                   Sort.by(sortBy).descending() :
                   Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return teamTaskRepository.findTeamTasksWithFilters(
                teamId, status, priority, taskCategory, assigneeId, creatorId, relatedProjectId, pageable);
    }

    /**
     * Get tasks by category
     */
    @Transactional(readOnly = true)
    public List<TeamTask> getTasksByTeamAndCategory(Long teamId, String category) {
        if (!TASK_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Invalid task category. Must be one of: " + TASK_CATEGORIES);
        }
        return teamTaskRepository.findByTeamIdAndTaskCategory(teamId, category);
    }

    /**
     * Get recurring tasks by team
     */
    @Transactional(readOnly = true)
    public List<TeamTask> getRecurringTasksByTeam(Long teamId) {
        return teamTaskRepository.findByTeamIdAndIsRecurringTrue(teamId);
    }

    /**
     * Get overdue tasks by team
     */
    @Transactional(readOnly = true)
    public List<TeamTask> getOverdueTasksByTeam(Long teamId) {
        return teamTaskRepository.findOverdueTasksByTeam(teamId);
    }

    /**
     * Get tasks by status
     */
    @Transactional(readOnly = true)
    public List<TeamTask> getTasksByTeamAndStatus(Long teamId, TaskStatus status) {
        return teamTaskRepository.findByTeamIdAndStatus(teamId, status);
    }

    /**
     * Get tasks for current week
     */
    @Transactional(readOnly = true)
    public List<TeamTask> getTasksForCurrentWeek(Long teamId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate weekEnd = weekStart.plusDays(6);

        return teamTaskRepository.findTeamTasksForWeek(teamId, weekStart, weekEnd);
    }

    /**
     * Get upcoming recurring tasks
     */
    @Transactional(readOnly = true)
    public List<TeamTask> getUpcomingRecurringTasks(Long teamId, int daysAhead) {
        LocalDate futureDate = LocalDate.now().plusDays(daysAhead);
        return teamTaskRepository.findUpcomingRecurringTasks(teamId, futureDate);
    }

    /**
     * Get subtasks
     */
    @Transactional(readOnly = true)
    public List<TeamTask> getSubtasks(Long parentTaskId) {
        TeamTask parentTask = getTeamTaskById(parentTaskId);
        return teamTaskRepository.findByParentTask(parentTask);
    }

    // ===== Statistics Methods =====

    /**
     * Get team task statistics
     */
    @Transactional(readOnly = true)
    public TeamTaskStats getTeamTaskStats(Long teamId) {
        long totalTasks = teamTaskRepository.countByTeamId(teamId);
        long completedTasks = teamTaskRepository.countByTeamIdAndStatus(teamId, TaskStatus.DONE);
        long inProgressTasks = teamTaskRepository.countByTeamIdAndStatus(teamId, TaskStatus.IN_PROGRESS);
        long todoTasks = teamTaskRepository.countByTeamIdAndStatus(teamId, TaskStatus.TODO);

        List<TeamTask> overdueTasks = teamTaskRepository.findOverdueTasksByTeam(teamId);
        List<TeamTask> recurringTasks = teamTaskRepository.findByTeamIdAndIsRecurringTrue(teamId);

        // Count by categories
        long meetingTasks = teamTaskRepository.countByTeamIdAndTaskCategory(teamId, "MEETING");
        long planningTasks = teamTaskRepository.countByTeamIdAndTaskCategory(teamId, "PLANNING");
        long reviewTasks = teamTaskRepository.countByTeamIdAndTaskCategory(teamId, "REVIEW");

        double completionPercentage = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

        return TeamTaskStats.builder()
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .inProgressTasks(inProgressTasks)
                .todoTasks(todoTasks)
                .overdueTasks(overdueTasks.size())
                .recurringTasks(recurringTasks.size())
                .meetingTasks(meetingTasks)
                .planningTasks(planningTasks)
                .reviewTasks(reviewTasks)
                .completionPercentage(completionPercentage)
                .build();
    }

    /**
     * Get user team task statistics
     */
    @Transactional(readOnly = true)
    public UserTeamTaskStats getUserTeamTaskStats(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        long totalTasks = teamTaskRepository.countUserTeamTasks(user);

        return UserTeamTaskStats.builder()
                .userId(userId)
                .totalTeamTasks(totalTasks)
                .build();
    }

    // ===== Helper Methods =====

    /**
     * Get current authenticated user
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    /**
     * Assign task to team member
     */
    public TeamTask assignTaskToMember(Long taskId, Long userId) {
        TeamTask task = getTeamTaskById(taskId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        task.setAssignee(user);
        return teamTaskRepository.save(task);
    }

    /**
     * Update task progress
     */
    public TeamTask updateTaskProgress(Long taskId, Integer progressPercentage) {
        TeamTask task = getTeamTaskById(taskId);

        if (progressPercentage < 0 || progressPercentage > 100) {
            throw new IllegalArgumentException("Progress percentage must be between 0 and 100");
        }

        task.setProgressPercentage(progressPercentage);

        // Auto-update status based on progress
        if (progressPercentage == 0) {
            task.setStatus(TaskStatus.TODO);
        } else if (progressPercentage == 100) {
            task.setStatus(TaskStatus.DONE);
        } else {
            task.setStatus(TaskStatus.IN_PROGRESS);
        }

        return teamTaskRepository.save(task);
    }

    /**
     * Link task to project
     */
    public TeamTask linkTaskToProject(Long taskId, Long projectId) {
        TeamTask task = getTeamTaskById(taskId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found with id: " + projectId));

        task.setRelatedProject(project);
        return teamTaskRepository.save(task);
    }

    /**
     * Get task categories
     */
    public List<String> getTaskCategories() {
        return TASK_CATEGORIES;
    }

    // ===== Inner Classes =====

    public static class TeamTaskStats {
        private long totalTasks;
        private long completedTasks;
        private long inProgressTasks;
        private long todoTasks;
        private long overdueTasks;
        private long recurringTasks;
        private long meetingTasks;
        private long planningTasks;
        private long reviewTasks;
        private double completionPercentage;

        public static TeamTaskStatsBuilder builder() {
            return new TeamTaskStatsBuilder();
        }

        public static class TeamTaskStatsBuilder {
            private long totalTasks;
            private long completedTasks;
            private long inProgressTasks;
            private long todoTasks;
            private long overdueTasks;
            private long recurringTasks;
            private long meetingTasks;
            private long planningTasks;
            private long reviewTasks;
            private double completionPercentage;

            public TeamTaskStatsBuilder totalTasks(long totalTasks) {
                this.totalTasks = totalTasks;
                return this;
            }

            public TeamTaskStatsBuilder completedTasks(long completedTasks) {
                this.completedTasks = completedTasks;
                return this;
            }

            public TeamTaskStatsBuilder inProgressTasks(long inProgressTasks) {
                this.inProgressTasks = inProgressTasks;
                return this;
            }

            public TeamTaskStatsBuilder todoTasks(long todoTasks) {
                this.todoTasks = todoTasks;
                return this;
            }

            public TeamTaskStatsBuilder overdueTasks(long overdueTasks) {
                this.overdueTasks = overdueTasks;
                return this;
            }

            public TeamTaskStatsBuilder recurringTasks(long recurringTasks) {
                this.recurringTasks = recurringTasks;
                return this;
            }

            public TeamTaskStatsBuilder meetingTasks(long meetingTasks) {
                this.meetingTasks = meetingTasks;
                return this;
            }

            public TeamTaskStatsBuilder planningTasks(long planningTasks) {
                this.planningTasks = planningTasks;
                return this;
            }

            public TeamTaskStatsBuilder reviewTasks(long reviewTasks) {
                this.reviewTasks = reviewTasks;
                return this;
            }

            public TeamTaskStatsBuilder completionPercentage(double completionPercentage) {
                this.completionPercentage = completionPercentage;
                return this;
            }

            public TeamTaskStats build() {
                TeamTaskStats stats = new TeamTaskStats();
                stats.totalTasks = this.totalTasks;
                stats.completedTasks = this.completedTasks;
                stats.inProgressTasks = this.inProgressTasks;
                stats.todoTasks = this.todoTasks;
                stats.overdueTasks = this.overdueTasks;
                stats.recurringTasks = this.recurringTasks;
                stats.meetingTasks = this.meetingTasks;
                stats.planningTasks = this.planningTasks;
                stats.reviewTasks = this.reviewTasks;
                stats.completionPercentage = this.completionPercentage;
                return stats;
            }
        }

        // Getters
        public long getTotalTasks() { return totalTasks; }
        public long getCompletedTasks() { return completedTasks; }
        public long getInProgressTasks() { return inProgressTasks; }
        public long getTodoTasks() { return todoTasks; }
        public long getOverdueTasks() { return overdueTasks; }
        public long getRecurringTasks() { return recurringTasks; }
        public long getMeetingTasks() { return meetingTasks; }
        public long getPlanningTasks() { return planningTasks; }
        public long getReviewTasks() { return reviewTasks; }
        public double getCompletionPercentage() { return completionPercentage; }
    }

    public static class UserTeamTaskStats {
        private Long userId;
        private long totalTeamTasks;

        public static UserTeamTaskStatsBuilder builder() {
            return new UserTeamTaskStatsBuilder();
        }

        public static class UserTeamTaskStatsBuilder {
            private Long userId;
            private long totalTeamTasks;

            public UserTeamTaskStatsBuilder userId(Long userId) {
                this.userId = userId;
                return this;
            }

            public UserTeamTaskStatsBuilder totalTeamTasks(long totalTeamTasks) {
                this.totalTeamTasks = totalTeamTasks;
                return this;
            }

            public UserTeamTaskStats build() {
                UserTeamTaskStats stats = new UserTeamTaskStats();
                stats.userId = this.userId;
                stats.totalTeamTasks = this.totalTeamTasks;
                return stats;
            }
        }

        // Getters
        public Long getUserId() { return userId; }
        public long getTotalTeamTasks() { return totalTeamTasks; }
    }
}