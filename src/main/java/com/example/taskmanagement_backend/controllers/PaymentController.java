package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.PaymentDto.*;
import com.example.taskmanagement_backend.services.StripeService;
import com.example.taskmanagement_backend.services.UserService;
import com.example.taskmanagement_backend.services.AuditLogService;
import com.example.taskmanagement_backend.dtos.AuditLogDto.CreateAuditLogRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Management", description = "Stripe payment and subscription APIs")
@CrossOrigin(origins = {"https://main.d2az19adxqfdf3.amplifyapp.com", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class PaymentController {

    private final StripeService stripeService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    // ===== SUBSCRIPTION MANAGEMENT =====

    /**
     * Create checkout session for subscription
     */
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('MEMBER') or hasRole('ADMIN')")
    @Operation(summary = "Create Stripe checkout session",
               description = "Create a Stripe checkout session for subscription payment")
    public ResponseEntity<CheckoutSessionResponseDto> createCheckoutSession(
            @Valid @RequestBody CreateSubscriptionRequestDto request) {
        try {
            Long userId = getCurrentUserId();
            log.info("🛒 [PaymentController] Creating checkout session for user: {} - Plan: {}",
                    userId, request.getPlanType());

            CheckoutSessionResponseDto response = stripeService.createCheckoutSession(userId, request);

            if (response.isSuccess()) {
                // Log successful checkout session creation
                logAuditEvent(userId, "PAYMENT_CHECKOUT_CREATED",
                    "Created checkout session for " + request.getPlanType() + " plan");

                log.info("✅ [PaymentController] Checkout session created successfully for user: {}", userId);
                return ResponseEntity.ok(response);
            } else {
                // Log failed checkout attempt
                logAuditEvent(userId, "PAYMENT_CHECKOUT_FAILED",
                    "Failed to create checkout session: " + response.getMessage());

                log.warn("❌ [PaymentController] Failed to create checkout session: {}", response.getMessage());
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error creating checkout session: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                CheckoutSessionResponseDto.builder()
                    .success(false)
                    .message("Internal server error: " + e.getMessage())
                    .build()
            );
        }
    }

    /**
     * Get current user's subscription
     */
    @GetMapping("/subscription")
    @PreAuthorize("hasRole('MEMBER') or hasRole('ADMIN')")
    @Operation(summary = "Get user subscription",
               description = "Get current user's active subscription details")
    public ResponseEntity<SubscriptionResponseDto> getUserSubscription() {
        try {
            Long userId = getCurrentUserId();
            log.info("🔍 [PaymentController] Getting subscription for user: {}", userId);

            SubscriptionResponseDto subscription = stripeService.getUserSubscription(userId);

            if (subscription != null) {
                log.info("✅ [PaymentController] Found subscription for user: {} - Plan: {}",
                        userId, subscription.getPlanType());
                return ResponseEntity.ok(subscription);
            } else {
                log.info("ℹ️ [PaymentController] No active subscription found for user: {}", userId);
                return ResponseEntity.noContent().build();
            }
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting subscription: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Cancel user subscription
     */
    @DeleteMapping("/subscription")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Cancel subscription",
               description = "Cancel current user's subscription")
    public ResponseEntity<SubscriptionResponseDto> cancelSubscription(
            @Parameter(description = "Cancel immediately or at period end")
            @RequestParam(defaultValue = "false") boolean immediately) {
        try {
            Long userId = getCurrentUserId();
            log.info("🚫 [PaymentController] Canceling subscription for user: {} - Immediate: {}",
                    userId, immediately);

            SubscriptionResponseDto subscription = stripeService.cancelSubscription(userId, immediately);

            // Log subscription cancellation
            logAuditEvent(userId, "SUBSCRIPTION_CANCELLED",
                "Subscription cancelled - Immediate: " + immediately);

            log.info("✅ [PaymentController] Subscription canceled successfully for user: {}", userId);
            return ResponseEntity.ok(subscription);
        } catch (RuntimeException e) {
            // Log failed cancellation attempt
            logAuditEvent(getCurrentUserId(), "SUBSCRIPTION_CANCEL_FAILED",
                "Failed to cancel subscription: " + e.getMessage());

            log.warn("❌ [PaymentController] Failed to cancel subscription: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error canceling subscription: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===== PAYMENT HISTORY =====

    /**
     * Get user's payment history
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('MEMBER') or hasRole('ADMIN')")
    @Operation(summary = "Get payment history",
               description = "Get current user's payment history with pagination")
    public ResponseEntity<Page<PaymentResponseDto>> getPaymentHistory(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            Long userId = getCurrentUserId();
            log.info("🔍 [PaymentController] Getting payment history for user: {} - page: {}, size: {}",
                    userId, page, size);

            Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<PaymentResponseDto> payments = stripeService.getUserPayments(userId, pageable);

            log.info("✅ [PaymentController] Retrieved {} payments for user: {}",
                    payments.getTotalElements(), userId);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting payment history: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===== WEBHOOK HANDLING =====

    /**
     * Handle Stripe webhook events
     */
    @PostMapping("/webhook")
    @Operation(summary = "Stripe webhook endpoint",
               description = "Handle Stripe webhook events (internal use)")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            log.info("📨 [PaymentController] Received Stripe webhook");

            stripeService.handleWebhookEvent(payload, sigHeader);

            log.info("✅ [PaymentController] Webhook processed successfully");
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    // ===== ADMIN ENDPOINTS =====

    /**
     * Get comprehensive payment dashboard (Admin only)
     */
    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get payment dashboard (Admin)",
               description = "Admin endpoint to get comprehensive payment and subscription dashboard")
    public ResponseEntity<PaymentDashboardResponseDto> getPaymentDashboard(
            @Parameter(description = "Dashboard period in days") @RequestParam(defaultValue = "30") int days) {
        try {
            log.info("📊 [PaymentController] Admin getting payment dashboard for {} days", days);

            PaymentDashboardResponseDto dashboard = stripeService.getPaymentDashboard(days);

            log.info("✅ [PaymentController] Payment dashboard retrieved successfully");
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting payment dashboard: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get revenue statistics (Admin only)
     */
    @GetMapping("/admin/revenue/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get revenue statistics (Admin)",
               description = "Admin endpoint to get detailed revenue statistics and trends")
    public ResponseEntity<RevenueStatisticsResponseDto> getRevenueStatistics(
            @Parameter(description = "Start date (YYYY-MM-DD)") @RequestParam(required = false) String startDate,
            @Parameter(description = "End date (YYYY-MM-DD)") @RequestParam(required = false) String endDate,
            @Parameter(description = "Period type") @RequestParam(defaultValue = "MONTHLY") String periodType,
            @Parameter(description = "Include trends") @RequestParam(defaultValue = "true") boolean includeTrends) {
        try {
            log.info("💰 [PaymentController] Admin getting revenue statistics - Period: {}, Trends: {}",
                    periodType, includeTrends);

            RevenueStatisticsResponseDto statistics = stripeService.getRevenueStatistics(
                startDate, endDate, periodType, includeTrends);

            log.info("✅ [PaymentController] Revenue statistics retrieved - Total Revenue: {}",
                    statistics.getSummary().getTotalRevenue());
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting revenue statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get subscription analytics and package performance (Admin only)
     */
    @GetMapping("/admin/subscriptions/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get subscription analytics (Admin)",
               description = "Admin endpoint to get subscription package performance and premium user analytics")
    public ResponseEntity<SubscriptionAnalyticsResponseDto> getSubscriptionAnalytics(
            @Parameter(description = "Analysis period in days") @RequestParam(defaultValue = "30") int days,
            @Parameter(description = "Include churn analysis") @RequestParam(defaultValue = "true") boolean includeChurn,
            @Parameter(description = "Include premium user details") @RequestParam(defaultValue = "true") boolean includePremiumUsers) {
        try {
            log.info("📈 [PaymentController] Admin getting subscription analytics for {} days", days);

            SubscriptionAnalyticsResponseDto analytics = stripeService.getSubscriptionAnalytics(
                days, includeChurn, includePremiumUsers);

            log.info("✅ [PaymentController] Subscription analytics retrieved - Active: {}, Premium: {}",
                    analytics.getOverview().getTotalActiveSubscriptions(),
                    analytics.getPremiumUsers().getTotalPremiumUsers());
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting subscription analytics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get premium users list with detailed analytics (Admin only)
     */
    @GetMapping("/admin/users/premium")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get premium users (Admin)",
               description = "Admin endpoint to get list of premium users with analytics")
    public ResponseEntity<Page<SubscriptionAnalyticsResponseDto.PremiumUserProfile>> getPremiumUsers(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by") @RequestParam(defaultValue = "totalSpent") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Filter by plan type") @RequestParam(required = false) String planType,
            @Parameter(description = "Filter by risk level") @RequestParam(required = false) String riskLevel) {
        try {
            log.info("👑 [PaymentController] Admin getting premium users - page: {}, size: {}, plan: {}",
                    page, size, planType);

            Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<SubscriptionAnalyticsResponseDto.PremiumUserProfile> premiumUsers =
                stripeService.getPremiumUsers(pageable, planType, riskLevel);

            log.info("✅ [PaymentController] Retrieved {} premium users", premiumUsers.getTotalElements());
            return ResponseEntity.ok(premiumUsers);
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting premium users: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get subscription package performance comparison (Admin only)
     */
    @GetMapping("/admin/packages/performance")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get package performance (Admin)",
               description = "Admin endpoint to compare subscription package performance")
    public ResponseEntity<List<SubscriptionAnalyticsResponseDto.PlanPerformance>> getPackagePerformance(
            @Parameter(description = "Analysis period in days") @RequestParam(defaultValue = "30") int days,
            @Parameter(description = "Sort by metric") @RequestParam(defaultValue = "totalRevenue") String sortBy) {
        try {
            log.info("📦 [PaymentController] Admin getting package performance for {} days", days);

            List<SubscriptionAnalyticsResponseDto.PlanPerformance> performance =
                stripeService.getPackagePerformance(days, sortBy);

            log.info("✅ [PaymentController] Package performance retrieved for {} plans", performance.size());
            return ResponseEntity.ok(performance);
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting package performance: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get revenue trends and forecasting (Admin only)
     */
    @GetMapping("/admin/revenue/trends")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get revenue trends (Admin)",
               description = "Admin endpoint to get revenue trends and forecasting")
    public ResponseEntity<RevenueStatisticsResponseDto.RevenueTrends> getRevenueTrends(
            @Parameter(description = "Trend period in days") @RequestParam(defaultValue = "90") int days,
            @Parameter(description = "Include forecast") @RequestParam(defaultValue = "true") boolean includeForecast) {
        try {
            log.info("📊 [PaymentController] Admin getting revenue trends for {} days", days);

            RevenueStatisticsResponseDto.RevenueTrends trends =
                stripeService.getRevenueTrends(days, includeForecast);

            log.info("✅ [PaymentController] Revenue trends retrieved - MoM Growth: {}%",
                    trends.getMonthOverMonthGrowth());
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting revenue trends: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get churn analysis and at-risk users (Admin only)
     */
    @GetMapping("/admin/churn/analysis")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get churn analysis (Admin)",
               description = "Admin endpoint to get churn analysis and identify at-risk users")
    public ResponseEntity<SubscriptionAnalyticsResponseDto.ChurnAnalysis> getChurnAnalysis(
            @Parameter(description = "Analysis period in days") @RequestParam(defaultValue = "60") int days) {
        try {
            log.info("⚠️ [PaymentController] Admin getting churn analysis for {} days", days);

            SubscriptionAnalyticsResponseDto.ChurnAnalysis churnAnalysis =
                stripeService.getChurnAnalysis(days);

            log.info("✅ [PaymentController] Churn analysis retrieved - Rate: {}%, At Risk: {}",
                    churnAnalysis.getOverallChurnRate(), churnAnalysis.getUsersAtRisk());
            return ResponseEntity.ok(churnAnalysis);
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting churn analysis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Export revenue report (Admin only)
     */
    @GetMapping("/admin/revenue/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Export revenue report (Admin)",
               description = "Admin endpoint to export detailed revenue report")
    public ResponseEntity<Map<String, String>> exportRevenueReport(
            @Parameter(description = "Start date (YYYY-MM-DD)") @RequestParam String startDate,
            @Parameter(description = "End date (YYYY-MM-DD)") @RequestParam String endDate,
            @Parameter(description = "Export format") @RequestParam(defaultValue = "CSV") String format) {
        try {
            log.info("📤 [PaymentController] Admin exporting revenue report - Format: {}, Period: {} to {}",
                    format, startDate, endDate);

            Map<String, String> exportResult = stripeService.exportRevenueReport(startDate, endDate, format);

            log.info("✅ [PaymentController] Revenue report exported successfully");
            return ResponseEntity.ok(exportResult);
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error exporting revenue report: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get subscription by user ID (Admin only)
     */
    @GetMapping("/admin/users/{userId}/subscription")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user subscription (Admin)",
               description = "Admin endpoint to get any user's subscription")
    public ResponseEntity<SubscriptionResponseDto> getAdminUserSubscription(@PathVariable Long userId) {
        try {
            log.info("🔍 [PaymentController] Admin getting subscription for user: {}", userId);

            SubscriptionResponseDto subscription = stripeService.getUserSubscription(userId);

            if (subscription != null) {
                return ResponseEntity.ok(subscription);
            } else {
                return ResponseEntity.noContent().build();
            }
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting admin user subscription: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get payment history by user ID (Admin only)
     */
    @GetMapping("/admin/users/{userId}/payments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user payment history (Admin)",
               description = "Admin endpoint to get any user's payment history")
    public ResponseEntity<Page<PaymentResponseDto>> getAdminUserPayments(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            log.info("🔍 [PaymentController] Admin getting payments for user: {}", userId);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<PaymentResponseDto> payments = stripeService.getUserPayments(userId, pageable);

            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting admin user payments: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===== UTILITY METHODS =====

    private Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();

            // Use direct method that bypasses cache completely to avoid ClassCastException
            Long userId = userService.getUserIdByEmailDirect(email);
            if (userId == null) {
                throw new RuntimeException("User not found for email: " + email);
            }
            return userId;
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error getting current user ID: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get current user ID", e);
        }
    }

    private void logAuditEvent(Long userId, String action, String description) {
        try {
            CreateAuditLogRequestDto auditLog = CreateAuditLogRequestDto.builder()
                    .userId(userId)
                    .action(action + " - " + description)
                    .build();

            auditLogService.create(auditLog);

            log.info("📝 [PaymentController] Audit log created - User: {}, Action: {}",
                    userId, action);
        } catch (Exception e) {
            log.error("❌ [PaymentController] Error logging audit event: {}", e.getMessage(), e);
        }
    }
}
