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
public class RevenueStatisticsResponseDto {

    // Time period info
    private TimePeriod period;

    // Revenue summary
    private RevenueSummary summary;

    // Plan breakdown
    private Map<String, PlanRevenue> planBreakdown;

    // Monthly revenue data
    private List<MonthlyRevenue> monthlyData;

    // Top paying users
    private List<TopPayingUser> topPayingUsers;

    // Revenue trends
    private RevenueTrends trends;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimePeriod {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private int days;
        private String periodType; // "DAILY", "WEEKLY", "MONTHLY", "YEARLY"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueSummary {
        private BigDecimal totalRevenue;
        private BigDecimal averageRevenuePerUser;
        private BigDecimal monthlyRecurringRevenue; // MRR
        private BigDecimal yearlyRecurringRevenue; // ARR
        private int totalTransactions;
        private int successfulTransactions;
        private int failedTransactions;
        private BigDecimal successRate;
        private int activeSubscriptions;
        private int newSubscriptions;
        private int canceledSubscriptions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanRevenue {
        private String planType;
        private BigDecimal revenue;
        private int subscriptionCount;
        private BigDecimal averageRevenue;
        private double marketShare; // Percentage
        private int newSubscriptions;
        private int renewalCount;
        private int cancellationCount;
        private double churnRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyRevenue {
        private String month; // "2025-09"
        private BigDecimal revenue;
        private int transactionCount;
        private int newSubscriptions;
        private int activeSubscriptions;
        private BigDecimal growthRate; // Percentage compared to previous month
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopPayingUser {
        private Long userId;
        private String userEmail;
        private String userFullName;
        private BigDecimal totalRevenue;
        private int transactionCount;
        private String currentPlan;
        private LocalDateTime firstPayment;
        private LocalDateTime lastPayment;
        private double loyaltyScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueTrends {
        private BigDecimal weekOverWeekGrowth;
        private BigDecimal monthOverMonthGrowth;
        private BigDecimal yearOverYearGrowth;
        private String trendDirection; // "UP", "DOWN", "STABLE"
        private List<String> insights; // AI-generated insights
        private BigDecimal predictedNextMonthRevenue;
    }
}
