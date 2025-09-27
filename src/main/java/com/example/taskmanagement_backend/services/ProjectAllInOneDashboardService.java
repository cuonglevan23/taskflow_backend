package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectAllInOneDashboardResponseDto;
import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectResponseDto;
import com.example.taskmanagement_backend.dtos.ProjectMemberDto.ProjectMemberResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProjectAllInOneDashboardService {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectTaskService projectTaskService; // ✅ FIX: Use ProjectTaskService thay vì TaskService

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private UserJpaRepository userRepository;

    /**
     * 🎯 Get comprehensive all-in-one project dashboard
     * Includes: stats, task breakdown, upcoming tasks, progress trends, team info
     */
    public ProjectAllInOneDashboardResponseDto getProjectAllInOneDashboard(Long projectId) {
        log.info("📊 [ProjectAllInOneDashboardService] Getting comprehensive dashboard for project: {}", projectId);

        try {
            // 1️⃣ Get basic project info
            ProjectResponseDto project = projectService.getProjectById(projectId);
            log.info("🔍 [Dashboard] Project info: id={}, name={}, isPersonal={}, teamId={}",
                    project.getId(), project.getName(), project.isPersonal(), project.getTeamId());

            // 2️⃣ Get all project tasks - ✅ FIX: Use ProjectTaskService thay vì TaskService
            List<TaskResponseDto> allTasks = projectTaskService.getTasksByProjectIdAsDto(projectId);
            log.info("📋 [Dashboard] Found {} tasks for project {}", allTasks.size(), projectId);

            // Debug task details
            if (!allTasks.isEmpty()) {
                allTasks.forEach(task -> {
                    log.info("📝 [Dashboard] Task: id={}, title={}, status={}, priority={}, assignedTo={}",
                            task.getId(), task.getTitle(), task.getStatus(), task.getPriority(),
                            task.getAssignedToIds() != null ? task.getAssignedToIds().size() : 0);
                });
            } else {
                log.warn("⚠️ [Dashboard] No tasks found for project {}! This might be the issue.", projectId);
            }

            // 3️⃣ Get team members (if team project)
            List<User> teamMembers = getProjectTeamMembers(project);
            log.info("👥 [Dashboard] Found {} team members for project {}", teamMembers.size(), projectId);

            // 4️⃣ Build comprehensive dashboard
            ProjectAllInOneDashboardResponseDto dashboard = ProjectAllInOneDashboardResponseDto.builder()
                    .projectInfo(buildProjectOverview(project))
                    .stats(buildProjectStats(project, allTasks))
                    .taskBreakdown(buildTaskBreakdown(allTasks, teamMembers))
                    .upcomingTasks(buildUpcomingTasks(allTasks))
                    .progressTrends(buildProgressTrends(allTasks, project))
                    .teamInfo(buildTeamInfo(project, teamMembers, allTasks))
                    .build();

            log.info("✅ [ProjectAllInOneDashboardService] Successfully built dashboard for project: {} with {} tasks, {} team members",
                    projectId, allTasks.size(), teamMembers.size());

            return dashboard;

        } catch (Exception e) {
            log.error("❌ [ProjectAllInOneDashboardService] Error building dashboard for project {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to build project dashboard: " + e.getMessage());
        }
    }

    // ===== 1️⃣ Project Overview =====
    private ProjectAllInOneDashboardResponseDto.ProjectOverview buildProjectOverview(ProjectResponseDto project) {
        return ProjectAllInOneDashboardResponseDto.ProjectOverview.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus() != null ? project.getStatus().toString() : "ACTIVE")
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .isPersonal(project.isPersonal())
                .teamId(project.getTeamId())
                .currentUserRole(project.getCurrentUserRole())
                .isCurrentUserMember(project.isCurrentUserMember())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    // ===== 2️⃣ Project Statistics =====
    private ProjectAllInOneDashboardResponseDto.ProjectStats buildProjectStats(ProjectResponseDto project, List<TaskResponseDto> tasks) {
        LocalDate today = LocalDate.now();

        log.info("🔍 [buildProjectStats] Building stats for {} tasks", tasks.size());

        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count(); // ✅ FIX: DONE thay vì COMPLETED
        int inProgressTasks = (int) tasks.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
        int pendingTasks = (int) tasks.stream().filter(t -> "PENDING".equals(t.getStatus()) || "TODO".equals(t.getStatus())).count();

        // Calculate overdue tasks
        int overdueTasks = (int) tasks.stream()
                .filter(t -> !("DONE".equals(t.getStatus())) && // ✅ FIX: DONE thay vì COMPLETED
                           t.getDeadline() != null &&
                           t.getDeadline().isBefore(today))
                .count();

        // Calculate completion rate
        double completionRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0.0;

        // Calculate days remaining
        int daysRemaining = project.getEndDate() != null ?
                (int) ChronoUnit.DAYS.between(today, project.getEndDate()) : -1;

        // Calculate project progress (weighted by task completion)
        double projectProgress = completionRate;

        // Tasks this week
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        int tasksThisWeek = (int) tasks.stream()
                .filter(t -> t.getCreatedAt() != null &&
                           !t.getCreatedAt().toLocalDate().isBefore(weekStart) &&
                           !t.getCreatedAt().toLocalDate().isAfter(weekEnd))
                .count();

        int tasksCompletedThisWeek = (int) tasks.stream()
                .filter(t -> "DONE".equals(t.getStatus()) && // ✅ FIX: DONE thay vì COMPLETED
                           t.getUpdatedAt() != null &&
                           !t.getUpdatedAt().toLocalDate().isBefore(weekStart) &&
                           !t.getUpdatedAt().toLocalDate().isAfter(weekEnd))
                .count();

        log.info("📊 [buildProjectStats] Stats: total={}, completed={}, inProgress={}, pending={}, overdue={}",
                totalTasks, completedTasks, inProgressTasks, pendingTasks, overdueTasks);

        return ProjectAllInOneDashboardResponseDto.ProjectStats.builder()
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .pendingTasks(pendingTasks)
                .inProgressTasks(inProgressTasks)
                .overdueTasks(overdueTasks)
                .completionRate(Math.round(completionRate * 100.0) / 100.0)
                .teamMembers(getTeamMemberCount(project))
                .daysRemaining(daysRemaining)
                .projectProgress(Math.round(projectProgress * 100.0) / 100.0)
                .tasksThisWeek(tasksThisWeek)
                .tasksCompletedThisWeek(tasksCompletedThisWeek)
                .build();
    }

    // ===== 3️⃣ Task Breakdown =====
    private ProjectAllInOneDashboardResponseDto.TaskBreakdown buildTaskBreakdown(List<TaskResponseDto> tasks, List<User> teamMembers) {
        int totalTasks = tasks.size();

        // By Status - ✅ FIX: Sử dụng DONE thay vì COMPLETED
        List<ProjectAllInOneDashboardResponseDto.StatusBreakdown> statusBreakdown = Arrays.asList(
                "DONE", "IN_PROGRESS", "PENDING", "TODO", "CANCELLED" // ✅ FIX: DONE thay vì COMPLETED
        ).stream().map(status -> {
            int count = (int) tasks.stream().filter(t -> status.equals(t.getStatus())).count();
            double percentage = totalTasks > 0 ? (double) count / totalTasks * 100 : 0.0;
            return ProjectAllInOneDashboardResponseDto.StatusBreakdown.builder()
                    .name(status)
                    .count(count)
                    .percentage(Math.round(percentage * 100.0) / 100.0)
                    .build();
        }).filter(s -> s.getCount() > 0).collect(Collectors.toList());

        // By Priority
        List<ProjectAllInOneDashboardResponseDto.PriorityBreakdown> priorityBreakdown = Arrays.asList(
                "HIGH", "MEDIUM", "LOW"
        ).stream().map(priority -> {
            int count = (int) tasks.stream().filter(t -> priority.equals(t.getPriority())).count();
            double percentage = totalTasks > 0 ? (double) count / totalTasks * 100 : 0.0;
            return ProjectAllInOneDashboardResponseDto.PriorityBreakdown.builder()
                    .name(priority)
                    .count(count)
                    .percentage(Math.round(percentage * 100.0) / 100.0)
                    .build();
        }).filter(p -> p.getCount() > 0).collect(Collectors.toList());

        // By Assignee - ✅ FIX: Sử dụng DONE thay vì COMPLETED
        List<ProjectAllInOneDashboardResponseDto.AssigneeBreakdown> assigneeBreakdown = teamMembers.stream()
                .map(member -> {
                    List<TaskResponseDto> memberTasks = tasks.stream()
                            .filter(t -> t.getAssignedToIds() != null && t.getAssignedToIds().contains(member.getId()))
                            .collect(Collectors.toList());

                    int taskCount = memberTasks.size();
                    int completedTasks = (int) memberTasks.stream()
                            .filter(t -> "DONE".equals(t.getStatus())) // ✅ FIX: DONE thay vì COMPLETED
                            .count();

                    double completionRate = taskCount > 0 ? (double) completedTasks / taskCount * 100 : 0.0;

                    return ProjectAllInOneDashboardResponseDto.AssigneeBreakdown.builder()
                            .userId(member.getId())
                            .name(member.getFirstName() + " " + member.getLastName())
                            .email(member.getEmail())
                            .count(taskCount)
                            .completedTasks(completedTasks)
                            .completionRate(Math.round(completionRate * 100.0) / 100.0)
                            .build();
                })
                .filter(a -> a.getCount() > 0)
                .collect(Collectors.toList());

        return ProjectAllInOneDashboardResponseDto.TaskBreakdown.builder()
                .byStatus(statusBreakdown)
                .byPriority(priorityBreakdown)
                .byAssignee(assigneeBreakdown)
                .build();
    }

    // ===== 4️⃣ Upcoming Tasks =====
    private ProjectAllInOneDashboardResponseDto.UpcomingTasks buildUpcomingTasks(List<TaskResponseDto> tasks) {
        LocalDate today = LocalDate.now();
        LocalDate threeDaysFromNow = today.plusDays(3);
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        // Helper method to convert task to summary
        java.util.function.Function<TaskResponseDto, ProjectAllInOneDashboardResponseDto.TaskSummary> toTaskSummary = task -> {
            boolean isOverdue = task.getDeadline() != null && task.getDeadline().isBefore(today) && !"COMPLETED".equals(task.getStatus());
            boolean isUrgent = task.getDeadline() != null && !task.getDeadline().isAfter(threeDaysFromNow) && !"COMPLETED".equals(task.getStatus());
            int daysOverdue = isOverdue ? (int) ChronoUnit.DAYS.between(task.getDeadline(), today) : 0;

            String assigneeName = "";
            String assigneeEmail = "";
            Long assigneeId = null;

            if (task.getAssignedToIds() != null && !task.getAssignedToIds().isEmpty()) {
                assigneeId = task.getAssignedToIds().get(0);
                if (task.getAssignedToEmails() != null && !task.getAssignedToEmails().isEmpty()) {
                    assigneeEmail = task.getAssignedToEmails().get(0);
                }
                // Try to get user name
                try {
                    Optional<User> user = userRepository.findById(assigneeId);
                    if (user.isPresent()) {
                        assigneeName = user.get().getFirstName() + " " + user.get().getLastName();
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch user name for ID: {}", assigneeId);
                }
            }

            return ProjectAllInOneDashboardResponseDto.TaskSummary.builder()
                    .id(task.getId())
                    .title(task.getTitle())
                    .status(task.getStatus())
                    .priority(task.getPriority())
                    .deadline(task.getDeadline())
                    .assigneeName(assigneeName)
                    .assigneeEmail(assigneeEmail)
                    .assigneeId(assigneeId)
                    .daysOverdue(daysOverdue)
                    .isOverdue(isOverdue)
                    .isUrgent(isUrgent)
                    .build();
        };

        // Urgent tasks (due within 3 days)
        List<ProjectAllInOneDashboardResponseDto.TaskSummary> urgentTasks = tasks.stream()
                .filter(t -> !"COMPLETED".equals(t.getStatus()) &&
                           t.getDeadline() != null &&
                           !t.getDeadline().isAfter(threeDaysFromNow) &&
                           !t.getDeadline().isBefore(today))
                .map(toTaskSummary)
                .sorted(Comparator.comparing(ProjectAllInOneDashboardResponseDto.TaskSummary::getDeadline))
                .collect(Collectors.toList());

        // Due today
        List<ProjectAllInOneDashboardResponseDto.TaskSummary> dueTodayTasks = tasks.stream()
                .filter(t -> !"COMPLETED".equals(t.getStatus()) &&
                           t.getDeadline() != null &&
                           t.getDeadline().equals(today))
                .map(toTaskSummary)
                .collect(Collectors.toList());

        // Overdue tasks
        List<ProjectAllInOneDashboardResponseDto.TaskSummary> overdueTasks = tasks.stream()
                .filter(t -> !"COMPLETED".equals(t.getStatus()) &&
                           t.getDeadline() != null &&
                           t.getDeadline().isBefore(today))
                .map(toTaskSummary)
                .sorted(Comparator.comparing(ProjectAllInOneDashboardResponseDto.TaskSummary::getDeadline))
                .collect(Collectors.toList());

        // This week tasks
        List<ProjectAllInOneDashboardResponseDto.TaskSummary> thisWeekTasks = tasks.stream()
                .filter(t -> !"COMPLETED".equals(t.getStatus()) &&
                           t.getDeadline() != null &&
                           !t.getDeadline().isBefore(today) &&
                           !t.getDeadline().isAfter(weekEnd))
                .map(toTaskSummary)
                .sorted(Comparator.comparing(ProjectAllInOneDashboardResponseDto.TaskSummary::getDeadline))
                .collect(Collectors.toList());

        return ProjectAllInOneDashboardResponseDto.UpcomingTasks.builder()
                .urgentTasks(urgentTasks)
                .dueTodayTasks(dueTodayTasks)
                .overdueTasks(overdueTasks)
                .thisWeekTasks(thisWeekTasks)
                .build();
    }

    // ===== 5️⃣ Progress Trends =====
    private ProjectAllInOneDashboardResponseDto.ProgressTrends buildProgressTrends(List<TaskResponseDto> tasks, ProjectResponseDto project) {
        LocalDate today = LocalDate.now();
        LocalDate projectStart = project.getStartDate() != null ? project.getStartDate() :
                                 tasks.stream()
                                      .filter(t -> t.getCreatedAt() != null)
                                      .map(t -> t.getCreatedAt().toLocalDate())
                                      .min(LocalDate::compareTo)
                                      .orElse(today.minusWeeks(4));

        // Weekly progress
        List<ProjectAllInOneDashboardResponseDto.WeeklyProgress> weeklyProgress = buildWeeklyProgress(tasks, projectStart, today);

        // Daily progress (last 7 days)
        List<ProjectAllInOneDashboardResponseDto.DailyProgress> dailyProgress = buildDailyProgress(tasks, today);

        // Velocity calculation
        ProjectAllInOneDashboardResponseDto.ProgressVelocity velocity = buildProgressVelocity(tasks, weeklyProgress, project);

        return ProjectAllInOneDashboardResponseDto.ProgressTrends.builder()
                .weeklyProgress(weeklyProgress)
                .dailyProgress(dailyProgress)
                .velocity(velocity)
                .build();
    }

    // ===== 6️⃣ Team Information =====
    private ProjectAllInOneDashboardResponseDto.TeamInfo buildTeamInfo(ProjectResponseDto project, List<User> teamMembers, List<TaskResponseDto> tasks) {
        if (project.isPersonal() || teamMembers.isEmpty()) {
            return ProjectAllInOneDashboardResponseDto.TeamInfo.builder()
                    .teamId(project.getTeamId())
                    .teamName("Personal Project")
                    .members(Collections.emptyList())
                    .totalMembers(0)
                    .build();
        }

        int totalTasks = tasks.size();

        List<ProjectAllInOneDashboardResponseDto.TeamMember> members = teamMembers.stream()
                .map(member -> {
                    int assignedTasks = (int) tasks.stream()
                            .filter(t -> t.getAssignedToIds() != null && t.getAssignedToIds().contains(member.getId()))
                            .count();

                    int completedTasks = (int) tasks.stream()
                            .filter(t -> "DONE".equals(t.getStatus()) && // ✅ FIX: DONE thay vì COMPLETED
                                       t.getAssignedToIds() != null &&
                                       t.getAssignedToIds().contains(member.getId()))
                            .count();

                    double workload = totalTasks > 0 ? (double) assignedTasks / totalTasks * 100 : 0.0;

                    return ProjectAllInOneDashboardResponseDto.TeamMember.builder()
                            .userId(member.getId())
                            .name(member.getFirstName() + " " + member.getLastName())
                            .email(member.getEmail())
                            .role("Member") // Default role, you can enhance this based on your role system
                            .assignedTasks(assignedTasks)
                            .completedTasks(completedTasks)
                            .workload(Math.round(workload * 100.0) / 100.0)
                            .build();
                })
                .collect(Collectors.toList());

        return ProjectAllInOneDashboardResponseDto.TeamInfo.builder()
                .teamId(project.getTeamId())
                .teamName("Project Team") // You can enhance this to get actual team name
                .members(members)
                .totalMembers(members.size())
                .build();
    }

    // ===== Helper Methods =====

    private List<User> getProjectTeamMembers(ProjectResponseDto project) {
        try {
            log.info("🔍 [getProjectTeamMembers] Getting members for project: {} (isPersonal: {})",
                    project.getId(), project.isPersonal());

            List<ProjectMemberResponseDto> projectMembers = projectMemberService.getMembersByProject(project.getId());
            log.info("📋 [getProjectTeamMembers] Found {} project members", projectMembers.size());

            // Convert to User entities
            List<User> users = projectMembers.stream()
                    .map(pm -> {
                        try {
                            log.info("🔍 [getProjectTeamMembers] Converting member: userId={}", pm.getUserId());
                            return userRepository.findById(pm.getUserId())
                                    .orElse(null);
                        } catch (Exception e) {
                            log.warn("Could not fetch user with ID {}: {}", pm.getUserId(), e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("✅ [getProjectTeamMembers] Successfully converted {} users", users.size());
            return users;
        } catch (Exception e) {
            log.error("❌ [getProjectTeamMembers] Error fetching team members for project {}: {}",
                    project.getId(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private int getTeamMemberCount(ProjectResponseDto project) {
        return getProjectTeamMembers(project).size();
    }

    private List<ProjectAllInOneDashboardResponseDto.WeeklyProgress> buildWeeklyProgress(List<TaskResponseDto> tasks, LocalDate projectStart, LocalDate today) {
        List<ProjectAllInOneDashboardResponseDto.WeeklyProgress> weeklyProgress = new ArrayList<>();

        LocalDate currentWeekStart = projectStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weekNumber = 1;

        while (!currentWeekStart.isAfter(today)) {
            final LocalDate weekStart = currentWeekStart; // Make variable final for lambda
            LocalDate weekEnd = currentWeekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            int plannedTasks = (int) tasks.stream()
                    .filter(t -> t.getCreatedAt() != null &&
                               !t.getCreatedAt().toLocalDate().isAfter(weekEnd))
                    .count();

            int completedTasks = (int) tasks.stream()
                    .filter(t -> "DONE".equals(t.getStatus()) && // ✅ FIX: DONE thay vì COMPLETED
                               t.getUpdatedAt() != null &&
                               !t.getUpdatedAt().toLocalDate().isBefore(weekStart) &&
                               !t.getUpdatedAt().toLocalDate().isAfter(weekEnd))
                    .count();

            double completionRate = plannedTasks > 0 ? (double) completedTasks / plannedTasks * 100 : 0.0;

            weeklyProgress.add(ProjectAllInOneDashboardResponseDto.WeeklyProgress.builder()
                    .week("Week " + weekNumber)
                    .weekStart(weekStart)
                    .weekEnd(weekEnd)
                    .planned(plannedTasks)
                    .completed(completedTasks)
                    .completionRate(Math.round(completionRate * 100.0) / 100.0)
                    .build());

            currentWeekStart = currentWeekStart.plusWeeks(1);
            weekNumber++;

            if (weekNumber > 12) break; // Limit to 12 weeks
        }

        return weeklyProgress;
    }

    private List<ProjectAllInOneDashboardResponseDto.DailyProgress> buildDailyProgress(List<TaskResponseDto> tasks, LocalDate today) {
        List<ProjectAllInOneDashboardResponseDto.DailyProgress> dailyProgress = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);

            int tasksCompleted = (int) tasks.stream()
                    .filter(t -> "DONE".equals(t.getStatus()) && // ✅ FIX: DONE thay vì COMPLETED
                               t.getUpdatedAt() != null &&
                               t.getUpdatedAt().toLocalDate().equals(date))
                    .count();

            int tasksCreated = (int) tasks.stream()
                    .filter(t -> t.getCreatedAt() != null &&
                               t.getCreatedAt().toLocalDate().equals(date))
                    .count();

            int tasksInProgress = (int) tasks.stream()
                    .filter(t -> "IN_PROGRESS".equals(t.getStatus()) &&
                               t.getUpdatedAt() != null &&
                               t.getUpdatedAt().toLocalDate().equals(date))
                    .count();

            dailyProgress.add(ProjectAllInOneDashboardResponseDto.DailyProgress.builder()
                    .date(date)
                    .tasksCompleted(tasksCompleted)
                    .tasksCreated(tasksCreated)
                    .tasksInProgress(tasksInProgress)
                    .build());
        }

        return dailyProgress;
    }

    private ProjectAllInOneDashboardResponseDto.ProgressVelocity buildProgressVelocity(List<TaskResponseDto> tasks, List<ProjectAllInOneDashboardResponseDto.WeeklyProgress> weeklyProgress, ProjectResponseDto project) {
        // Calculate average tasks per week
        double averageTasksPerWeek = weeklyProgress.isEmpty() ? 0.0 :
                weeklyProgress.stream()
                        .mapToInt(ProjectAllInOneDashboardResponseDto.WeeklyProgress::getCompleted)
                        .average()
                        .orElse(0.0);

        double averageTasksPerDay = averageTasksPerWeek / 7.0;

        // Calculate remaining tasks - ✅ FIX: DONE thay vì COMPLETED
        int remainingTasks = (int) tasks.stream()
                .filter(t -> !"DONE".equals(t.getStatus()))
                .count();

        // Estimate completion
        int estimatedDaysToCompletion = averageTasksPerDay > 0 ?
                (int) Math.ceil(remainingTasks / averageTasksPerDay) : -1;

        LocalDate estimatedCompletionDate = estimatedDaysToCompletion > 0 ?
                LocalDate.now().plusDays(estimatedDaysToCompletion) : null;

        return ProjectAllInOneDashboardResponseDto.ProgressVelocity.builder()
                .averageTasksPerWeek(Math.round(averageTasksPerWeek * 100.0) / 100.0)
                .averageTasksPerDay(Math.round(averageTasksPerDay * 100.0) / 100.0)
                .estimatedDaysToCompletion(estimatedDaysToCompletion)
                .estimatedCompletionDate(estimatedCompletionDate)
                .build();
    }
}
