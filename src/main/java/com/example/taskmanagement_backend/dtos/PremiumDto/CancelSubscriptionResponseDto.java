package com.example.taskmanagement_backend.dtos.PremiumDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 🏆 Cancel Subscription Response DTO - Response when cancelling subscription/trial
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelSubscriptionResponseDto {

    private Boolean success;
    private String message;
    private String type; // "trial_cancelled" or "subscription_cancelled"
    private Boolean immediately; // Whether cancellation is immediate
}
