package com.example.taskmanagement_backend.enums;

/**
 * Subscription status enum for managing subscription lifecycle
 */
public enum SubscriptionStatus {
    TRIAL,          // 14-day trial period
    ACTIVE,         // Active paid subscription
    EXPIRED,        // Subscription expired
    CANCELLED,      // User cancelled subscription
    PAYMENT_FAILED, // Payment failed, grace period
    SUSPENDED       // Account suspended
}
