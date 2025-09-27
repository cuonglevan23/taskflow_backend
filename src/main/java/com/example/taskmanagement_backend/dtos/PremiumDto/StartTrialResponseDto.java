package com.example.taskmanagement_backend.dtos.PremiumDto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 🎁 Start Trial Response DTO - Response when starting trial
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartTrialResponseDto {

    private Boolean success;
    private String trialStatus; // ACTIVE, ALREADY_USED, ERROR
    private String message;
    private Boolean isPremium;
    private String planType; // "trial"

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endDate;

    private Integer daysRemaining;
    private String premiumBadgeUrl;
}
