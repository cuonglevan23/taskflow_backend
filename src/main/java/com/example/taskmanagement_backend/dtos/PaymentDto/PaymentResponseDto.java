package com.example.taskmanagement_backend.dtos.PaymentDto;

import com.example.taskmanagement_backend.entities.Payment.PaymentStatus;
import com.example.taskmanagement_backend.entities.Payment.PaymentMethodType;
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
public class PaymentResponseDto {

    private Long id;
    private String stripePaymentIntentId;
    private String stripeChargeId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentMethodType paymentMethodType;
    private String description;
    private String receiptUrl;
    private String failureReason;
    private BigDecimal refundedAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // User info
    private Long userId;
    private String userEmail;

    // Subscription info
    private Long subscriptionId;
    private String planType;
}
