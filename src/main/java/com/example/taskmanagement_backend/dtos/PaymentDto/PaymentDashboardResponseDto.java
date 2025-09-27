package com.example.taskmanagement_backend.dtos.PaymentDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDashboardResponseDto {

    // Quick overview metrics
    private DashboardOverview overview;

    // Real-time metrics
    private RealtimeMetrics realtime;

    // Key performance indicators
    private List<KpiMetric> kpis;

    // Chart data for dashboard
    private ChartData chartData;

    // Alerts and notifications
    private List<DashboardAlert> alerts;

    // Recent activities
    private List<RecentActivity> recentActivities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardOverview {
        private BigDecimal totalRevenue;
        private BigDecimal monthlyRevenue;
        private int totalSubscriptions;
        private int activeUsers;
        private double conversionRate;
        private BigDecimal averageOrderValue;
        private LocalDateTime lastUpdated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealtimeMetrics {
        private int onlineUsers;
        private int todaySignups;
        private BigDecimal todayRevenue;
        private int todayTransactions;
        private int activeTrials;
        private String systemStatus; // "HEALTHY", "WARNING", "ERROR"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KpiMetric {
        private String name;
        private String value;
        private String unit;
        private double changePercentage;
        private String changeDirection; // "UP", "DOWN", "STABLE"
        private String status; // "GOOD", "WARNING", "CRITICAL"
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartData {
        private List<TimeSeriesData> revenueChart;
        private List<PieChartData> planDistribution;
        private List<TimeSeriesData> subscriptionGrowth;
        private List<BarChartData> monthlyComparison;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesData {
        private String date;
        private BigDecimal value;
        private String label;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PieChartData {
        private String label;
        private BigDecimal value;
        private double percentage;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BarChartData {
        private String category;
        private BigDecimal current;
        private BigDecimal previous;
        private double growth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardAlert {
        private String id;
        private String type; // "SUCCESS", "WARNING", "ERROR", "INFO"
        private String title;
        private String message;
        private LocalDateTime timestamp;
        private boolean isRead;
        private String actionUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivity {
        private String id;
        private String type; // "SUBSCRIPTION", "PAYMENT", "CANCELLATION", "UPGRADE"
        private String description;
        private String userEmail;
        private BigDecimal amount;
        private LocalDateTime timestamp;
        private String status;
    }
}
