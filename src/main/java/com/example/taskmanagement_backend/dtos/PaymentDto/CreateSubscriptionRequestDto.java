package com.example.taskmanagement_backend.dtos.PaymentDto;

import com.example.taskmanagement_backend.entities.Subscription.PlanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionRequestDto {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Plan type is required")
    private PlanType planType;

    private String successUrl;
    private String cancelUrl;
    @Builder.Default
    private boolean allowPromotionCodes = true;
    private Integer trialPeriodDays;
}
