package com.example.taskmanagement_backend.dtos.PremiumDto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 🏆 Premium Status Response DTO - Structured response for FE type definitions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremiumStatusResponseDto {

    // Basic Premium Status
    private Boolean isPremium;
    private String subscriptionStatus;
    private String planType;
    private Integer daysRemaining;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiryDate;

    private String premiumBadgeUrl;
    private Boolean isExpired;
    private String message;

    // Trial Information
    private TrialInfoDto trial;

    // Available Plans
    private AvailablePlansDto availablePlans;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrialInfoDto {
        private String status; // ACTIVE, EXPIRING_SOON, EXPIRED, NOT_STARTED
        private Boolean hasAccess;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime startDate;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime endDate;

        private Integer daysRemaining;
        private Integer hoursRemaining;
        private Integer daysOverdue;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailablePlansDto {
        private PlanDetailsDto monthly;
        private PlanDetailsDto quarterly;
        private PlanDetailsDto yearly;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanDetailsDto {
        private Double price;
        private String duration;
        private String displayName;
    }
}
