package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.PremiumDto.*;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.PlanType;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.services.SubscriptionManagementService;
import com.example.taskmanagement_backend.services.TrialManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/premium")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Premium Management", description = "Premium subscription and trial management with graceful degradation")
public class PremiumController {

    private final UserJpaRepository userRepository;
    private final SubscriptionManagementService subscriptionService;
    private final TrialManagementService trialManagementService;

    /**
     * 🏆 Get Premium Status with detailed subscription and trial info
     */
    @GetMapping("/status")
    @Operation(summary = "Get Premium Status", description = "Get current premium status, trial info and subscription details")
    public ResponseEntity<PremiumStatusResponseDto> getPremiumStatus(Authentication authentication) {
        try {
            String userEmail = authentication.getName();
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Kiểm tra trạng thái trial
            TrialManagementService.TrialInfo trialInfo = trialManagementService.checkTrialStatus(user.getId());

            // Lấy subscription access info
            SubscriptionManagementService.SubscriptionAccessDto accessInfo =
                    subscriptionService.checkSubscriptionAccess(user.getId());

            // Build trial info
            PremiumStatusResponseDto.TrialInfoDto trialDto = PremiumStatusResponseDto.TrialInfoDto.builder()
                    .status(trialInfo.getStatus().toString())
                    .hasAccess(trialInfo.isHasAccess())
                    .startDate(trialInfo.getStartDate())
                    .endDate(trialInfo.getEndDate())
                    .daysRemaining(trialInfo.getDaysRemaining())
                    .hoursRemaining(trialInfo.getHoursRemaining())
                    .daysOverdue(trialInfo.getDaysOverdue())
                    .message(trialInfo.getMessage())
                    .build();

            // Build available plans
            PremiumStatusResponseDto.AvailablePlansDto availablePlans = PremiumStatusResponseDto.AvailablePlansDto.builder()
                    .monthly(PremiumStatusResponseDto.PlanDetailsDto.builder()
                            .price(9.99)
                            .duration("30 days")
                            .displayName("Monthly Plan")
                            .build())
                    .quarterly(PremiumStatusResponseDto.PlanDetailsDto.builder()
                            .price(24.99)
                            .duration("90 days")
                            .displayName("Quarterly Plan")
                            .build())
                    .yearly(PremiumStatusResponseDto.PlanDetailsDto.builder()
                            .price(99.99)
                            .duration("365 days")
                            .displayName("Yearly Plan")
                            .build())
                    .build();

            // Build main response
            PremiumStatusResponseDto response = PremiumStatusResponseDto.builder()
                    .isPremium(accessInfo.isHasAccess())
                    .subscriptionStatus(accessInfo.getStatus().toString()) // Convert enum to string
                    .planType(accessInfo.getPlanType() != null ? accessInfo.getPlanType() : "none")
                    .daysRemaining(accessInfo.getDaysRemaining())
                    .expiryDate(accessInfo.getExpiryDate())
                    .premiumBadgeUrl(accessInfo.isHasAccess() ? "/images/premium-badge.png" : null)
                    .isExpired(!accessInfo.isHasAccess())
                    .message(accessInfo.getMessage())
                    .trial(trialDto)
                    .availablePlans(availablePlans)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error getting premium status: {}", e.getMessage());
            // Return error response with structured DTO
            PremiumStatusResponseDto errorResponse = PremiumStatusResponseDto.builder()
                    .isPremium(false)
                    .isExpired(true)
                    .message("Failed to get premium status: " + e.getMessage())
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 🎁 Start Trial Subscription (14 days) - Auto-initialize for new users
     */
    @PostMapping("/start-trial")
    @Operation(summary = "Start Trial", description = "Initialize or start 14-day trial subscription")
    public ResponseEntity<StartTrialResponseDto> startTrial(Authentication authentication) {
        try {
            String userEmail = authentication.getName();
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Khởi tạo trial 14 ngày
            TrialManagementService.TrialInfo trialInfo = trialManagementService.initializeTrial(user);

            log.info("🎁 Trial initialization result for user: {} - Status: {}",
                    userEmail, trialInfo.getStatus());

            StartTrialResponseDto response = StartTrialResponseDto.builder()
                    .success(trialInfo.isHasAccess())
                    .trialStatus(trialInfo.getStatus().toString())
                    .message(trialInfo.getMessage())
                    .isPremium(trialInfo.isHasAccess())
                    .planType("trial")
                    .startDate(trialInfo.getStartDate())
                    .endDate(trialInfo.getEndDate())
                    .daysRemaining(trialInfo.getDaysRemaining())
                    .premiumBadgeUrl(trialInfo.isHasAccess() ? "/images/premium-badge.png" : null)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error starting trial: {}", e.getMessage());
            StartTrialResponseDto errorResponse = StartTrialResponseDto.builder()
                    .success(false)
                    .message("Failed to start trial: " + e.getMessage())
                    .isPremium(false)
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 📊 Get detailed trial analytics for current user
     */
    @GetMapping("/trial/analytics")
    @Operation(summary = "Get Trial Analytics", description = "Get detailed trial usage and remaining time analytics")
    public ResponseEntity<TrialAnalyticsResponseDto> getTrialAnalytics(Authentication authentication) {
        try {
            String userEmail = authentication.getName();
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            TrialManagementService.TrialInfo trialInfo = trialManagementService.checkTrialStatus(user.getId());

            // Build analytics data
            TrialAnalyticsResponseDto.AnalyticsDto analytics = TrialAnalyticsResponseDto.AnalyticsDto.builder()
                    .totalTrialDays(14)
                    .daysUsed(trialInfo.getStartDate() != null ?
                            java.time.temporal.ChronoUnit.DAYS.between(trialInfo.getStartDate(), LocalDateTime.now()) : 0L)
                    .daysRemaining(trialInfo.getDaysRemaining())
                    .hoursRemaining(trialInfo.getHoursRemaining())
                    .progressPercentage(trialInfo.getStartDate() != null ?
                            Math.min(100.0, (double) java.time.temporal.ChronoUnit.DAYS.between(trialInfo.getStartDate(), LocalDateTime.now()) / 14 * 100) : 0.0)
                    .timeRemainingText(getTimeRemainingText(trialInfo))
                    .urgencyLevel(getUrgencyLevel(trialInfo.getDaysRemaining()))
                    .shouldShowUpgradePrompt(trialInfo.getDaysRemaining() <= 3)
                    .build();

            // Build date info
            TrialAnalyticsResponseDto.DateInfoDto dateInfo = TrialAnalyticsResponseDto.DateInfoDto.builder()
                    .startDate(trialInfo.getStartDate())
                    .endDate(trialInfo.getEndDate())
                    .currentDate(LocalDateTime.now())
                    .build();

            // Build main response
            TrialAnalyticsResponseDto response = TrialAnalyticsResponseDto.builder()
                    .trialStatus(trialInfo.getStatus().toString())
                    .hasAccess(trialInfo.isHasAccess())
                    .analytics(analytics)
                    .dates(dateInfo)
                    .message(trialInfo.getMessage())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error getting trial analytics: {}", e.getMessage());
            TrialAnalyticsResponseDto errorResponse = TrialAnalyticsResponseDto.builder()
                    .hasAccess(false)
                    .message("Failed to get trial analytics: " + e.getMessage())
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 🏆 Cancel Subscription with trial awareness
     */
    @PostMapping("/cancel")
    @Operation(summary = "Cancel Subscription", description = "Cancel current subscription or trial")
    public ResponseEntity<CancelSubscriptionResponseDto> cancelSubscription(
            @RequestParam(defaultValue = "false") boolean immediately,
            Authentication authentication) {

        try {
            String userEmail = authentication.getName();
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Kiểm tra loại subscription hiện tại
            TrialManagementService.TrialInfo trialInfo = trialManagementService.checkTrialStatus(user.getId());

            if (trialInfo.getStatus() == TrialManagementService.TrialStatus.ACTIVE ||
                trialInfo.getStatus() == TrialManagementService.TrialStatus.EXPIRING_SOON) {

                // Đang trong trial - chỉ có thể cancel immediately
                subscriptionService.cancelSubscription(user.getId(), true);

                CancelSubscriptionResponseDto response = CancelSubscriptionResponseDto.builder()
                        .success(true)
                        .message("Trial đã được hủy thành công")
                        .type("trial_cancelled")
                        .immediately(true)
                        .build();

                return ResponseEntity.ok(response);
            } else {
                // Paid subscription - có thể cancel ngay hoặc cuối kỳ
                subscriptionService.cancelSubscription(user.getId(), immediately);

                log.info("✅ Subscription cancelled for user: {} - Immediate: {}", userEmail, immediately);

                CancelSubscriptionResponseDto response = CancelSubscriptionResponseDto.builder()
                        .success(true)
                        .message(immediately ? "Subscription cancelled immediately" : "Subscription will cancel at period end")
                        .type("subscription_cancelled")
                        .immediately(immediately)
                        .build();

                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("❌ Error cancelling subscription: {}", e.getMessage());
            CancelSubscriptionResponseDto errorResponse = CancelSubscriptionResponseDto.builder()
                    .success(false)
                    .message("Failed to cancel subscription: " + e.getMessage())
                    .immediately(false)
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 📊 Get Subscription Statistics (Admin only)
     */
    @GetMapping("/admin/statistics")
    @Operation(summary = "Get Subscription Stats", description = "Admin endpoint to get subscription and trial statistics")
    public ResponseEntity<SubscriptionStatisticsResponseDto> getSubscriptionStatistics(Authentication authentication) {
        try {
            // TODO: Add admin role check here

            Map<String, Object> stats = subscriptionService.getSubscriptionStats();

            // Thêm trial-specific statistics
            LocalDateTime now = LocalDateTime.now();
            long activeTrials = userRepository.countActiveTrialUsers(now);
            long expiredTrials = userRepository.findExpiredTrialUsers(now).size();
            long trialsExpiringSoon = userRepository.findTrialExpiringSoon(now, now.plusDays(3)).size();

            // Build trial statistics
            SubscriptionStatisticsResponseDto.TrialStatisticsDto trialStats =
                    SubscriptionStatisticsResponseDto.TrialStatisticsDto.builder()
                    .activeTrials(activeTrials)
                    .expiredTrials(expiredTrials)
                    .trialsExpiringSoon(trialsExpiringSoon)
                    .trialConversionRate(activeTrials > 0 ?
                            (double) ((Long) stats.get("totalActiveSubscriptions")) / (activeTrials + ((Long) stats.get("totalActiveSubscriptions"))) * 100 : 0.0)
                    .build();

            // Build main statistics
            SubscriptionStatisticsResponseDto.StatisticsDto statisticsDto =
                    SubscriptionStatisticsResponseDto.StatisticsDto.builder()
                    .totalActiveSubscriptions((Long) stats.get("totalActiveSubscriptions"))
                    .totalUsers((Long) stats.get("totalUsers"))
                    .monthlyRevenue((Double) stats.get("monthlyRevenue"))
                    .yearlyRevenue((Double) stats.get("yearlyRevenue"))
                    .trialStatistics(trialStats)
                    .build();

            SubscriptionStatisticsResponseDto response = SubscriptionStatisticsResponseDto.builder()
                    .success(true)
                    .statistics(statisticsDto)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error getting subscription statistics: {}", e.getMessage());
            SubscriptionStatisticsResponseDto errorResponse = SubscriptionStatisticsResponseDto.builder()
                    .success(false)
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    // ===== HELPER METHODS =====

    private String getTimeRemainingText(TrialManagementService.TrialInfo trialInfo) {
        if (trialInfo.getDaysRemaining() > 1) {
            return String.format("%d ngày %d giờ", trialInfo.getDaysRemaining(), trialInfo.getHoursRemaining() % 24);
        } else if (trialInfo.getDaysRemaining() == 1) {
            return String.format("%d giờ", trialInfo.getHoursRemaining());
        } else if (trialInfo.getHoursRemaining() > 0) {
            return String.format("%d giờ", trialInfo.getHoursRemaining());
        } else {
            return "Đã hết hạn";
        }
    }

    private String getUrgencyLevel(int daysRemaining) {
        if (daysRemaining <= 0) return "EXPIRED";
        if (daysRemaining == 1) return "CRITICAL";
        if (daysRemaining <= 3) return "HIGH";
        if (daysRemaining <= 7) return "MEDIUM";
        return "LOW";
    }

    /**
     * Calculate expiry date based on plan type
     */
    private LocalDateTime calculateExpiryDate(String planType) {
        try {
            PlanType plan = PlanType.fromCode(planType);
            return LocalDateTime.now().plusDays(plan.getDurationDays());
        } catch (Exception e) {
            log.warn("Unknown plan type: {}, defaulting to 30 days", planType);
            return LocalDateTime.now().plusDays(30);
        }
    }
}
