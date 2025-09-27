package com.example.taskmanagement_backend.dtos.PaymentDto;

import com.example.taskmanagement_backend.entities.Subscription.PlanType;
import com.example.taskmanagement_backend.entities.Subscription.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponseDto {

    private Long id;
    private String stripeSubscriptionId;
    private String stripeCustomerId;
    private PlanType planType;
    private SubscriptionStatus status;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime trialStart;
    private LocalDateTime trialEnd;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Computed fields
    private boolean isActive;
    private boolean isTrialing;
    private boolean isCanceled;
    private long daysUntilRenewal;
    private String nextBillingDate;
}
