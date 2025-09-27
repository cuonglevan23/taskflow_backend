package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.config.StripeConfig;
import com.example.taskmanagement_backend.dtos.PaymentDto.*;
import com.example.taskmanagement_backend.entities.*;
import com.example.taskmanagement_backend.entities.Subscription.PlanType;
import com.example.taskmanagement_backend.entities.Subscription.SubscriptionStatus;
import com.example.taskmanagement_backend.repositories.*;
import com.example.taskmanagement_backend.services.infrastructure.AutomatedEmailService;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StripeService {

    private final StripeConfig stripeConfig;
    private final UserJpaRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final UserProfileRepository userProfileRepository;
    private final AutomatedEmailService automatedEmailService;

    // ===== DEMO SUBSCRIPTION CREATION (No Webhooks) =====

    /**
     * Create demo subscription for testing - No real webhooks needed
     */
    public CreateSubscriptionResponseDto createDemoSubscription(CreateSubscriptionRequestDto request) {
        try {
            log.info("🎬 [StripeService] Creating DEMO subscription for user: {}", request.getUserId());

            // Find user
            User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

            // Create Stripe checkout session (for demo)
            Session session = createCheckoutSession(request);

            // Create local subscription record (demo data)
            com.example.taskmanagement_backend.entities.Subscription subscription =
                createLocalSubscription(user, request, session.getId());

            // For demo - immediately activate subscription (skip webhook)
            activateDemoSubscription(subscription, session);

            return CreateSubscriptionResponseDto.builder()
                .sessionId(session.getId())
                .sessionUrl(session.getUrl())
                .subscriptionId(subscription.getId())
                .status("DEMO_ACTIVE")
                .message("Demo subscription created successfully")
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("❌ [StripeService] Error creating demo subscription: {}", e.getMessage(), e);
            return CreateSubscriptionResponseDto.builder()
                .success(false)
                .message("Failed to create demo subscription: " + e.getMessage())
                .build();
        }
    }

    /**
     * Create checkout session for demo
     */
    public CheckoutSessionResponseDto createCheckoutSession(Long userId, CreateSubscriptionRequestDto request) {
        try {
            log.info("🎬 [StripeService] Creating DEMO checkout session for user: {}", userId);

            // Find user
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            // Set userId in request if not already set
            if (request.getUserId() == null) {
                request.setUserId(userId);
            }

            // Create Stripe checkout session (for demo)
            Session session = createStripeCheckoutSession(request, userId);

            // Create local subscription record (demo data)
            com.example.taskmanagement_backend.entities.Subscription subscription =
                createLocalSubscription(user, request, session.getId());

            // For demo - immediately activate subscription (skip webhook)
            activateDemoSubscription(subscription, session);

            return CheckoutSessionResponseDto.builder()
                .success(true)
                .sessionId(session.getId())
                .checkoutUrl(session.getUrl())
                .subscriptionId(subscription.getId().toString())
                .message("Demo checkout session created successfully")
                .build();

        } catch (Exception e) {
            log.error("❌ [StripeService] Error creating demo checkout session: {}", e.getMessage(), e);
            return CheckoutSessionResponseDto.builder()
                .success(false)
                .message("Failed to create demo checkout session: " + e.getMessage())
                .build();
        }
    }

    /**
     * Get user subscription
     */
    public SubscriptionResponseDto getUserSubscription(Long userId) {
        try {
            log.info("🔍 [StripeService] Getting subscription for user: {}", userId);

            // Find active subscription
            com.example.taskmanagement_backend.entities.Subscription subscription =
                    subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                        .orElse(null);

                if (subscription != null) {
                    return convertToSubscriptionDto(subscription);
                }
                return null;
        } catch (Exception e) {
            log.error("❌ [StripeService] Error getting user subscription: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get user subscription", e);
        }
    }

    /**
     * ✅ NEW: Send payment success confirmation for existing premium users
     */
    public void sendPaymentSuccessEmailForUser(Long userId) {
        try {
            log.info("💳 Manually sending payment success email for user: {}", userId);

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            com.example.taskmanagement_backend.entities.Subscription subscription =
                subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                    .orElseThrow(() -> new RuntimeException("No active subscription found for user"));

            String userName = getUserDisplayName(user);
            String transactionId = "manual_" + UUID.randomUUID().toString().substring(0, 8);

            log.info("💳 Sending payment success email to: {} for plan: {}",
                    user.getEmail(), subscription.getPlanType().name());

            automatedEmailService.sendPaymentSuccessEmail(
                    user.getEmail(),
                    userName,
                    subscription.getPlanType().name() + " Plan",
                    subscription.getAmount(),
                    transactionId,
                    subscription.getCurrentPeriodEnd()
            );

            log.info("✅ Payment success email sent successfully to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("❌ Failed to send payment success email for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to send payment success email: " + e.getMessage());
        }
    }

    /**
     * ✅ NEW: Send existing premium user welcome email (less frequent)
     */
    private void sendExistingPremiumWelcomeEmail(com.example.taskmanagement_backend.entities.Subscription subscription) {
        // Only send if subscription was created recently (within last 7 days) to avoid spam
        if (subscription.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            User user = subscription.getUser();
            String userName = getUserDisplayName(user);
            String transactionId = "existing_" + UUID.randomUUID().toString().substring(0, 8);

            log.info("💳 Sending existing premium welcome email to: {} for plan: {}",
                    user.getEmail(), subscription.getPlanType().name());

            automatedEmailService.sendPaymentSuccessEmail(
                    user.getEmail(),
                    userName,
                    subscription.getPlanType().name() + " Plan",
                    subscription.getAmount(),
                    transactionId,
                    subscription.getCurrentPeriodEnd()
            );
        }
    }

    /**
     * Cancel subscription (demo)
     */
    public SubscriptionResponseDto cancelSubscription(Long userId, boolean immediately) {
        try {
            log.info("🎬 [StripeService] Canceling DEMO subscription for user: {} - Immediate: {}", userId, immediately);

            com.example.taskmanagement_backend.entities.Subscription subscription =
                subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                    .orElseThrow(() -> new RuntimeException("No active subscription found"));

            // Update subscription status
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            subscription.setCanceledAt(LocalDateTime.now());
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);

            // Update user premium status
            UserProfile profile = subscription.getUser().getUserProfile();
            if (profile != null) {
                profile.setIsPremium(false);
                profile.setPremiumPlanType(null);
                profile.setPremiumExpiry(null);
                userProfileRepository.save(profile);
            }

            return convertToSubscriptionDto(subscription);
        } catch (Exception e) {
            log.error("❌ [StripeService] Error canceling demo subscription: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to cancel demo subscription: " + e.getMessage());
        }
    }

    /**
     * Get user payments
     */
    public Page<PaymentResponseDto> getUserPayments(Long userId, Pageable pageable) {
        try {
            Page<Payment> payments = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
            return payments.map(this::convertToPaymentDto);
        } catch (Exception e) {
            log.error("❌ [StripeService] Error getting user payments: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get user payments", e);
        }
    }

    /**
     * Handle webhook event (demo - do nothing)
     */
    public void handleWebhookEvent(String payload, String sigHeader) {
        log.info("🎬 [StripeService] DEMO webhook received - skipping real processing");
        // For demo, we don't process real webhooks
    }

    /**
     * Create Stripe Checkout Session
     */
    private Session createStripeCheckoutSession(CreateSubscriptionRequestDto request, Long userId) throws StripeException {
        String priceId = getPriceIdByPlan(request.getPlanType().name());

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(stripeConfig.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(stripeConfig.getCancelUrl())
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build()
            )
            .setClientReferenceId(userId.toString())
            .putMetadata("user_id", userId.toString())
            .putMetadata("plan_type", request.getPlanType().name())
            .setAllowPromotionCodes(true)
            .build();

        return Session.create(params);
    }

    /**
     * Create Stripe Checkout Session
     */
    private Session createCheckoutSession(CreateSubscriptionRequestDto request) throws StripeException {
        String priceId = getPriceIdByPlan(request.getPlanType().name());

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(stripeConfig.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(stripeConfig.getCancelUrl())
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build()
            )
            .setClientReferenceId(request.getUserId().toString())
            .putMetadata("user_id", request.getUserId().toString())
            .putMetadata("plan_type", request.getPlanType().name())
            .setAllowPromotionCodes(request.isAllowPromotionCodes())
            .build();

        return Session.create(params);
    }

    /**
     * Create local subscription record
     */
    private com.example.taskmanagement_backend.entities.Subscription createLocalSubscription(
            User user, CreateSubscriptionRequestDto request, String sessionId) {

        com.example.taskmanagement_backend.entities.Subscription subscription =
            com.example.taskmanagement_backend.entities.Subscription.builder()
                .user(user)
                .stripeSubscriptionId("demo_sub_" + UUID.randomUUID().toString().substring(0, 8))
                .stripeCustomerId("demo_cus_" + UUID.randomUUID().toString().substring(0, 8))
                .stripePriceId(getPriceIdByPlan(request.getPlanType().name()))
                .planType(request.getPlanType())
                .status(SubscriptionStatus.PENDING_PAYMENT)
                .amount(getPriceByPlan(request.getPlanType().name()))
                .currency("USD")
                .startDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return subscriptionRepository.save(subscription);
    }

    /**
     * Activate demo subscription (skip webhook processing)
     */
    private void activateDemoSubscription(com.example.taskmanagement_backend.entities.Subscription subscription, Session session) {
        // Update subscription status
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(LocalDateTime.now());
        subscription.setCurrentPeriodEnd(calculateNextBillingDate(subscription.getPlanType()));
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        // Update user premium status
        UserProfile profile = subscription.getUser().getUserProfile();
        if (profile != null) {
            profile.setIsPremium(true);
            profile.setPremiumPlanType(subscription.getPlanType().name().toLowerCase());
            profile.setPremiumExpiry(subscription.getCurrentPeriodEnd());
            userProfileRepository.save(profile);
        }

        // Create demo payment record
        createDemoPaymentRecord(subscription);

        log.info("✅ [StripeService] Demo subscription activated for user: {}", subscription.getUser().getId());
        // 💳 AUTO-SEND PAYMENT SUCCESS EMAIL
        try {
            User user = subscription.getUser();
            String userName = getUserDisplayName(user);
            String transactionId = "demo_txn_" + UUID.randomUUID().toString().substring(0, 8);

            log.info("💳 Sending payment success email to: {} for plan: {}",
                    user.getEmail(), subscription.getPlanType().name());

            automatedEmailService.sendPaymentSuccessEmail(
                    user.getEmail(),
                    userName,
                    subscription.getPlanType().name() + " Plan",
                    subscription.getAmount(),
                    transactionId,
                    subscription.getCurrentPeriodEnd()
            );
        } catch (Exception e) {
            log.error("❌ Failed to send payment success email for user {}: {}",
                    subscription.getUser().getEmail(), e.getMessage(), e);
            // Don't throw exception - email failure shouldn't block payment processing
        }

    }

    /**
     * Create demo payment record
     */
    private void createDemoPaymentRecord(com.example.taskmanagement_backend.entities.Subscription subscription) {
        Payment payment = Payment.builder()
            .user(subscription.getUser())
            .subscription(subscription)
            .stripePaymentIntentId("demo_pi_" + UUID.randomUUID().toString().substring(0, 8))
            .amount(subscription.getAmount())
            .currency(subscription.getCurrency())
            .status(Payment.PaymentStatus.SUCCEEDED)
            .description("Demo payment for " + subscription.getPlanType().name() + " plan")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        paymentRepository.save(payment);
    }

    // ===== HELPER METHODS =====

    private String getPriceIdByPlan(String planType) {
        return switch (planType.toLowerCase()) {
            case "monthly" -> stripeConfig.getMonthlyPriceId();
            case "quarterly" -> stripeConfig.getQuarterlyPriceId();
            case "yearly" -> stripeConfig.getYearlyPriceId();
            default -> throw new IllegalArgumentException("Invalid plan type: " + planType);
        };
    }

    private BigDecimal getPriceByPlan(String planType) {
        return switch (planType.toLowerCase()) {
            case "monthly" -> new BigDecimal("9.99");
            case "quarterly" -> new BigDecimal("24.99");
            case "yearly" -> new BigDecimal("89.99");
            default -> throw new IllegalArgumentException("Invalid plan type: " + planType);
        };
    }

    private LocalDateTime calculateNextBillingDate(PlanType planType) {
        LocalDateTime now = LocalDateTime.now();
        return switch (planType) {
            case MONTHLY -> now.plusMonths(1);
            case QUARTERLY -> now.plusMonths(3);
            case YEARLY -> now.plusYears(1);
        };
    }

    // ===== SUBSCRIPTION MANAGEMENT =====

    /**
     * Get user subscriptions
     */
    public Page<SubscriptionResponseDto> getUserSubscriptions(Long userId, Pageable pageable) {
        try {
            Page<com.example.taskmanagement_backend.entities.Subscription> subscriptions =
                subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

            return subscriptions.map(this::convertToSubscriptionDto);
        } catch (Exception e) {
            log.error("❌ [StripeService] Error getting user subscriptions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get user subscriptions", e);
        }
    }

    // ===== ANALYTICS METHODS =====

    /**
     * Get payment dashboard overview
     */
    public PaymentDashboardResponseDto getPaymentDashboard(int days) {
        try {
            log.info("📊 [StripeService] Generating payment dashboard for {} days", days);

            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            LocalDateTime endDate = LocalDateTime.now();

            // Get basic metrics - Fix repository method call
            List<com.example.taskmanagement_backend.entities.Subscription> activeSubscriptions =
                subscriptionRepository.findAll().stream()
                    .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                    .collect(Collectors.toList());

            List<Payment> recentPayments = paymentRepository.findByCreatedAtBetween(startDate, endDate);

            BigDecimal totalRevenue = recentPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal monthlyRevenue = recentPayments.stream()
                .filter(p -> p.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30)))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Build overview - Fix lastUpdated type
            PaymentDashboardResponseDto.DashboardOverview overview = PaymentDashboardResponseDto.DashboardOverview.builder()
                .totalRevenue(totalRevenue)
                .monthlyRevenue(monthlyRevenue)
                .totalSubscriptions(activeSubscriptions.size())
                .activeUsers(activeSubscriptions.size())
                .conversionRate(85.5) // Demo value
                .averageOrderValue(totalRevenue.divide(BigDecimal.valueOf(Math.max(1, recentPayments.size())), 2, RoundingMode.HALF_UP))
                .lastUpdated(LocalDateTime.now())
                .build();

            // Build real-time metrics
            PaymentDashboardResponseDto.RealtimeMetrics realtime = PaymentDashboardResponseDto.RealtimeMetrics.builder()
                .onlineUsers(5) // Demo value
                .todaySignups(3) // Demo value
                .todayRevenue(recentPayments.stream()
                    .filter(p -> p.getCreatedAt().isAfter(LocalDateTime.now().minusDays(1)))
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                .todayTransactions((int) recentPayments.stream()
                    .filter(p -> p.getCreatedAt().isAfter(LocalDateTime.now().minusDays(1)))
                    .count())
                .activeTrials(2) // Demo value
                .systemStatus("HEALTHY")
                .build();

            // Build KPIs
            List<PaymentDashboardResponseDto.KpiMetric> kpis = Arrays.asList(
                PaymentDashboardResponseDto.KpiMetric.builder()
                    .name("Monthly Recurring Revenue")
                    .value(monthlyRevenue.toString())
                    .unit("USD")
                    .changePercentage(12.5)
                    .changeDirection("UP")
                    .status("GOOD")
                    .description("MRR growth this month")
                    .build(),
                PaymentDashboardResponseDto.KpiMetric.builder()
                    .name("Active Subscriptions")
                    .value(String.valueOf(activeSubscriptions.size()))
                    .unit("subscriptions")
                    .changePercentage(8.3)
                    .changeDirection("UP")
                    .status("GOOD")
                    .description("New subscriptions this period")
                    .build()
            );

            // Build chart data
            PaymentDashboardResponseDto.ChartData chartData = PaymentDashboardResponseDto.ChartData.builder()
                .revenueChart(generateRevenueChartData(days))
                .planDistribution(generatePlanDistributionData())
                .subscriptionGrowth(generateSubscriptionGrowthData(days))
                .monthlyComparison(generateMonthlyComparisonData())
                .build();

            // Build alerts - Fix timestamp type
            List<PaymentDashboardResponseDto.DashboardAlert> alerts = Arrays.asList(
                PaymentDashboardResponseDto.DashboardAlert.builder()
                    .id("alert_1")
                    .type("SUCCESS")
                    .title("Revenue Milestone")
                    .message("Monthly revenue exceeded $10,000!")
                    .timestamp(LocalDateTime.now())
                    .isRead(false)
                    .build()
            );

            // Build recent activities - Fix user email access
            List<PaymentDashboardResponseDto.RecentActivity> recentActivities = recentPayments.stream()
                .limit(10)
                .map(payment -> PaymentDashboardResponseDto.RecentActivity.builder()
                    .id(payment.getId().toString())
                    .type("PAYMENT")
                    .description("Payment processed")
                    .userEmail(payment.getUser().getEmail())
                    .amount(payment.getAmount())
                    .timestamp(LocalDateTime.now())
                    .status(payment.getStatus().toString())
                    .build())
                .collect(Collectors.toList());

            return PaymentDashboardResponseDto.builder()
                .overview(overview)
                .realtime(realtime)
                .kpis(kpis)
                .chartData(chartData)
                .alerts(alerts)
                .recentActivities(recentActivities)
                .build();

        } catch (Exception e) {
            log.error("❌ [StripeService] Error generating payment dashboard: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate payment dashboard", e);
        }
    }

    /**
     * Get revenue statistics
     */
    public RevenueStatisticsResponseDto getRevenueStatistics(String startDate, String endDate, String periodType, boolean includeTrends) {
        try {
            log.info("💰 [StripeService] Generating revenue statistics - Period: {}, Trends: {}", periodType, includeTrends);

            LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate + "T00:00:00") : LocalDateTime.now().minusDays(30);
            LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate + "T23:59:59") : LocalDateTime.now();

            List<Payment> payments = paymentRepository.findByCreatedAtBetween(start, end);
            List<com.example.taskmanagement_backend.entities.Subscription> subscriptions = subscriptionRepository.findAll();

            BigDecimal totalRevenue = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal averageRevenuePerUser = totalRevenue.divide(
                BigDecimal.valueOf(Math.max(1, subscriptions.size())), 2, RoundingMode.HALF_UP);

            // Build period info
            RevenueStatisticsResponseDto.TimePeriod period = RevenueStatisticsResponseDto.TimePeriod.builder()
                .startDate(start)
                .endDate(end)
                .days((int) java.time.Duration.between(start, end).toDays())
                .periodType(periodType)
                .build();

            // Build summary
            RevenueStatisticsResponseDto.RevenueSummary summary = RevenueStatisticsResponseDto.RevenueSummary.builder()
                .totalRevenue(totalRevenue)
                .averageRevenuePerUser(averageRevenuePerUser)
                .monthlyRecurringRevenue(totalRevenue.multiply(BigDecimal.valueOf(0.8))) // Estimate
                .yearlyRecurringRevenue(totalRevenue.multiply(BigDecimal.valueOf(12)))
                .totalTransactions(payments.size())
                .successfulTransactions((int) payments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.SUCCEEDED).count())
                .failedTransactions((int) payments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.FAILED).count())
                .successRate(payments.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf((double) payments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.SUCCEEDED).count() / payments.size() * 100))
                .activeSubscriptions((int) subscriptions.stream().filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE).count())
                .newSubscriptions((int) subscriptions.stream().filter(s -> s.getCreatedAt().isAfter(start)).count())
                .canceledSubscriptions((int) subscriptions.stream().filter(s -> s.getStatus() == SubscriptionStatus.CANCELLED).count())
                .build();

            // Build plan breakdown
            Map<String, RevenueStatisticsResponseDto.PlanRevenue> planBreakdown = subscriptions.stream()
                .collect(Collectors.groupingBy(
                    s -> s.getPlanType().toString(),
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        subs -> RevenueStatisticsResponseDto.PlanRevenue.builder()
                            .planType(subs.get(0).getPlanType().toString())
                            .revenue(subs.stream().map(com.example.taskmanagement_backend.entities.Subscription::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                            .subscriptionCount(subs.size())
                            .averageRevenue(subs.stream().map(com.example.taskmanagement_backend.entities.Subscription::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(subs.size()), 2, RoundingMode.HALF_UP))
                            .marketShare((double) subs.size() / subscriptions.size() * 100)
                            .newSubscriptions((int) subs.stream().filter(s -> s.getCreatedAt().isAfter(start)).count())
                            .renewalCount(0) // Demo value
                            .cancellationCount((int) subs.stream().filter(s -> s.getStatus() == SubscriptionStatus.CANCELLED).count())
                            .churnRate(5.2) // Demo value
                            .build()
                    )
                ));

            // Build monthly data
            List<RevenueStatisticsResponseDto.MonthlyRevenue> monthlyData = generateMonthlyRevenueData(payments);

            // Build top paying users
            List<RevenueStatisticsResponseDto.TopPayingUser> topPayingUsers = generateTopPayingUsers(payments);

            // Build trends if requested
            RevenueStatisticsResponseDto.RevenueTrends trends = null;
            if (includeTrends) {
                trends = RevenueStatisticsResponseDto.RevenueTrends.builder()
                    .weekOverWeekGrowth(BigDecimal.valueOf(5.2))
                    .monthOverMonthGrowth(BigDecimal.valueOf(12.8))
                    .yearOverYearGrowth(BigDecimal.valueOf(35.4))
                    .trendDirection("UP")
                    .insights(Arrays.asList("Revenue is growing steadily", "Premium plan adoption increasing"))
                    .predictedNextMonthRevenue(totalRevenue.multiply(BigDecimal.valueOf(1.15)))
                    .build();
            }

            return RevenueStatisticsResponseDto.builder()
                .period(period)
                .summary(summary)
                .planBreakdown(planBreakdown)
                .monthlyData(monthlyData)
                .topPayingUsers(topPayingUsers)
                .trends(trends)
                .build();

        } catch (Exception e) {
            log.error("❌ [StripeService] Error generating revenue statistics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate revenue statistics", e);
        }
    }

    /**
     * Get subscription analytics
     */
    public SubscriptionAnalyticsResponseDto getSubscriptionAnalytics(int days, boolean includeChurn, boolean includePremiumUsers) {
        try {
            log.info("📈 [StripeService] Generating subscription analytics for {} days", days);

            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<com.example.taskmanagement_backend.entities.Subscription> allSubscriptions = subscriptionRepository.findAll();
            List<com.example.taskmanagement_backend.entities.Subscription> activeSubscriptions =
                subscriptionRepository.findAll().stream()
                    .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                    .collect(Collectors.toList());

            // Build period info
            SubscriptionAnalyticsResponseDto.TimePeriod period = SubscriptionAnalyticsResponseDto.TimePeriod.builder()
                .startDate(startDate)
                .endDate(LocalDateTime.now())
                .days(days)
                .periodType("DAILY")
                .build();

            // Build overview
            SubscriptionAnalyticsResponseDto.SubscriptionOverview overview = SubscriptionAnalyticsResponseDto.SubscriptionOverview.builder()
                .totalActiveSubscriptions(activeSubscriptions.size())
                .totalCanceledSubscriptions((int) allSubscriptions.stream().filter(s -> s.getStatus() == SubscriptionStatus.CANCELLED).count())
                .newSubscriptionsThisPeriod((int) allSubscriptions.stream().filter(s -> s.getCreatedAt().isAfter(startDate)).count())
                .renewalsThisPeriod(0) // Demo value
                .overallChurnRate(5.2)
                .retentionRate(94.8)
                .averageLifetimeValue(BigDecimal.valueOf(245.50))
                .subscriptionsByStatus(Map.of(
                    "ACTIVE", activeSubscriptions.size(),
                    "CANCELLED", (int) allSubscriptions.stream().filter(s -> s.getStatus() == SubscriptionStatus.CANCELLED).count()
                ))
                .build();

            // Build plan performance
            List<SubscriptionAnalyticsResponseDto.PlanPerformance> planPerformance = generatePlanPerformance(allSubscriptions);

            // Build premium users analytics
            SubscriptionAnalyticsResponseDto.PremiumUserAnalytics premiumUsers = null;
            if (includePremiumUsers) {
                premiumUsers = generatePremiumUserAnalytics(activeSubscriptions);
            }

            // Build churn analysis
            SubscriptionAnalyticsResponseDto.ChurnAnalysis churnAnalysis = null;
            if (includeChurn) {
                churnAnalysis = generateChurnAnalysis(allSubscriptions);
            }

            // Build upgrade patterns
            List<SubscriptionAnalyticsResponseDto.UpgradePattern> upgradePatterns = generateUpgradePatterns();

            return SubscriptionAnalyticsResponseDto.builder()
                .period(period)
                .overview(overview)
                .planPerformance(planPerformance)
                .premiumUsers(premiumUsers)
                .churnAnalysis(churnAnalysis)
                .upgradePatterns(upgradePatterns)
                .build();

        } catch (Exception e) {
            log.error("❌ [StripeService] Error generating subscription analytics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate subscription analytics", e);
        }
    }

    /**
     * Get premium users with pagination
     */
    public Page<SubscriptionAnalyticsResponseDto.PremiumUserProfile> getPremiumUsers(Pageable pageable, String planType, String riskLevel) {
        try {
            log.info("👑 [StripeService] Getting premium users - Plan: {}, Risk: {}", planType, riskLevel);

            List<com.example.taskmanagement_backend.entities.Subscription> activeSubscriptions =
                subscriptionRepository.findAll().stream()
                    .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                    .collect(Collectors.toList());

            List<SubscriptionAnalyticsResponseDto.PremiumUserProfile> premiumUsers = activeSubscriptions.stream()
                .filter(sub -> planType == null || sub.getPlanType().toString().equals(planType))
                .map(this::convertToPremiumUserProfile)
                .filter(user -> riskLevel == null || user.getRiskLevel().equals(riskLevel))
                .collect(Collectors.toList());

            // Simple pagination simulation
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), premiumUsers.size());
            List<SubscriptionAnalyticsResponseDto.PremiumUserProfile> pageContent =
                start < premiumUsers.size() ? premiumUsers.subList(start, end) : Collections.emptyList();

            return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, premiumUsers.size());

        } catch (Exception e) {
            log.error("❌ [StripeService] Error getting premium users: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get premium users", e);
        }
    }

    /**
     * Get package performance comparison
     */
    public List<SubscriptionAnalyticsResponseDto.PlanPerformance> getPackagePerformance(int days, String sortBy) {
        try {
            log.info("📦 [StripeService] Getting package performance for {} days, sorted by {}", days, sortBy);

            List<com.example.taskmanagement_backend.entities.Subscription> allSubscriptions = subscriptionRepository.findAll();
            return generatePlanPerformance(allSubscriptions);

        } catch (Exception e) {
            log.error("❌ [StripeService] Error getting package performance: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get package performance", e);
        }
    }

    /**
     * Get revenue trends and forecasting
     */
    public RevenueStatisticsResponseDto.RevenueTrends getRevenueTrends(int days, boolean includeForecast) {
        try {
            log.info("📊 [StripeService] Getting revenue trends for {} days, forecast: {}", days, includeForecast);

            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<Payment> payments = paymentRepository.findByCreatedAtBetween(startDate, LocalDateTime.now());

            BigDecimal currentRevenue = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return RevenueStatisticsResponseDto.RevenueTrends.builder()
                .weekOverWeekGrowth(BigDecimal.valueOf(5.2))
                .monthOverMonthGrowth(BigDecimal.valueOf(12.8))
                .yearOverYearGrowth(BigDecimal.valueOf(35.4))
                .trendDirection("UP")
                .insights(Arrays.asList(
                    "Revenue growth is accelerating",
                    "Premium plan conversion rate improved by 15%",
                    "Customer retention is at an all-time high"
                ))
                .predictedNextMonthRevenue(includeForecast ? currentRevenue.multiply(BigDecimal.valueOf(1.15)) : null)
                .build();

        } catch (Exception e) {
            log.error("❌ [StripeService] Error getting revenue trends: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get revenue trends", e);
        }
    }

    /**
     * Get churn analysis
     */
    public SubscriptionAnalyticsResponseDto.ChurnAnalysis getChurnAnalysis(int days) {
        try {
            log.info("⚠️ [StripeService] Getting churn analysis for {} days", days);

            List<com.example.taskmanagement_backend.entities.Subscription> allSubscriptions = subscriptionRepository.findAll();
            return generateChurnAnalysis(allSubscriptions);

        } catch (Exception e) {
            log.error("❌ [StripeService] Error getting churn analysis: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get churn analysis", e);
        }
    }

    /**
     * Export revenue report
     */
    public Map<String, String> exportRevenueReport(String startDate, String endDate, String format) {
        try {
            log.info("📤 [StripeService] Exporting revenue report - Format: {}, Period: {} to {}", format, startDate, endDate);

            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");

            List<Payment> payments = paymentRepository.findByCreatedAtBetween(start, end);

            // Generate export file (demo)
            String fileName = "revenue_report_" + startDate + "_to_" + endDate + "." + format.toLowerCase();
            String downloadUrl = "/api/downloads/" + fileName;

            return Map.of(
                "success", "true",
                "fileName", fileName,
                "downloadUrl", downloadUrl,
                "recordCount", String.valueOf(payments.size()),
                "totalRevenue", payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).toString()
            );

        } catch (Exception e) {
            log.error("❌ [StripeService] Error exporting revenue report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to export revenue report", e);
        }
    }

    // ===== HELPER METHODS FOR ANALYTICS =====

    private List<PaymentDashboardResponseDto.TimeSeriesData> generateRevenueChartData(int days) {
        return IntStream.range(0, days)
            .mapToObj(i -> PaymentDashboardResponseDto.TimeSeriesData.builder()
                .date(LocalDateTime.now().minusDays(days - i).toLocalDate().toString())
                .value(BigDecimal.valueOf(100 + i * 10 + Math.random() * 50))
                .label("Day " + (i + 1))
                .build())
            .collect(Collectors.toList());
    }

    private List<PaymentDashboardResponseDto.PieChartData> generatePlanDistributionData() {
        return Arrays.asList(
            PaymentDashboardResponseDto.PieChartData.builder()
                .label("Premium")
                .value(BigDecimal.valueOf(45))
                .percentage(45.0)
                .color("#8884d8")
                .build(),
            PaymentDashboardResponseDto.PieChartData.builder()
                .label("Basic")
                .value(BigDecimal.valueOf(35))
                .percentage(35.0)
                .color("#82ca9d")
                .build(),
            PaymentDashboardResponseDto.PieChartData.builder()
                .label("Starter")
                .value(BigDecimal.valueOf(20))
                .percentage(20.0)
                .color("#ffc658")
                .build()
        );
    }

    private List<PaymentDashboardResponseDto.TimeSeriesData> generateSubscriptionGrowthData(int days) {
        return IntStream.range(0, Math.min(days, 12))
            .mapToObj(i -> PaymentDashboardResponseDto.TimeSeriesData.builder()
                .date(LocalDateTime.now().minusMonths(12 - i).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")))
                .value(BigDecimal.valueOf(50 + i * 15 + Math.random() * 20))
                .label("Month " + (i + 1))
                .build())
            .collect(Collectors.toList());
    }

    private List<PaymentDashboardResponseDto.BarChartData> generateMonthlyComparisonData() {
        return Arrays.asList(
            PaymentDashboardResponseDto.BarChartData.builder()
                .category("This Month")
                .current(BigDecimal.valueOf(12500))
                .previous(BigDecimal.valueOf(11200))
                .growth(11.6)
                .build(),
            PaymentDashboardResponseDto.BarChartData.builder()
                .category("Last Month")
                .current(BigDecimal.valueOf(11200))
                .previous(BigDecimal.valueOf(10800))
                .growth(3.7)
                .build()
        );
    }

    private List<RevenueStatisticsResponseDto.MonthlyRevenue> generateMonthlyRevenueData(List<Payment> payments) {
        Map<String, List<Payment>> paymentsByMonth = payments.stream()
            .collect(Collectors.groupingBy(
                p -> p.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
            ));

        return paymentsByMonth.entrySet().stream()
            .map(entry -> RevenueStatisticsResponseDto.MonthlyRevenue.builder()
                .month(entry.getKey())
                .revenue(entry.getValue().stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .transactionCount(entry.getValue().size())
                .newSubscriptions(5) // Demo value
                .activeSubscriptions(25) // Demo value
                .growthRate(BigDecimal.valueOf(8.5)) // Demo value
                .build())
            .collect(Collectors.toList());
    }

    private List<RevenueStatisticsResponseDto.TopPayingUser> generateTopPayingUsers(List<Payment> payments) {
        // Fix: Use payment.getUser().getEmail() instead of Payment::getUserEmail
        Map<String, List<Payment>> paymentsByUser = payments.stream()
            .collect(Collectors.groupingBy(payment -> payment.getUser().getEmail()));

        return paymentsByUser.entrySet().stream()
            .map(entry -> {
                List<Payment> userPayments = entry.getValue();
                BigDecimal totalRevenue = userPayments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

                return RevenueStatisticsResponseDto.TopPayingUser.builder()
                    .userId(userPayments.get(0).getUser().getId()) // Fix: Use getUser().getId()
                    .userEmail(entry.getKey())
                    .userFullName("User " + entry.getKey().split("@")[0])
                    .totalRevenue(totalRevenue)
                    .transactionCount(userPayments.size())
                    .currentPlan("PREMIUM")
                    .firstPayment(userPayments.stream().min(Comparator.comparing(Payment::getCreatedAt)).get().getCreatedAt())
                    .lastPayment(userPayments.stream().max(Comparator.comparing(Payment::getCreatedAt)).get().getCreatedAt())
                    .loyaltyScore(85.5 + Math.random() * 14.5)
                    .build();
            })
            .sorted(Comparator.comparing(RevenueStatisticsResponseDto.TopPayingUser::getTotalRevenue).reversed())
            .limit(10)
            .collect(Collectors.toList());
    }

    private List<SubscriptionAnalyticsResponseDto.PlanPerformance> generatePlanPerformance(List<com.example.taskmanagement_backend.entities.Subscription> subscriptions) {
        Map<PlanType, List<com.example.taskmanagement_backend.entities.Subscription>> subsByPlan = subscriptions.stream()
            .collect(Collectors.groupingBy(com.example.taskmanagement_backend.entities.Subscription::getPlanType));

        return subsByPlan.entrySet().stream()
            .map(entry -> {
                List<com.example.taskmanagement_backend.entities.Subscription> planSubs = entry.getValue();
                BigDecimal totalRevenue = planSubs.stream().map(com.example.taskmanagement_backend.entities.Subscription::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

                return SubscriptionAnalyticsResponseDto.PlanPerformance.builder()
                    .planType(entry.getKey().toString())
                    .planName(entry.getKey().toString() + " Plan")
                    .planPrice(planSubs.get(0).getAmount())
                    .totalSubscriptions(planSubs.size())
                    .activeSubscriptions((int) planSubs.stream().filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE).count())
                    .newSubscriptions((int) planSubs.stream().filter(s -> s.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30))).count())
                    .cancellations((int) planSubs.stream().filter(s -> s.getStatus() == SubscriptionStatus.CANCELLED).count())
                    .renewals(0) // Demo value
                    .marketShare((double) planSubs.size() / subscriptions.size() * 100)
                    .conversionRate(75.5 + Math.random() * 20)
                    .churnRate(3.5 + Math.random() * 5)
                    .retentionRate(95.5 - Math.random() * 5)
                    .totalRevenue(totalRevenue)
                    .averageRevenuePerUser(totalRevenue.divide(BigDecimal.valueOf(planSubs.size()), 2, RoundingMode.HALF_UP))
                    .growthRate(8.5 + Math.random() * 10)
                    .trendDirection("GROWING")
                    .popularityRank(entry.getKey() == PlanType.MONTHLY ? "MOST_POPULAR" : "SECOND")
                    .satisfactionScore(85.5 + Math.random() * 10)
                    .topFeatures(Arrays.asList("Feature A", "Feature B", "Feature C"))
                    .build();
            })
            .sorted(Comparator.comparing(SubscriptionAnalyticsResponseDto.PlanPerformance::getTotalRevenue).reversed())
            .collect(Collectors.toList());
    }

    private SubscriptionAnalyticsResponseDto.PremiumUserAnalytics generatePremiumUserAnalytics(List<com.example.taskmanagement_backend.entities.Subscription> activeSubscriptions) {
        List<SubscriptionAnalyticsResponseDto.PremiumUserProfile> topUsers = activeSubscriptions.stream()
            .limit(10)
            .map(this::convertToPremiumUserProfile)
            .collect(Collectors.toList());

        Map<String, Integer> usersByPlan = activeSubscriptions.stream()
            .collect(Collectors.groupingBy(
                s -> s.getPlanType().toString(),
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));

        return SubscriptionAnalyticsResponseDto.PremiumUserAnalytics.builder()
            .totalPremiumUsers(activeSubscriptions.size())
            .newPremiumUsers((int) activeSubscriptions.stream().filter(s -> s.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30))).count())
            .premiumUserPercentage(85.5)
            .topPremiumUsers(topUsers)
            .premiumUsersByPlan(usersByPlan)
            .averagePremiumLifetime(18.5)
            .averagePremiumSpending(BigDecimal.valueOf(245.50))
            .premiumUsersByCountry(Map.of("US", 45, "CA", 25, "UK", 20, "Other", 10))
            .averageDailyActiveUsers(75.5)
            .averageFeatureUsage(82.3)
            .mostUsedPremiumFeatures(Arrays.asList("Advanced Analytics", "Priority Support", "Custom Integrations"))
            .build();
    }

    private SubscriptionAnalyticsResponseDto.ChurnAnalysis generateChurnAnalysis(List<com.example.taskmanagement_backend.entities.Subscription> allSubscriptions) {
        Map<String, Double> churnByPlan = allSubscriptions.stream()
            .collect(Collectors.groupingBy(
                s -> s.getPlanType().toString(),
                Collectors.collectingAndThen(
                    Collectors.partitioningBy(s -> s.getStatus() == SubscriptionStatus.CANCELLED),
                    partition -> {
                        int canceled = partition.get(true).size();
                        int total = partition.get(true).size() + partition.get(false).size();
                        return total > 0 ? (double) canceled / total * 100 : 0.0;
                    }
                )
            ));

        List<SubscriptionAnalyticsResponseDto.ChurnReason> churnReasons = Arrays.asList(
            SubscriptionAnalyticsResponseDto.ChurnReason.builder()
                .reason("Price too high")
                .count(15)
                .percentage(35.5)
                .build(),
            SubscriptionAnalyticsResponseDto.ChurnReason.builder()
                .reason("Found alternative")
                .count(12)
                .percentage(28.8)
                .build(),
            SubscriptionAnalyticsResponseDto.ChurnReason.builder()
                .reason("Not using enough")
                .count(8)
                .percentage(19.2)
                .build()
        );

        return SubscriptionAnalyticsResponseDto.ChurnAnalysis.builder()
            .overallChurnRate(5.2)
            .churnRateByPlan(churnByPlan)
            .topChurnReasons(churnReasons)
            .usersAtRisk(8)
            .highRiskUserIds(Arrays.asList(1L, 2L, 3L))
            .averageTimeToChurn(45.5)
            .churnByUserSegment(Map.of("New Users", 8.5, "Long-term Users", 2.3))
            .build();
    }

    private List<SubscriptionAnalyticsResponseDto.UpgradePattern> generateUpgradePatterns() {
        return Arrays.asList(
            SubscriptionAnalyticsResponseDto.UpgradePattern.builder()
                .fromPlan("BASIC")
                .toPlan("PREMIUM")
                .upgradeCount(25)
                .downgradeCount(3)
                .conversionRate(15.5)
                .revenueImpact(BigDecimal.valueOf(2500))
                .averageTimeToUpgrade(30.5)
                .upgradeReasons(Arrays.asList("Need more features", "Growing team"))
                .build(),
            SubscriptionAnalyticsResponseDto.UpgradePattern.builder()
                .fromPlan("STARTER")
                .toPlan("BASIC")
                .upgradeCount(18)
                .downgradeCount(2)
                .conversionRate(22.3)
                .revenueImpact(BigDecimal.valueOf(900))
                .averageTimeToUpgrade(15.2)
                .upgradeReasons(Arrays.asList("Usage exceeded limits", "Better value"))
                .build()
        );
    }

    private SubscriptionAnalyticsResponseDto.PremiumUserProfile convertToPremiumUserProfile(com.example.taskmanagement_backend.entities.Subscription subscription) {
        User user = subscription.getUser();

        return SubscriptionAnalyticsResponseDto.PremiumUserProfile.builder()
            .userId(user.getId())
            .userEmail(user.getEmail())
            .userFullName(getUserDisplayName(user))
            .currentPlan(subscription.getPlanType().toString())
            .totalSpent(subscription.getAmount().multiply(BigDecimal.valueOf(3))) // Estimate
            .subscriptionStartDate(subscription.getCreatedAt())
            .subscriptionDurationMonths((int) java.time.Duration.between(subscription.getCreatedAt(), LocalDateTime.now()).toDays() / 30)
            .engagementScore(75.5 + Math.random() * 20)
            .favoriteFeatures(Arrays.asList("Dashboard", "Reports", "Integrations"))
            .riskLevel(Math.random() > 0.8 ? "HIGH" : Math.random() > 0.6 ? "MEDIUM" : "LOW")
            .build();
    }

    // ===== DTO CONVERSION =====

    private SubscriptionResponseDto convertToSubscriptionDto(com.example.taskmanagement_backend.entities.Subscription subscription) {
        return SubscriptionResponseDto.builder()
            .id(subscription.getId())
            .stripeSubscriptionId(subscription.getStripeSubscriptionId())
            .planType(subscription.getPlanType())
            .status(subscription.getStatus())
            .amount(subscription.getAmount())
            .currency(subscription.getCurrency())
            .currentPeriodStart(subscription.getCurrentPeriodStart())
            .currentPeriodEnd(subscription.getCurrentPeriodEnd())
            .canceledAt(subscription.getCanceledAt())
            .createdAt(subscription.getCreatedAt())
            .updatedAt(subscription.getUpdatedAt())
            .build();
    }

    private PaymentResponseDto convertToPaymentDto(Payment payment) {
        return PaymentResponseDto.builder()
            .id(payment.getId())
            .userId(payment.getUser().getId())
            .subscriptionId(payment.getSubscription() != null ? payment.getSubscription().getId() : null)
            .stripePaymentIntentId(payment.getStripePaymentIntentId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(payment.getStatus())
            .description(payment.getDescription())
            .refundedAmount(payment.getRefundedAmount())
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            .build();
    }

    /**
     * Helper method to get user display name
     */
    private String getUserDisplayName(User user) {
        if (user.getUserProfile() != null) {
            String firstName = user.getUserProfile().getFirstName();
            String lastName = user.getUserProfile().getLastName();

            if (firstName != null && !firstName.trim().isEmpty()) {
                return lastName != null && !lastName.trim().isEmpty()
                        ? firstName + " " + lastName
                        : firstName;
            }
        }

        // Fallback to email prefix
        return user.getEmail().split("@")[0];
    }

    // ===== AUTO-RENEWAL SUPPORT =====

    /**
     * Process auto-renewal for subscription
     */
    public boolean processAutoRenewal(Long userId, com.example.taskmanagement_backend.enums.PlanType planType) {
        try {
            log.info("🔄 [StripeService] Processing auto-renewal for user: {} - Plan: {}", userId, planType);

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if user has existing Stripe customer
            String stripeCustomerId = getOrCreateStripeCustomer(user);

            // Create payment intent for renewal
            PaymentIntent paymentIntent = PaymentIntent.create(
                PaymentIntentCreateParams.builder()
                    .setAmount((long) (planType.getPrice() * 100)) // Amount in cents
                    .setCurrency("usd")
                    .setCustomer(stripeCustomerId)
                    .setDescription("Auto-renewal for " + planType.getDisplayName())
                    .setConfirm(true)
                    .build()
            );

            if ("succeeded".equals(paymentIntent.getStatus())) {
                log.info("✅ [StripeService] Auto-renewal payment successful for user: {}", userId);

                // Create payment record
                createPaymentRecord(user, paymentIntent, planType);

                return true;
            } else {
                log.warn("❌ [StripeService] Auto-renewal payment failed for user: {} - Status: {}",
                        userId, paymentIntent.getStatus());
                return false;
            }

        } catch (StripeException e) {
            log.error("❌ [StripeService] Stripe error during auto-renewal for user: {} - {}",
                    userId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("❌ [StripeService] Error during auto-renewal for user: {} - {}",
                    userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get or create Stripe customer for user
     */
    private String getOrCreateStripeCustomer(User user) throws StripeException {
        // Check if user already has a Stripe customer ID
        // This would typically be stored in user profile or separate table
        UserProfile profile = user.getUserProfile();

        // For demo purposes, create new customer each time
        Customer customer = Customer.create(
            CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(profile != null ?
                    (profile.getFirstName() + " " + profile.getLastName()).trim() :
                    user.getEmail())
                .build()
        );

        return customer.getId();
    }

    /**
     * Create payment record for successful auto-renewal
     */
    private void createPaymentRecord(User user, PaymentIntent paymentIntent,
                                   com.example.taskmanagement_backend.enums.PlanType planType) {
        try {
            Payment payment = Payment.builder()
                .user(user)
                .stripePaymentIntentId(paymentIntent.getId())
                .amount(BigDecimal.valueOf(planType.getPrice()))
                .currency("USD")
                .status(Payment.PaymentStatus.COMPLETED)
                .paymentType(Payment.PaymentType.SUBSCRIPTION)
                .description("Auto-renewal for " + planType.getDisplayName())
                .createdAt(LocalDateTime.now())
                .build();

            paymentRepository.save(payment);
            log.info("✅ [StripeService] Payment record created for auto-renewal - User: {}", user.getId());

        } catch (Exception e) {
            log.error("❌ [StripeService] Error creating payment record: {}", e.getMessage());
        }
    }
}
