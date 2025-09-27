package com.example.taskmanagement_backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_price_id")
    private String stripePriceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "trial_start")
    private LocalDateTime trialStart;

    @Column(name = "trial_end")
    private LocalDateTime trialEnd;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PlanType {
        MONTHLY("monthly", "Monthly Plan"),
        QUARTERLY("quarterly", "Quarterly Plan"),
        YEARLY("yearly", "Yearly Plan");

        private final String code;
        private final String displayName;

        PlanType(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum SubscriptionStatus {
        ACTIVE("active"),
        CANCELED("canceled"),
        CANCELLED("cancelled"), // Add alias for British spelling
        INCOMPLETE("incomplete"),
        INCOMPLETE_EXPIRED("incomplete_expired"),
        PAST_DUE("past_due"),
        PAUSED("paused"),
        TRIALING("trialing"),
        UNPAID("unpaid"),
        PENDING_PAYMENT("pending_payment"); // Add missing status

        private final String stripeStatus;

        SubscriptionStatus(String stripeStatus) {
            this.stripeStatus = stripeStatus;
        }

        public String getStripeStatus() {
            return stripeStatus;
        }

        public static SubscriptionStatus fromStripeStatus(String stripeStatus) {
            for (SubscriptionStatus status : values()) {
                if (status.stripeStatus.equals(stripeStatus)) {
                    return status;
                }
            }
            return INCOMPLETE;
        }
    }
}
