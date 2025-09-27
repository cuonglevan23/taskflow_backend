package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.DashboardDto.DashboardOverviewResponseDto;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.TaskJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TaskJpaRepository taskRepository;
    private final UserJpaRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Cache configuration
    private static final String CACHE_PREFIX = "dashboard:overview:user:";
    private static final long CACHE_TTL_MINUTES = 5; // 5 minutes cache

    /**
     * Get dashboard overview for current user with Redis cache
     */
    public DashboardOverviewResponseDto getDashboardOverview() {
        User currentUser = getCurrentUser();
        String cacheKey = CACHE_PREFIX + currentUser.getId();

        log.info("🔍 [DashboardService] Getting dashboard for user: {} (ID: {})",
                currentUser.getEmail(), currentUser.getId());

        // Try to get from cache first
        try {
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.info("✅ [DashboardService] Cache HIT for user: {}", currentUser.getId());
                DashboardOverviewResponseDto cachedResponse = objectMapper.readValue(cachedData, DashboardOverviewResponseDto.class);

                // Update cache info to show it's from cache
                cachedResponse.getCacheInfo().setFromCache(true);

                return cachedResponse;
            }
        } catch (Exception e) {
            log.warn("⚠️ [DashboardService] Cache read error for user {}: {}", currentUser.getId(), e.getMessage());
        }

        log.info("❌ [DashboardService] Cache MISS for user: {} - generating new data", currentUser.getId());

        // Cache miss - generate fresh data
        DashboardOverviewResponseDto response = generateDashboardData(currentUser);

        // Cache the result
        try {
            String jsonData = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonData, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.info("✅ [DashboardService] Cached dashboard data for user: {} (TTL: {} minutes)",
                    currentUser.getId(), CACHE_TTL_MINUTES);
        } catch (JsonProcessingException e) {
            log.error("❌ [DashboardService] Failed to cache dashboard data for user {}: {}",
                    currentUser.getId(), e.getMessage());
        }

        return response;
    }

    /**
     * Invalidate cache when user's tasks are modified
     */
    public void invalidateDashboardCache(Long userId) {
        String cacheKey = CACHE_PREFIX + userId;
        Boolean deleted = redisTemplate.delete(cacheKey);
        log.info("🗑️ [DashboardService] Cache invalidated for user: {} (deleted: {})", userId, deleted);
    }

    /**
     * Generate fresh dashboard data from database
     */
    private DashboardOverviewResponseDto generateDashboardData(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);

        // Get all user's tasks
        List<Task> allTasks = taskRepository.findTasksByCreatorIdOrAssigneeId(user.getId(), user.getId());

        log.info("📊 [DashboardService] Found {} total tasks for user: {}", allTasks.size(), user.getId());

        // Task Statistics
        DashboardOverviewResponseDto.TaskStatistics taskStats = generateTaskStatistics(allTasks, startOfMonth, now);

        // Task Breakdown
        DashboardOverviewResponseDto.TaskBreakdown taskBreakdown = generateTaskBreakdown(allTasks);

        // Upcoming Tasks
        DashboardOverviewResponseDto.UpcomingTasks upcomingTasks = generateUpcomingTasks(allTasks, now);

        // Completion Trends
        DashboardOverviewResponseDto.CompletionTrends completionTrends = generateCompletionTrends(allTasks, now);

        // Cache Info
        DashboardOverviewResponseDto.CacheInfo cacheInfo = DashboardOverviewResponseDto.CacheInfo.builder()
                .fromCache(false)
                .cacheGeneratedAt(now)
                .cacheKey(CACHE_PREFIX + user.getId())
                .cacheExpiresInSeconds(CACHE_TTL_MINUTES * 60)
                .build();

        return DashboardOverviewResponseDto.builder()
                .taskStats(taskStats)
                .taskBreakdown(taskBreakdown)
                .upcomingTasks(upcomingTasks)
                .completionTrends(completionTrends)
                .cacheInfo(cacheInfo)
                .build();
    }

    private DashboardOverviewResponseDto.TaskStatistics generateTaskStatistics(
            List<Task> allTasks, LocalDateTime startOfMonth, LocalDateTime now) {

        long totalTasks = allTasks.size();
        long completedTasks = allTasks.stream().mapToLong(task ->
                "COMPLETED".equalsIgnoreCase(task.getStatusKey()) ? 1 : 0).sum();
        long pendingTasks = totalTasks - completedTasks;

        // Overdue tasks (deadline passed and not completed)
        // Convert LocalDateTime to LocalDate for comparison
        LocalDate today = now.toLocalDate();
        long overdueTasks = allTasks.stream().mapToLong(task -> {
            if (task.getDeadline() != null &&
                task.getDeadline().isBefore(today) &&
                !"COMPLETED".equalsIgnoreCase(task.getStatusKey())) {
                return 1;
            }
            return 0;
        }).sum();

        // Tasks this month
        long tasksThisMonth = allTasks.stream().mapToLong(task ->
                task.getCreatedAt().isAfter(startOfMonth) ? 1 : 0).sum();

        // Tasks completed this month
        long tasksCompletedThisMonth = allTasks.stream().mapToLong(task ->
                "COMPLETED".equalsIgnoreCase(task.getStatusKey()) &&
                task.getUpdatedAt().isAfter(startOfMonth) ? 1 : 0).sum();

        double completionRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0.0;

        return DashboardOverviewResponseDto.TaskStatistics.builder()
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .pendingTasks(pendingTasks)
                .overdueTasks(overdueTasks)
                .completionRate(Math.round(completionRate * 100.0) / 100.0)
                .tasksThisMonth(tasksThisMonth)
                .tasksCompletedThisMonth(tasksCompletedThisMonth)
                .build();
    }

    private DashboardOverviewResponseDto.TaskBreakdown generateTaskBreakdown(List<Task> allTasks) {
        // By Project
        List<DashboardOverviewResponseDto.CategoryStats> byProject = allTasks.stream()
                .filter(task -> task.getProject() != null)
                .collect(Collectors.groupingBy(task -> task.getProject()))
                .entrySet().stream()
                .map(entry -> {
                    List<Task> projectTasks = entry.getValue();
                    long completed = projectTasks.stream().mapToLong(task ->
                            "COMPLETED".equalsIgnoreCase(task.getStatusKey()) ? 1 : 0).sum();
                    return DashboardOverviewResponseDto.CategoryStats.builder()
                            .id(entry.getKey().getId())
                            .name(entry.getKey().getName())
                            .count((long) projectTasks.size())
                            .completed(completed)
                            .pending(projectTasks.size() - completed)
                            .completionRate(projectTasks.size() > 0 ?
                                    Math.round((double) completed / projectTasks.size() * 10000.0) / 100.0 : 0.0)
                            .build();
                })
                .collect(Collectors.toList());

        // By Team
        List<DashboardOverviewResponseDto.CategoryStats> byTeam = allTasks.stream()
                .filter(task -> task.getTeam() != null)
                .collect(Collectors.groupingBy(task -> task.getTeam()))
                .entrySet().stream()
                .map(entry -> {
                    List<Task> teamTasks = entry.getValue();
                    long completed = teamTasks.stream().mapToLong(task ->
                            "COMPLETED".equalsIgnoreCase(task.getStatusKey()) ? 1 : 0).sum();
                    return DashboardOverviewResponseDto.CategoryStats.builder()
                            .id(entry.getKey().getId())
                            .name(entry.getKey().getName())
                            .count((long) teamTasks.size())
                            .completed(completed)
                            .pending(teamTasks.size() - completed)
                            .completionRate(teamTasks.size() > 0 ?
                                    Math.round((double) completed / teamTasks.size() * 10000.0) / 100.0 : 0.0)
                            .build();
                })
                .collect(Collectors.toList());

        // By Status
        List<DashboardOverviewResponseDto.CategoryStats> byStatus = allTasks.stream()
                .filter(task -> task.getStatusKey() != null)  // Filter out tasks with null statusKey
                .collect(Collectors.groupingBy(Task::getStatusKey))
                .entrySet().stream()
                .map(entry -> DashboardOverviewResponseDto.CategoryStats.builder()
                        .name(entry.getKey())
                        .count((long) entry.getValue().size())
                        .build())
                .collect(Collectors.toList());

        // Add unknown status category if any tasks have null statusKey
        long nullStatusCount = allTasks.stream().filter(task -> task.getStatusKey() == null).count();
        if (nullStatusCount > 0) {
            byStatus.add(DashboardOverviewResponseDto.CategoryStats.builder()
                    .name("UNKNOWN")
                    .count(nullStatusCount)
                    .build());
        }

        // By Priority
        List<DashboardOverviewResponseDto.CategoryStats> byPriority = allTasks.stream()
                .filter(task -> task.getPriorityKey() != null)  // Filter out tasks with null priorityKey
                .collect(Collectors.groupingBy(Task::getPriorityKey))
                .entrySet().stream()
                .map(entry -> DashboardOverviewResponseDto.CategoryStats.builder()
                        .name(entry.getKey())
                        .count((long) entry.getValue().size())
                        .build())
                .collect(Collectors.toList());

        // Add unknown priority category if any tasks have null priorityKey
        long nullPriorityCount = allTasks.stream().filter(task -> task.getPriorityKey() == null).count();
        if (nullPriorityCount > 0) {
            byPriority.add(DashboardOverviewResponseDto.CategoryStats.builder()
                    .name("UNKNOWN")
                    .count(nullPriorityCount)
                    .build());
        }

        return DashboardOverviewResponseDto.TaskBreakdown.builder()
                .byProject(byProject)
                .byTeam(byTeam)
                .byStatus(byStatus)
                .byPriority(byPriority)
                .build();
    }

    private DashboardOverviewResponseDto.UpcomingTasks generateUpcomingTasks(List<Task> allTasks, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate nextWeekDate = today.plusWeeks(1);
        LocalDate nextMonthDate = today.plusMonths(1);

        // Count upcoming tasks
        long nextWeekCount = allTasks.stream().mapToLong(task -> {
            if (task.getDeadline() != null &&
                task.getDeadline().isAfter(today) &&
                task.getDeadline().isBefore(nextWeekDate) &&
                !"COMPLETED".equalsIgnoreCase(task.getStatusKey())) {
                return 1;
            }
            return 0;
        }).sum();

        long nextMonthCount = allTasks.stream().mapToLong(task -> {
            if (task.getDeadline() != null &&
                task.getDeadline().isAfter(today) &&
                task.getDeadline().isBefore(nextMonthDate) &&
                !"COMPLETED".equalsIgnoreCase(task.getStatusKey())) {
                return 1;
            }
            return 0;
        }).sum();

        // Get specific task lists
        List<DashboardOverviewResponseDto.TaskSummary> urgentTasks = allTasks.stream()
                .filter(task -> "HIGH".equalsIgnoreCase(task.getPriorityKey()) &&
                               !"COMPLETED".equalsIgnoreCase(task.getStatusKey()) &&
                               task.getDeadline() != null &&
                               task.getDeadline().isAfter(today) &&
                               task.getDeadline().isBefore(nextWeekDate))
                .limit(5)
                .map(this::convertToTaskSummary)
                .collect(Collectors.toList());

        List<DashboardOverviewResponseDto.TaskSummary> dueTodayTasks = allTasks.stream()
                .filter(task -> task.getDeadline() != null &&
                               task.getDeadline().isEqual(today) &&
                               !"COMPLETED".equalsIgnoreCase(task.getStatusKey()))
                .limit(5)
                .map(this::convertToTaskSummary)
                .collect(Collectors.toList());

        List<DashboardOverviewResponseDto.TaskSummary> overdueTasks = allTasks.stream()
                .filter(task -> task.getDeadline() != null &&
                               task.getDeadline().isBefore(today) &&
                               !"COMPLETED".equalsIgnoreCase(task.getStatusKey()))
                .limit(5)
                .map(this::convertToTaskSummary)
                .collect(Collectors.toList());

        return DashboardOverviewResponseDto.UpcomingTasks.builder()
                .nextWeek(nextWeekCount)
                .nextMonth(nextMonthCount)
                .urgentTasks(urgentTasks)
                .dueTodayTasks(dueTodayTasks)
                .overdueTasks(overdueTasks)
                .build();
    }

    private DashboardOverviewResponseDto.CompletionTrends generateCompletionTrends(List<Task> allTasks, LocalDateTime now) {
        // Monthly trends (last 6 months)
        List<DashboardOverviewResponseDto.MonthlyTrend> monthlyTrends = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthStart = now.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1);

            long created = allTasks.stream().mapToLong(task ->
                    task.getCreatedAt().isAfter(monthStart) && task.getCreatedAt().isBefore(monthEnd) ? 1 : 0).sum();
            long completed = allTasks.stream().mapToLong(task ->
                    "COMPLETED".equalsIgnoreCase(task.getStatusKey()) &&
                    task.getUpdatedAt().isAfter(monthStart) && task.getUpdatedAt().isBefore(monthEnd) ? 1 : 0).sum();

            monthlyTrends.add(DashboardOverviewResponseDto.MonthlyTrend.builder()
                    .month(monthStart.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                    .monthName(monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
                    .created(created)
                    .completed(completed)
                    .completionRate(created > 0 ? Math.round((double) completed / created * 10000.0) / 100.0 : 0.0)
                    .build());
        }

        // Task counts by status and priority
        Map<String, Long> tasksByStatus = allTasks.stream()
                .filter(task -> task.getStatusKey() != null)  // Filter out tasks with null statusKey
                .collect(Collectors.groupingBy(Task::getStatusKey, Collectors.counting()));

        // Add unknown status count if any tasks have null statusKey
        long nullStatusCount = allTasks.stream().filter(task -> task.getStatusKey() == null).count();
        if (nullStatusCount > 0) {
            tasksByStatus.put("UNKNOWN", nullStatusCount);
        }

        Map<String, Long> tasksByPriority = allTasks.stream()
                .filter(task -> task.getPriorityKey() != null)  // Filter out tasks with null priorityKey
                .collect(Collectors.groupingBy(Task::getPriorityKey, Collectors.counting()));

        // Add unknown priority count if any tasks have null priorityKey
        long nullPriorityCount = allTasks.stream().filter(task -> task.getPriorityKey() == null).count();
        if (nullPriorityCount > 0) {
            tasksByPriority.put("UNKNOWN", nullPriorityCount);
        }

        return DashboardOverviewResponseDto.CompletionTrends.builder()
                .monthlyTrends(monthlyTrends)
                .weeklyTrends(new ArrayList<>()) // TODO: Implement weekly trends if needed
                .tasksByStatus(tasksByStatus)
                .tasksByPriority(tasksByPriority)
                .build();
    }

    private DashboardOverviewResponseDto.TaskSummary convertToTaskSummary(Task task) {
        LocalDate today = LocalDate.now();
        Long daysOverdue = null;
        if (task.getDeadline() != null && task.getDeadline().isBefore(today)) {
            daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(task.getDeadline(), today);
        }

        return DashboardOverviewResponseDto.TaskSummary.builder()
                .id(task.getId())
                .title(task.getTitle())
                .status(task.getStatusKey())
                .priority(task.getPriorityKey())
                .deadline(task.getDeadline() != null ? task.getDeadline().atStartOfDay() : null) // Handle null deadline
                .projectName(task.getProject() != null ? task.getProject().getName() : null)
                .teamName(task.getTeam() != null ? task.getTeam().getName() : null)
                .daysOverdue(daysOverdue)
                .build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }
}
