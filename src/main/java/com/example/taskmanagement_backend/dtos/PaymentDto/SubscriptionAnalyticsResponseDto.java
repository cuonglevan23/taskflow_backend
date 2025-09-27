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
public class SubscriptionAnalyticsResponseDto {

    // Time period info
    private TimePeriod period;

    // Subscription overview
    private SubscriptionOverview overview;

    // Plan performance breakdown
    private List<PlanPerformance> planPerformance;

    // Premium user analytics
    private PremiumUserAnalytics premiumUsers;

    // Churn analysis
    private ChurnAnalysis churnAnalysis;

    // Upgrade/downgrade patterns
    private List<UpgradePattern> upgradePatterns;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimePeriod {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private int days;
        private String periodType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionOverview {
        private int totalActiveSubscriptions;
        private int totalCanceledSubscriptions;
        private int newSubscriptionsThisPeriod;
        private int renewalsThisPeriod;
        private double overallChurnRate;
        private double retentionRate;
        private BigDecimal averageLifetimeValue;
        private Map<String, Integer> subscriptionsByStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanPerformance {
        private String planType;
        private String planName;
        private BigDecimal planPrice;

        // Subscription metrics
        private int totalSubscriptions;
        private int activeSubscriptions;
        private int newSubscriptions;
        private int cancellations;
        private int renewals;

        // Performance metrics
        private double marketShare; // Percentage of total subscriptions
        private double conversionRate; // From trial/free to this plan
        private double churnRate;
        private double retentionRate;
        private BigDecimal totalRevenue;
        private BigDecimal averageRevenuePerUser;

        // Trend data
        private double growthRate;
        private String trendDirection; // "GROWING", "DECLINING", "STABLE"
        private String popularityRank; // "MOST_POPULAR", "SECOND", "LEAST_POPULAR"

        // User satisfaction
        private double satisfactionScore; // Based on usage patterns
        private List<String> topFeatures; // Most used features by this plan
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PremiumUserAnalytics {
        private int totalPremiumUsers;
        private int newPremiumUsers;
        private double premiumUserPercentage;

        // Premium user behavior
        private List<PremiumUserProfile> topPremiumUsers;
        private Map<String, Integer> premiumUsersByPlan;
        private double averagePremiumLifetime; // in months
        private BigDecimal averagePremiumSpending;

        // Geographic distribution
        private Map<String, Integer> premiumUsersByCountry;

        // Engagement metrics
        private double averageDailyActiveUsers;
        private double averageFeatureUsage;
        private List<String> mostUsedPremiumFeatures;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PremiumUserProfile {
        private Long userId;
        private String userEmail;
        private String userFullName;
        private String currentPlan;
        private BigDecimal totalSpent;
        private LocalDateTime subscriptionStartDate;
        private int subscriptionDurationMonths;
        private double engagementScore;
        private List<String> favoriteFeatures;
        private String riskLevel; // "LOW", "MEDIUM", "HIGH" (churn risk)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChurnAnalysis {
        private double overallChurnRate;
        private Map<String, Double> churnRateByPlan;
        private List<ChurnReason> topChurnReasons;
        private int usersAtRisk; // Predicted to churn
        private List<Long> highRiskUserIds;
        private double averageTimeToChurn; // in days
        private Map<String, Double> churnByUserSegment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChurnReason {
        private String reason;
        private int count;
        private double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpgradePattern {
        private String fromPlan;
        private String toPlan;
        private int upgradeCount;
        private int downgradeCount;
        private double conversionRate;
        private BigDecimal revenueImpact;
        private double averageTimeToUpgrade; // in days
        private List<String> upgradeReasons;
    }
}
