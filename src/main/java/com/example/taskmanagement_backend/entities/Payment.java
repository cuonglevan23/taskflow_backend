package com.example.taskmanagement_backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    @Column(name = "stripe_charge_id")
    private String stripeChargeId;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_type")
    private PaymentMethodType paymentMethodType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    private PaymentType paymentType;

    @Column(name = "description")
    private String description;

    @Column(name = "receipt_url")
    private String receiptUrl;

    @Column(name = "failure_reason")
    private String failureReason;

    @Builder.Default
    @Column(name = "refunded_amount", precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    private BigDecimal refundedAmount = BigDecimal.ZERO;

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

    // ===== ENUMS =====

    public enum PaymentStatus {
        PENDING("requires_payment_method"),
        PROCESSING("processing"),
        SUCCEEDED("succeeded"),
        COMPLETED("succeeded"), // Alias for SUCCEEDED - needed by StripeService
        FAILED("payment_failed"),
        CANCELED("canceled"),
        REFUNDED("refunded"),
        PARTIALLY_REFUNDED("partially_refunded");

        private final String stripeStatus;

        PaymentStatus(String stripeStatus) {
            this.stripeStatus = stripeStatus;
        }

        public String getStripeStatus() {
            return stripeStatus;
        }

        public static PaymentStatus fromStripeStatus(String stripeStatus) {
            for (PaymentStatus status : values()) {
                if (status.stripeStatus.equals(stripeStatus)) {
                    return status;
                }
            }
            return FAILED; // Default fallback
        }
    }

    public enum PaymentMethodType {
        CARD("card"),
        BANK_TRANSFER("bank_transfer"),
        PAYPAL("paypal"),
        APPLE_PAY("apple_pay"),
        GOOGLE_PAY("google_pay"),
        OTHER("other");

        private final String stripeType;

        PaymentMethodType(String stripeType) {
            this.stripeType = stripeType;
        }

        public String getStripeType() {
            return stripeType;
        }

        public static PaymentMethodType fromStripeType(String stripeType) {
            for (PaymentMethodType type : values()) {
                if (type.stripeType.equals(stripeType)) {
                    return type;
                }
            }
            return OTHER; // Default fallback
        }
    }

    public enum PaymentType {
        SUBSCRIPTION("subscription"),
        ONE_TIME("one_time"),
        REFUND("refund"),
        PARTIAL_REFUND("partial_refund");

        private final String type;

        PaymentType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public static PaymentType fromType(String type) {
            for (PaymentType paymentType : values()) {
                if (paymentType.type.equals(type)) {
                    return paymentType;
                }
            }
            return ONE_TIME; // Default fallback
        }
    }
}
