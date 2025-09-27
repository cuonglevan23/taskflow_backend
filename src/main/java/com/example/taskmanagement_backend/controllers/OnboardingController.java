package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.services.OnboardingService;
import com.example.taskmanagement_backend.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Onboarding", description = "UI tour and first-time user experience management")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final UserService userService;

    /**
     * 🎯 Kiểm tra trạng thái onboarding cho user hiện tại
     */
    @GetMapping("/status")
    @Operation(summary = "Check Onboarding Status",
              description = "Check if current user needs UI tour and get onboarding progress")
    public ResponseEntity<Map<String, Object>> getOnboardingStatus(Authentication authentication) {
        try {
            String userEmail = authentication.getName();
            Long userId = userService.getUserIdByEmailDirect(userEmail);

            if (userId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "User not found"
                ));
            }

            OnboardingService.OnboardingInfo onboardingInfo = onboardingService.checkOnboardingStatus(userId);

            log.info("🎯 Onboarding status check for user: {} - Needs tour: {}, First login: {}",
                    userEmail, onboardingInfo.isNeedsUITour(), onboardingInfo.isFirstLogin());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "onboarding", Map.of(
                    "isFirstLogin", onboardingInfo.isFirstLogin(),
                    "needsUITour", onboardingInfo.isNeedsUITour(),
                    "shouldShowTour", onboardingInfo.isShouldShowTour(),
                    "currentStep", onboardingInfo.getCurrentStep(),
                    "tourType", onboardingInfo.getTourType(),
                    "progress", onboardingInfo.getOnboardingProgress(),
                    "completedSteps", onboardingInfo.getCompletedSteps()
                ),
                "recommendations", Map.of(
                    "showWelcomeModal", onboardingInfo.isFirstLogin(),
                    "showTrialBanner", onboardingInfo.getTourType() == OnboardingService.TourType.TRIAL_FEATURES,
                    "highlightPremiumFeatures", onboardingInfo.getTourType() == OnboardingService.TourType.TRIAL_FEATURES,
                    "focusOnBasics", onboardingInfo.getTourType() == OnboardingService.TourType.FULL_ONBOARDING
                )
            ));

        } catch (Exception e) {
            log.error("❌ Error checking onboarding status: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to check onboarding status: " + e.getMessage()
            ));
        }
    }

    /**
     * 🎓 Hoàn thành UI tour
     */
    @PostMapping("/complete-tour")
    @Operation(summary = "Complete UI Tour",
              description = "Mark UI tour as completed for current user")
    public ResponseEntity<Map<String, Object>> completeTour(
            @RequestParam String tourType,
            Authentication authentication) {

        try {
            String userEmail = authentication.getName();
            Long userId = userService.getUserIdByEmailDirect(userEmail);

            if (userId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "User not found"
                ));
            }

            OnboardingService.OnboardingCompletionResult result =
                    onboardingService.completeTour(userId, tourType);

            log.info("🎓 UI tour completed for user: {} - Type: {}", userEmail, tourType);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "UI tour completed successfully",
                "result", Map.of(
                    "tourCompleted", result.isTourCompleted(),
                    "tourType", result.getTourType(),
                    "wasFirstLogin", result.isWasFirstLogin(),
                    "completedAt", result.getCompletedAt(),
                    "nextSteps", result.getNextSteps()
                )
            ));

        } catch (Exception e) {
            log.error("❌ Error completing tour: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to complete tour: " + e.getMessage()
            ));
        }
    }

    /**
     * ⏭️ Bỏ qua UI tour
     */
    @PostMapping("/skip-tour")
    @Operation(summary = "Skip UI Tour",
              description = "Skip UI tour for current user")
    public ResponseEntity<Map<String, Object>> skipTour(Authentication authentication) {
        try {
            String userEmail = authentication.getName();
            Long userId = userService.getUserIdByEmailDirect(userEmail);

            if (userId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "User not found"
                ));
            }

            OnboardingService.OnboardingCompletionResult result = onboardingService.skipTour(userId);

            log.info("⏭️ UI tour skipped for user: {}", userEmail);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "UI tour skipped",
                "result", Map.of(
                    "tourSkipped", true,
                    "wasFirstLogin", result.isWasFirstLogin(),
                    "skippedAt", result.getCompletedAt(),
                    "nextSteps", result.getNextSteps()
                )
            ));

        } catch (Exception e) {
            log.error("❌ Error skipping tour: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to skip tour: " + e.getMessage()
            ));
        }
    }

    /**
     * 🔄 Reset onboarding status (Admin hoặc testing)
     */
    @PostMapping("/reset/{userId}")
    @Operation(summary = "Reset Onboarding",
              description = "Reset onboarding status for testing purposes")
    public ResponseEntity<Map<String, Object>> resetOnboarding(
            @PathVariable Long userId,
            Authentication authentication) {

        try {
            // TODO: Add admin role check

            onboardingService.resetOnboardingStatus(userId);

            log.info("🔄 Onboarding status reset for user: {}", userId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Onboarding status reset successfully",
                "userId", userId,
                "resetAt", java.time.LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("❌ Error resetting onboarding: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to reset onboarding: " + e.getMessage()
            ));
        }
    }

    /**
     * 📊 Thống kê onboarding (Admin)
     */
    @GetMapping("/admin/statistics")
    @Operation(summary = "Get Onboarding Stats",
              description = "Admin endpoint to get onboarding completion statistics")
    public ResponseEntity<Map<String, Object>> getOnboardingStatistics(Authentication authentication) {
        try {
            // TODO: Add admin role check

            // Get basic statistics from user repository
            long totalUsers = userService.getTotalUsersCount();
            long newUsers = userService.getNewUsersCountThisMonth();
            long firstLoginUsers = userService.getFirstLoginUsersCount();

            double completionRate = totalUsers > 0 ?
                    (double) (totalUsers - firstLoginUsers) / totalUsers * 100 : 0;

            return ResponseEntity.ok(Map.of(
                "success", true,
                "statistics", Map.of(
                    "totalUsers", totalUsers,
                    "newUsersThisMonth", newUsers,
                    "usersNeedingOnboarding", firstLoginUsers,
                    "onboardingCompletionRate", completionRate,
                    "averageTimeToComplete", "4.5 minutes", // Demo value
                    "mostSkippedStep", "Profile completion", // Demo value
                    "tourTypes", Map.of(
                        "fullOnboarding", 65,
                        "trialFeatures", 25,
                        "basicDashboard", 10
                    )
                )
            ));

        } catch (Exception e) {
            log.error("❌ Error getting onboarding statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to get statistics: " + e.getMessage()
            ));
        }
    }
}
