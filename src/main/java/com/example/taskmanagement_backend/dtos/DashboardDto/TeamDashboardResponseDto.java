package com.example.taskmanagement_backend.dtos.DashboardDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDashboardResponseDto {

    // ===== Team Statistics =====
    private TeamStats teamStats;

    // ===== Member Breakdown =====
    private MemberBreakdown memberBreakdown;

    // ===== Project Breakdown =====
    private ProjectBreakdown projectBreakdown;

    // ===== Upcoming Deadlines =====
    private UpcomingDeadlines upcomingDeadlines;

    // ===== Team Performance =====
    private TeamPerformance teamPerformance;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamStats {
        private Integer totalMembers;
        private Integer activeMembers;
        private Integer totalProjects;
        private Integer activeProjects;
        private Integer completedProjects;
        private Integer totalTasks;
        private Integer completedTasks;
        private Integer pendingTasks;
        private Integer overdueTasks;
        private Double teamEfficiency;
        private Double avgTasksPerMember;
        private Double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberBreakdown {
        private List<RoleBreakdown> byRole;
        private List<WorkloadBreakdown> byWorkload;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleBreakdown {
        private String name;
        private Integer count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkloadBreakdown {
        private String name;
        private Integer count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectBreakdown {
        private List<StatusBreakdown> byStatus;
        private List<ProgressBreakdown> byProgress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusBreakdown {
        private String name;
        private Integer count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressBreakdown {
        private String name;
        private Double progress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpcomingDeadlines {
        private List<DeadlineTask> thisWeek;
        private List<DeadlineTask> nextWeek;
        private List<OverdueTask> overdue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeadlineTask {
        private Long id;
        private String title;
        private String project;
        private String dueDate;
        private String assignee;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverdueTask {
        private Long id;
        private String title;
        private String project;
        private String dueDate;
        private Integer daysOverdue;
        private String assignee;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamPerformance {
        private List<MonthlyTrend> monthlyTrends;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTrend {
        private String month;
        private Integer tasksCompleted;
        private Integer tasksCreated;
    }
}
