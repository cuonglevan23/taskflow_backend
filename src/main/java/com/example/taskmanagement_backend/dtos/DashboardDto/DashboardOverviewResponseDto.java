package com.example.taskmanagement_backend.dtos.DashboardDto;

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
public class DashboardOverviewResponseDto {

    // ===== Task Statistics =====
    private TaskStatistics taskStats;

    // ===== Task Breakdown by Category =====
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
    public static class TaskStatistics {
        private Long totalTasks;
        private Long completedTasks;
        private Long pendingTasks;
        private Long overdueTasks;
        private Double completionRate; // Percentage
        private Long tasksThisMonth;
        private Long tasksCompletedThisMonth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskBreakdown {
        private List<CategoryStats> byProject;
        private List<CategoryStats> byTeam;
        private List<CategoryStats> byStatus;
        private List<CategoryStats> byPriority;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStats {
        private String name;
        private Long id; // Project ID, Team ID, etc.
        private Long count;
        private Long completed;
        private Long pending;
        private Long overdue;
        private Double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpcomingTasks {
        private Long nextWeek;
        private Long nextMonth;
        private List<TaskSummary> urgentTasks; // High priority tasks due soon
        private List<TaskSummary> dueTodayTasks;
        private List<TaskSummary> overdueTasks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskSummary {
        private Long id;
        private String title;
        private String status;
        private String priority;
        private LocalDateTime deadline;
        private String projectName;
        private String teamName;
        private Long daysOverdue; // null if not overdue
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompletionTrends {
        private List<MonthlyTrend> monthlyTrends; // Last 6 months
        private List<WeeklyTrend> weeklyTrends; // Last 4 weeks
        private Map<String, Long> tasksByStatus;
        private Map<String, Long> tasksByPriority;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTrend {
        private String month; // "2025-09"
        private String monthName; // "September 2025"
        private Long created;
        private Long completed;
        private Double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyTrend {
        private String week; // "2025-W36"
        private String weekName; // "Week 36, 2025"
        private Long created;
        private Long completed;
        private Double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheInfo {
        private Boolean fromCache;
        private LocalDateTime cacheGeneratedAt;
        private String cacheKey;
        private Long cacheExpiresInSeconds;
    }
}
