package com.example.taskmanagement_backend.dtos.ProjectDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDashboardResponseDto {

    // ===== Project Statistics =====
    private ProjectStatistics projectStats;

    // ===== Task Breakdown by Status =====
    private TaskBreakdown taskBreakdown;

    // ===== Upcoming Tasks (next month) =====
    private UpcomingTasks upcomingTasks;

    // ===== Completion Trends =====
    private CompletionTrends completionTrends;

    // ===== Cache Information =====
    private CacheInfo cacheInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectStatistics {
        private Long totalProjects;
        private Long activeProjects;
        private Long completedProjects;
        private Long totalTasks;
        private Long completedTasks;
        private Long pendingTasks;
        private Long overdueTasks;
        private Double completionRate;
        private Long tasksThisMonth;
        private Long tasksCompletedThisMonth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStats {
        private Long id;
        private String name;
        private Long count;
        private Long completed;
        private Long pending;
        private Double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskBreakdown {
        private List<CategoryStats> byStatus;
        private List<CategoryStats> byAssignee;
        private List<CategoryStats> byPriority;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskSummary {
        private Long id;
        private String title;
        private String status;
        private LocalDateTime deadline;
        private String assigneeName;
        private String priority;
        private Long projectId;
        private String projectName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpcomingTasks {
        private List<TaskSummary> thisWeek;
        private List<TaskSummary> nextWeek;
        private List<TaskSummary> later;
        private List<TaskSummary> overdue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompletionTrends {
        private Map<String, Long> completedTasksByDay;
        private Map<String, Long> createdTasksByDay;
        private Map<String, Double> completionRateByWeek;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheInfo {
        private boolean fromCache;
        private LocalDateTime cacheGeneratedAt;
        private String cacheKey;
        private long cacheExpiresInSeconds;
    }
}
