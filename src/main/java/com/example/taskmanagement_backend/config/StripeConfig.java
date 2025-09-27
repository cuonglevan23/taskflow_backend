package com.example.taskmanagement_backend.config;

import com.stripe.Stripe;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
@Slf4j
@Getter
public class StripeConfig {

    @Value("${stripe.api.key}")
    private String secretKey;

    @Value("${stripe.publishable.key}")
    private String publishableKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Value("${stripe.products.monthly.price-id}")
    private String monthlyPriceId;

    @Value("${stripe.products.quarterly.price-id}")
    private String quarterlyPriceId;

    @Value("${stripe.products.yearly.price-id}")
    private String yearlyPriceId;

    @Value("${stripe.success.url}")
    private String successUrl;

    @Value("${stripe.cancel.url}")
    private String cancelUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        log.info("✅ Stripe initialized with API key: {}***", secretKey.substring(0, 7));
    }
}
