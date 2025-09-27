package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectDashboardResponseDto;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.enums.ProjectStatus;
import com.example.taskmanagement_backend.repositories.ProjectJpaRepository;
import com.example.taskmanagement_backend.repositories.TaskJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectDashboardService {

    private final ProjectJpaRepository projectRepository;
    private final TaskJpaRepository taskRepository;
    private final UserJpaRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Cache configuration
    private static final String CACHE_PREFIX = "dashboard:project:overview:";
    private static final long CACHE_TTL_MINUTES = 5; // 5 minutes cache

    /**
     * Get project dashboard overview with Redis cache
     */
    public ProjectDashboardResponseDto getProjectDashboard(Long projectId) {
        String cacheKey = CACHE_PREFIX + projectId;

        log.info("🔍 [ProjectDashboardService] Getting dashboard for project ID: {}", projectId);

        // Try to get from cache first
        try {
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.info("✅ [ProjectDashboardService] Cache HIT for project: {}", projectId);
                ProjectDashboardResponseDto cachedResponse = objectMapper.readValue(cachedData, ProjectDashboardResponseDto.class);

                // Update cache info to show it's from cache
                cachedResponse.getCacheInfo().setFromCache(true);

                return cachedResponse;
            }
        } catch (Exception e) {
            log.warn("⚠️ [ProjectDashboardService] Cache read error for project {}: {}", projectId, e.getMessage());
        }

        log.info("❌ [ProjectDashboardService] Cache MISS for project: {} - generating new data", projectId);

        // Cache miss - generate fresh data
        ProjectDashboardResponseDto response = generateProjectDashboard(projectId);

        // Cache the result
        try {
            String jsonData = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonData, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.info("✅ [ProjectDashboardService] Cached dashboard data for project: {} (TTL: {} minutes)",
                    projectId, CACHE_TTL_MINUTES);
        } catch (JsonProcessingException e) {
            log.error("❌ [ProjectDashboardService] Failed to cache dashboard data for project {}: {}",
                    projectId, e.getMessage());
        }

        return response;
    }

    /**
     * Invalidate project dashboard cache
     */
    public void invalidateProjectDashboardCache(Long projectId) {
        String cacheKey = CACHE_PREFIX + projectId;
        Boolean deleted = redisTemplate.delete(cacheKey);
        log.info("🗑️ [ProjectDashboardService] Cache invalidated for project: {} (deleted: {})", projectId, deleted);
    }

    /**
     * Generate fresh project dashboard data from database
     */
    private ProjectDashboardResponseDto generateProjectDashboard(Long projectId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        // Get project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        // Get all tasks for this project
        List<Task> projectTasks = taskRepository.findByProjectId(projectId);

        log.info("📊 [ProjectDashboardService] Found {} tasks for project: {}", projectTasks.size(), projectId);

        // Project Statistics
        ProjectDashboardResponseDto.ProjectStatistics projectStats = generateProjectStatistics(project, projectTasks, startOfMonth, now);

        // Task Breakdown
        ProjectDashboardResponseDto.TaskBreakdown taskBreakdown = generateTaskBreakdown(projectTasks);

        // Upcoming Tasks
        ProjectDashboardResponseDto.UpcomingTasks upcomingTasks = generateUpcomingTasks(projectTasks, now);

        // Completion Trends
        ProjectDashboardResponseDto.CompletionTrends completionTrends = generateCompletionTrends(projectTasks, now);

        // Cache Info
        ProjectDashboardResponseDto.CacheInfo cacheInfo = ProjectDashboardResponseDto.CacheInfo.builder()
                .fromCache(false)
                .cacheGeneratedAt(now)
                .cacheKey(CACHE_PREFIX + projectId)
                .cacheExpiresInSeconds(CACHE_TTL_MINUTES * 60)
                .build();

        return ProjectDashboardResponseDto.builder()
                .projectStats(projectStats)
                .taskBreakdown(taskBreakdown)
                .upcomingTasks(upcomingTasks)
                .completionTrends(completionTrends)
                .cacheInfo(cacheInfo)
                .build();
    }

    private ProjectDashboardResponseDto.ProjectStatistics generateProjectStatistics(
            Project project, List<Task> projectTasks, LocalDateTime startOfMonth, LocalDateTime now) {

        long totalProjects = 1; // Just this project
        long activeProjects = project.getStatus() != null &&
                project.getStatus() != ProjectStatus.COMPLETED ? 1 : 0;
        long completedProjects = project.getStatus() != null &&
                project.getStatus() == ProjectStatus.COMPLETED ? 1 : 0;

        long totalTasks = projectTasks.size();
        long completedTasks = projectTasks.stream()
                .mapToLong(task -> "COMPLETED".equalsIgnoreCase(task.getStatusKey()) ? 1 : 0)
                .sum();
        long pendingTasks = totalTasks - completedTasks;

        // Overdue tasks (deadline passed and not completed)
        LocalDate today = now.toLocalDate();
        long overdueTasks = projectTasks.stream().mapToLong(task -> {
            if (task.getDeadline() != null &&
                task.getDeadline().isBefore(today) &&
                !"COMPLETED".equalsIgnoreCase(task.getStatusKey())) {
                return 1;
            }
            return 0;
        }).sum();

        // Tasks this month
        long tasksThisMonth = projectTasks.stream()
                .mapToLong(task -> task.getCreatedAt().isAfter(startOfMonth) ? 1 : 0)
                .sum();

        // Tasks completed this month
        long tasksCompletedThisMonth = projectTasks.stream()
                .mapToLong(task -> "COMPLETED".equalsIgnoreCase(task.getStatusKey()) &&
                        task.getUpdatedAt().isAfter(startOfMonth) ? 1 : 0)
                .sum();

        double completionRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0.0;

        return ProjectDashboardResponseDto.ProjectStatistics.builder()
                .totalProjects(totalProjects)
                .activeProjects(activeProjects)
                .completedProjects(completedProjects)
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .pendingTasks(pendingTasks)
                .overdueTasks(overdueTasks)
                .completionRate(Math.round(completionRate * 100.0) / 100.0)
                .tasksThisMonth(tasksThisMonth)
                .tasksCompletedThisMonth(tasksCompletedThisMonth)
                .build();
    }

    private ProjectDashboardResponseDto.TaskBreakdown generateTaskBreakdown(List<Task> projectTasks) {
        // By Status
        Map<String, List<Task>> tasksByStatus = projectTasks.stream()
                .collect(Collectors.groupingBy(task ->
                    task.getStatusKey() != null ? task.getStatusKey() : "UNKNOWN"));

        List<ProjectDashboardResponseDto.CategoryStats> byStatus = tasksByStatus.entrySet().stream()
                .map(entry -> {
                    List<Task> statusTasks = entry.getValue();
                    long completed = statusTasks.stream()
                            .mapToLong(task -> "COMPLETED".equalsIgnoreCase(task.getStatusKey()) ? 1 : 0)
                            .sum();
                    return ProjectDashboardResponseDto.CategoryStats.builder()
                            .id(null) // Status doesn't have ID
                            .name(entry.getKey())
                            .count((long) statusTasks.size())
                            .completed(completed)
                            .pending(statusTasks.size() - completed)
                            .completionRate(statusTasks.size() > 0 ?
                                    Math.round((double) completed / statusTasks.size() * 10000.0) / 100.0 : 0.0)
                            .build();
                })
                .collect(Collectors.toList());

        // Create a map of tasks by creator
        // Since tasks don't have a direct getAssignee method, we'll use task creator instead
        Map<User, List<Task>> tasksByCreator = projectTasks.stream()
                .filter(task -> task.getCreator() != null)
                .collect(Collectors.groupingBy(Task::getCreator));

        List<ProjectDashboardResponseDto.CategoryStats> byAssignee = tasksByCreator.entrySet().stream()
                .map(entry -> {
                    List<Task> creatorTasks = entry.getValue();
                    long completed = creatorTasks.stream()
                            .mapToLong(task -> "COMPLETED".equalsIgnoreCase(task.getStatusKey()) ? 1 : 0)
                            .sum();
                    User user = entry.getKey();
                    // Get display name from user or user profile
                    String displayName = getUserDisplayName(user);
                    return ProjectDashboardResponseDto.CategoryStats.builder()
                            .id(user.getId())
                            .name(displayName)
                            .count((long) creatorTasks.size())
                            .completed(completed)
                            .pending(creatorTasks.size() - completed)
                            .completionRate(creatorTasks.size() > 0 ?
                                    Math.round((double) completed / creatorTasks.size() * 10000.0) / 100.0 : 0.0)
                            .build();
                })
                .collect(Collectors.toList());

        // By Priority (use priorityKey String instead of Priority enum)
        Map<String, List<Task>> tasksByPriority = projectTasks.stream()
                .filter(task -> task.getPriorityKey() != null)
                .collect(Collectors.groupingBy(Task::getPriorityKey));

        List<ProjectDashboardResponseDto.CategoryStats> byPriority = tasksByPriority.entrySet().stream()
                .map(entry -> {
                    List<Task> priorityTasks = entry.getValue();
                    long completed = priorityTasks.stream()
                            .mapToLong(task -> "COMPLETED".equalsIgnoreCase(task.getStatusKey()) ? 1 : 0)
                            .sum();
                    return ProjectDashboardResponseDto.CategoryStats.builder()
                            .id(null) // Priority doesn't have ID
                            .name(entry.getKey())
                            .count((long) priorityTasks.size())
                            .completed(completed)
                            .pending(priorityTasks.size() - completed)
                            .completionRate(priorityTasks.size() > 0 ?
                                    Math.round((double) completed / priorityTasks.size() * 10000.0) / 100.0 : 0.0)
                            .build();
                })
                .collect(Collectors.toList());

        return ProjectDashboardResponseDto.TaskBreakdown.builder()
                .byStatus(byStatus)
                .byAssignee(byAssignee)
                .byPriority(byPriority)
                .build();
    }

    /**
     * Helper method to get a display name for a user
     */
    private String getUserDisplayName(User user) {
        if (user == null) {
            return "Unknown";
        }

        // Check if user has a profile with name information
        if (user.getUserProfile() != null) {
            UserProfile profile = user.getUserProfile();
            if (profile.getFirstName() != null && profile.getLastName() != null) {
                return profile.getFirstName() + " " + profile.getLastName();
            }
            if (profile.getFirstName() != null) {
                return profile.getFirstName();
            }
            if (profile.getUsername() != null) {
                return profile.getUsername();
            }
        }

        // Fall back to email if no better name is available
        return user.getEmail();
    }

    private ProjectDashboardResponseDto.UpcomingTasks generateUpcomingTasks(List<Task> projectTasks, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate endOfWeek = today.plusDays(7 - today.getDayOfWeek().getValue());
        LocalDate endOfNextWeek = endOfWeek.plusDays(7);

        // Convert tasks to TaskSummary
        List<ProjectDashboardResponseDto.TaskSummary> allTaskSummaries = projectTasks.stream()
                .map(task -> {
                    // Get primary creator name as assignee name since there's no direct getAssignee
                    String assigneeName = null;
                    if (task.getCreator() != null) {
                        assigneeName = getUserDisplayName(task.getCreator());
                    }

                    return ProjectDashboardResponseDto.TaskSummary.builder()
                            .id(task.getId())
                            .title(task.getTitle())
                            .status(task.getStatusKey())
                            .deadline(task.getDeadline() != null ?
                                    task.getDeadline().atStartOfDay() : null)
                            .assigneeName(assigneeName)
                            .priority(task.getPriorityKey())
                            .projectId(task.getProject().getId())
                            .projectName(task.getProject().getName())
                            .build();
                })
                .collect(Collectors.toList());

        // Group by deadline
        List<ProjectDashboardResponseDto.TaskSummary> thisWeek = allTaskSummaries.stream()
                .filter(task -> task.getDeadline() != null &&
                        !task.getStatus().equalsIgnoreCase("COMPLETED") &&
                        task.getDeadline().toLocalDate().isAfter(today) &&
                        (task.getDeadline().toLocalDate().isBefore(endOfWeek) ||
                         task.getDeadline().toLocalDate().isEqual(endOfWeek)))
                .collect(Collectors.toList());

        List<ProjectDashboardResponseDto.TaskSummary> nextWeek = allTaskSummaries.stream()
                .filter(task -> task.getDeadline() != null &&
                        !task.getStatus().equalsIgnoreCase("COMPLETED") &&
                        task.getDeadline().toLocalDate().isAfter(endOfWeek) &&
                        (task.getDeadline().toLocalDate().isBefore(endOfNextWeek) ||
                         task.getDeadline().toLocalDate().isEqual(endOfNextWeek)))
                .collect(Collectors.toList());

        List<ProjectDashboardResponseDto.TaskSummary> later = allTaskSummaries.stream()
                .filter(task -> task.getDeadline() != null &&
                        !task.getStatus().equalsIgnoreCase("COMPLETED") &&
                        task.getDeadline().toLocalDate().isAfter(endOfNextWeek))
                .collect(Collectors.toList());

        List<ProjectDashboardResponseDto.TaskSummary> overdue = allTaskSummaries.stream()
                .filter(task -> task.getDeadline() != null &&
                        !task.getStatus().equalsIgnoreCase("COMPLETED") &&
                        task.getDeadline().toLocalDate().isBefore(today))
                .collect(Collectors.toList());

        return ProjectDashboardResponseDto.UpcomingTasks.builder()
                .thisWeek(thisWeek)
                .nextWeek(nextWeek)
                .later(later)
                .overdue(overdue)
                .build();
    }

    private ProjectDashboardResponseDto.CompletionTrends generateCompletionTrends(List<Task> projectTasks, LocalDateTime now) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate today = now.toLocalDate();
        LocalDate thirtyDaysAgo = today.minusDays(30);

        // Tasks completed by day (last 30 days)
        Map<String, Long> completedTasksByDay = projectTasks.stream()
                .filter(task -> "COMPLETED".equalsIgnoreCase(task.getStatusKey()) &&
                        task.getUpdatedAt() != null &&
                        task.getUpdatedAt().toLocalDate().isAfter(thirtyDaysAgo) &&
                        !task.getUpdatedAt().toLocalDate().isAfter(today))
                .collect(Collectors.groupingBy(
                        task -> task.getUpdatedAt().toLocalDate().format(formatter),
                        Collectors.counting()
                ));

        // Tasks created by day (last 30 days)
        Map<String, Long> createdTasksByDay = projectTasks.stream()
                .filter(task -> task.getCreatedAt() != null &&
                        task.getCreatedAt().toLocalDate().isAfter(thirtyDaysAgo) &&
                        !task.getCreatedAt().toLocalDate().isAfter(today))
                .collect(Collectors.groupingBy(
                        task -> task.getCreatedAt().toLocalDate().format(formatter),
                        Collectors.counting()
                ));

        // Completion rate by week
        Map<Integer, List<Task>> tasksByWeek = projectTasks.stream()
                .filter(task -> task.getCreatedAt() != null &&
                        task.getCreatedAt().toLocalDate().isAfter(thirtyDaysAgo.minusDays(7)))
                .collect(Collectors.groupingBy(
                        task -> task.getCreatedAt().toLocalDate().get(
                                java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).weekOfWeekBasedYear())
                ));

        Map<String, Double> completionRateByWeek = new HashMap<>();
        tasksByWeek.forEach((week, tasks) -> {
            long totalInWeek = tasks.size();
            long completedInWeek = tasks.stream()
                    .filter(task -> "COMPLETED".equalsIgnoreCase(task.getStatusKey()))
                    .count();
            double rate = totalInWeek > 0 ? (double) completedInWeek / totalInWeek * 100 : 0.0;
            completionRateByWeek.put("Week " + week, Math.round(rate * 100.0) / 100.0);
        });

        return ProjectDashboardResponseDto.CompletionTrends.builder()
                .completedTasksByDay(completedTasksByDay)
                .createdTasksByDay(createdTasksByDay)
                .completionRateByWeek(completionRateByWeek)
                .build();
    }
}
