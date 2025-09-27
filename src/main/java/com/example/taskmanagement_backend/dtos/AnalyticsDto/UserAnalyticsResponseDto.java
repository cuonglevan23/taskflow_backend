package com.example.taskmanagement_backend.dtos.AnalyticsDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAnalyticsResponseDto {

    // Current statistics
    private Long totalUsers;
    private Long activeUsers;
    private Long onlineUsers;
    private Long newUsersToday;
    private Long newUsersThisWeek;
    private Long newUsersThisMonth;

    // Chart data for user registrations over time
    private List<ChartDataPoint> dailyRegistrations;
    private List<ChartDataPoint> monthlyRegistrations;
    private List<ChartDataPoint> quarterlyRegistrations;
    private List<ChartDataPoint> yearlyRegistrations;

    // Chart data for user logins over time
    private List<ChartDataPoint> dailyLogins;
    private List<ChartDataPoint> monthlyLogins;
    private List<ChartDataPoint> quarterlyLogins;
    private List<ChartDataPoint> yearlyLogins;

    // Growth rates
    private Double monthlyGrowthRate;
    private Double quarterlyGrowthRate;
    private Double yearlyGrowthRate;

    // Peak times
    private Map<String, Object> peakRegistrationTime;
    private Map<String, Object> peakLoginTime;

    // Report metadata
    private LocalDateTime generatedAt;
    private String reportPeriod;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChartDataPoint {
        private String label;  // Date label (e.g., "2025-09", "Q3 2025", "Monday")
        private Long value;    // Count of users
        private String period; // Period type: "day", "month", "quarter", "year"
        private LocalDateTime date; // Actual date for sorting
    }
}
