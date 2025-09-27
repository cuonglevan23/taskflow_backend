package com.example.taskmanagement_backend.dtos.PaymentDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionResponseDto {

    private String sessionId;
    private String sessionUrl;
    private Long subscriptionId;
    private String status;
    private String message;
    private boolean success;

    // Additional fields that might be useful for demo
    private String stripeSubscriptionId;
    private String customerId;
}
