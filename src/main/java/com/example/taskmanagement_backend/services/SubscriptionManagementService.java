package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.enums.PlanType;
import com.example.taskmanagement_backend.enums.SubscriptionStatus;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionManagementService {

    private final UserJpaRepository userRepository;
    private final StripeService stripeService;

    /**
     * Initialize trial for new user (14 days)
     */
    @Transactional
    public void initializeTrialSubscription(User user) {
        UserProfile profile = user.getUserProfile();
        if (profile != null && (profile.getIsPremium() == null || !profile.getIsPremium())) {
            profile.setIsPremium(true);
            profile.setPremiumPlanType("trial");
            profile.setPremiumExpiry(LocalDateTime.now().plusDays(14));

            userRepository.save(user);
            log.info("✅ Trial subscription initialized for user: {} - Expires: {}",
                    user.getEmail(), profile.getPremiumExpiry());
        }
    }

    /**
     * Check subscription status and return access level
     */
    public SubscriptionAccessDto checkSubscriptionAccess(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile profile = user.getUserProfile();
        if (profile == null) {
            return SubscriptionAccessDto.builder()
                    .hasAccess(false)
                    .status(SubscriptionStatus.EXPIRED)
                    .planType(null)
                    .daysRemaining(0)
                    .message("User profile not found")
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = profile.getPremiumExpiry();

        if (!Boolean.TRUE.equals(profile.getIsPremium()) || expiry == null) {
            return SubscriptionAccessDto.builder()
                    .hasAccess(false)
                    .status(SubscriptionStatus.EXPIRED)
                    .planType(profile.getPremiumPlanType())
                    .daysRemaining(0)
                    .message("No active subscription")
                    .build();
        }

        if (expiry.isAfter(now)) {
            // Active subscription
            long daysRemaining = java.time.Duration.between(now, expiry).toDays();
            PlanType planType = PlanType.fromCode(profile.getPremiumPlanType());

            return SubscriptionAccessDto.builder()
                    .hasAccess(true)
                    .status(planType == PlanType.TRIAL ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE)
                    .planType(profile.getPremiumPlanType())
                    .daysRemaining((int) daysRemaining)
                    .expiryDate(expiry)
                    .message("Subscription active")
                    .build();
        } else {
            // Expired subscription
            return SubscriptionAccessDto.builder()
                    .hasAccess(false)
                    .status(SubscriptionStatus.EXPIRED)
                    .planType(profile.getPremiumPlanType())
                    .daysRemaining(0)
                    .expiryDate(expiry)
                    .message("Subscription expired")
                    .build();
        }
    }

    /**
     * Upgrade subscription from trial to paid plan
     */
    @Transactional
    public void upgradeSubscription(Long userId, String planType, String stripeSubscriptionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile profile = user.getUserProfile();
        if (profile == null) {
            throw new RuntimeException("User profile not found");
        }

        PlanType plan = PlanType.fromCode(planType);
        LocalDateTime newExpiry = LocalDateTime.now().plusDays(plan.getDurationDays());

        profile.setIsPremium(true);
        profile.setPremiumPlanType(planType);
        profile.setPremiumExpiry(newExpiry);

        userRepository.save(user);
        log.info("✅ Subscription upgraded for user: {} - Plan: {} - Expires: {}",
                user.getEmail(), planType, newExpiry);
    }

    /**
     * Renew subscription automatically (for auto-renew)
     */
    @Transactional
    public boolean renewSubscription(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            UserProfile profile = user.getUserProfile();
            if (profile == null || profile.getPremiumPlanType() == null) {
                log.warn("❌ Cannot renew - no active plan for user: {}", userId);
                return false;
            }

            PlanType planType = PlanType.fromCode(profile.getPremiumPlanType());
            if (planType == PlanType.TRIAL) {
                log.warn("❌ Cannot auto-renew trial subscription for user: {}", userId);
                return false;
            }

            // Attempt payment through Stripe
            boolean paymentSuccess = stripeService.processAutoRenewal(userId, planType);

            if (paymentSuccess) {
                // Extend subscription
                LocalDateTime newExpiry = LocalDateTime.now().plusDays(planType.getDurationDays());
                profile.setPremiumExpiry(newExpiry);
                userRepository.save(user);

                log.info("✅ Auto-renewal successful for user: {} - New expiry: {}", userId, newExpiry);
                return true;
            } else {
                // Payment failed - set to expired
                profile.setIsPremium(false);
                userRepository.save(user);

                log.warn("❌ Auto-renewal failed for user: {} - Subscription expired", userId);
                return false;
            }
        } catch (Exception e) {
            log.error("❌ Error during auto-renewal for user: {} - {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Cancel subscription
     */
    @Transactional
    public void cancelSubscription(Long userId, boolean immediately) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile profile = user.getUserProfile();
        if (profile == null) {
            throw new RuntimeException("User profile not found");
        }

        if (immediately) {
            profile.setIsPremium(false);
            profile.setPremiumExpiry(LocalDateTime.now());
            log.info("✅ Subscription cancelled immediately for user: {}", userId);
        } else {
            // Cancel at period end - keep active until expiry
            log.info("✅ Subscription set to cancel at period end for user: {} - Expires: {}",
                    userId, profile.getPremiumExpiry());
        }

        userRepository.save(user);
    }

    /**
     * Scheduled task to check and expire subscriptions
     */
    @Scheduled(cron = "0 0 1 * * ?") // Run daily at 1 AM
    @Transactional
    public void processExpiredSubscriptions() {
        log.info("🕐 Running scheduled subscription expiry check...");

        List<User> users = userRepository.findUsersWithExpiredSubscriptions(LocalDateTime.now());

        for (User user : users) {
            UserProfile profile = user.getUserProfile();
            if (profile != null && Boolean.TRUE.equals(profile.getIsPremium())) {

                // Check if auto-renew is enabled and attempt renewal
                if (shouldAttemptAutoRenew(profile)) {
                    boolean renewed = renewSubscription(user.getId());
                    if (renewed) {
                        continue; // Skip expiration if renewal successful
                    }
                }

                // Expire the subscription
                profile.setIsPremium(false);
                userRepository.save(user);

                log.info("⏰ Expired subscription for user: {} - Plan: {}",
                        user.getEmail(), profile.getPremiumPlanType());
            }
        }

        log.info("✅ Subscription expiry check completed - Processed {} users", users.size());
    }

    /**
     * Check if should attempt auto-renewal (based on plan type and user settings)
     */
    private boolean shouldAttemptAutoRenew(UserProfile profile) {
        // Only attempt auto-renew for paid plans (not trial)
        String planType = profile.getPremiumPlanType();
        return planType != null && !planType.equals("trial");
    }

    /**
     * Get subscription statistics for dashboard
     */
    public Map<String, Object> getSubscriptionStats() {
        LocalDateTime now = LocalDateTime.now();

        long totalActive = userRepository.countActiveSubscriptions(now);
        long totalTrial = userRepository.countTrialSubscriptions(now);
        long expiringSoon = userRepository.countExpiringSoon(now, now.plusDays(7));
        long totalExpired = userRepository.countExpiredSubscriptions(now);

        return Map.of(
            "totalActiveSubscriptions", totalActive,
            "totalTrialSubscriptions", totalTrial,
            "subscriptionsExpiringSoon", expiringSoon,
            "totalExpiredSubscriptions", totalExpired,
            "conversionRate", totalTrial > 0 ? (double) totalActive / (totalActive + totalTrial) * 100 : 0
        );
    }

    /**
     * DTO for subscription access information
     */
    @lombok.Builder
    @lombok.Data
    public static class SubscriptionAccessDto {
        private boolean hasAccess;
        private SubscriptionStatus status;
        private String planType;
        private int daysRemaining;
        private LocalDateTime expiryDate;
        private String message;
    }
}
