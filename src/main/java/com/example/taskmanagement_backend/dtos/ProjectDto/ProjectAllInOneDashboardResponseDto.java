package com.example.taskmanagement_backend.dtos.ProjectDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAllInOneDashboardResponseDto {

    // ===== 1️⃣ Project Overview =====
    private ProjectOverview projectInfo;

    // ===== 2️⃣ Statistics Overview =====
    private ProjectStats stats;

    // ===== 3️⃣ Task Breakdown Analysis =====
    private TaskBreakdown taskBreakdown;

    // ===== 4️⃣ Upcoming Tasks & Deadlines =====
    private UpcomingTasks upcomingTasks;

    // ===== 5️⃣ Progress Trends =====
    private ProgressTrends progressTrends;

    // ===== 6️⃣ Team Information =====
    private TeamInfo teamInfo;

    // ===== Basic Project Information =====
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectOverview {
        private Long id;
        private String name;
        private String description;
        private String status;
        private LocalDate startDate;
        private LocalDate endDate;
        private boolean isPersonal;
        private Long teamId;
        private String currentUserRole;
        private boolean isCurrentUserMember;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ===== 1️⃣ Main Statistics =====
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectStats {
        private Integer totalTasks;
        private Integer completedTasks;
        private Integer pendingTasks;
        private Integer inProgressTasks;
        private Integer overdueTasks;
        private Double completionRate;
        private Integer teamMembers;
        private Integer daysRemaining;
        private Double projectProgress;
        private Integer tasksThisWeek;
        private Integer tasksCompletedThisWeek;
    }

    // ===== 2️⃣ Task Breakdown Analysis =====
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskBreakdown {
        private List<StatusBreakdown> byStatus;
        private List<PriorityBreakdown> byPriority;
        private List<AssigneeBreakdown> byAssignee;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusBreakdown {
        private String name;
        private Integer count;
        private Double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriorityBreakdown {
        private String name;
        private Integer count;
        private Double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssigneeBreakdown {
        private Long userId;
        private String name;
        private String email;
        private Integer count;
        private Integer completedTasks;
        private Double completionRate;
    }

    // ===== 3️⃣ Upcoming Tasks & Deadlines =====
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpcomingTasks {
        private List<TaskSummary> urgentTasks;        // Due within 3 days
        private List<TaskSummary> dueTodayTasks;      // Due today
        private List<TaskSummary> overdueTasks;       // Past due date
        private List<TaskSummary> thisWeekTasks;      // Due this week
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
        private LocalDate deadline;
        private String assigneeName;
        private String assigneeEmail;
        private Long assigneeId;
        private Integer daysOverdue;
        private boolean isOverdue;
        private boolean isUrgent;
    }

    // ===== 4️⃣ Progress Trends =====
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressTrends {
        private List<WeeklyProgress> weeklyProgress;
        private List<DailyProgress> dailyProgress;      // Last 7 days
        private ProgressVelocity velocity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyProgress {
        private String week;                  // "Week 1", "Week 2"
        private LocalDate weekStart;
        private LocalDate weekEnd;
        private Integer planned;
        private Integer completed;
        private Double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyProgress {
        private LocalDate date;
        private Integer tasksCompleted;
        private Integer tasksCreated;
        private Integer tasksInProgress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressVelocity {
        private Double averageTasksPerWeek;
        private Double averageTasksPerDay;
        private Integer estimatedDaysToCompletion;
        private LocalDate estimatedCompletionDate;
    }

    // ===== 5️⃣ Team Information =====
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamInfo {
        private Long teamId;
        private String teamName;
        private List<TeamMember> members;
        private Integer totalMembers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMember {
        private Long userId;
        private String name;
        private String email;
        private String role;
        private Integer assignedTasks;
        private Integer completedTasks;
        private Double workload;              // Percentage of total project tasks
    }
}
