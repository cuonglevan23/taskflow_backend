package com.example.taskmanagement_backend.dtos.PremiumDto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 📊 Trial Analytics Response DTO - Detailed countdown and progress info for FE
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialAnalyticsResponseDto {

    private String trialStatus; // ACTIVE, EXPIRING_SOON, EXPIRED, NOT_STARTED
    private Boolean hasAccess;

    // Detailed Analytics
    private AnalyticsDto analytics;

    // Date Information
    private DateInfoDto dates;

    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyticsDto {
        private Integer totalTrialDays; // Always 14
        private Long daysUsed;
        private Integer daysRemaining;
        private Integer hoursRemaining;
        private Double progressPercentage; // 0-100%
        private String timeRemainingText; // "3 ngày 12 giờ" or "Đã hết hạn"
        private String urgencyLevel; // LOW, MEDIUM, HIGH, CRITICAL, EXPIRED
        private Boolean shouldShowUpgradePrompt; // true when <= 3 days
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateInfoDto {
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime startDate;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime endDate;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime currentDate;
    }
}
