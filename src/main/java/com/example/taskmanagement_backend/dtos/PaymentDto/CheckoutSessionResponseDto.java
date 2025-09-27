package com.example.taskmanagement_backend.dtos.PaymentDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionResponseDto {

    private String sessionId;
    private String checkoutUrl;
    private String clientSecret;
    private String customerId;
    private String subscriptionId;
    private boolean success;
    private String message;
}
