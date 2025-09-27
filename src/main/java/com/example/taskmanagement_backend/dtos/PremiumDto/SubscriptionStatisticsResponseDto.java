package com.example.taskmanagement_backend.dtos.PremiumDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 📊 Subscription Statistics Response DTO - Admin statistics for subscription data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatisticsResponseDto {

    private Boolean success;
    private StatisticsDto statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticsDto {
        // Basic subscription stats
        private Long totalActiveSubscriptions;
        private Long totalUsers;
        private Double monthlyRevenue;
        private Double yearlyRevenue;

        // Trial-specific statistics
        private TrialStatisticsDto trialStatistics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrialStatisticsDto {
        private Long activeTrials;
        private Long expiredTrials;
        private Long trialsExpiringSoon; // <= 3 days
        private Double trialConversionRate; // Percentage of trials that convert to paid
    }
}
