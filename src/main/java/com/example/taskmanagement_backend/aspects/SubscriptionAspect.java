package com.example.taskmanagement_backend.aspects;

import com.example.taskmanagement_backend.annotations.RequiresPremium;
import com.example.taskmanagement_backend.services.SubscriptionManagementService;
import com.example.taskmanagement_backend.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionAspect {

    private final SubscriptionManagementService subscriptionService;
    private final UserService userService;

    @Around("@annotation(requiresPremium)")
    public Object checkSubscription(ProceedingJoinPoint joinPoint, RequiresPremium requiresPremium) throws Throwable {
        try {
            // Get current user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return joinPoint.proceed(); // Let security handle authentication
            }

            String email = authentication.getName();
            Long userId = userService.getUserIdByEmailDirect(email);

            if (userId == null) {
                return joinPoint.proceed(); // Let it proceed if user not found
            }

            // Check subscription access
            SubscriptionManagementService.SubscriptionAccessDto accessInfo =
                    subscriptionService.checkSubscriptionAccess(userId);

            // Get HTTP method to determine if it's a read operation
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String httpMethod = request.getMethod();
            boolean isReadOperation = "GET".equals(httpMethod);

            // Allow access if subscription is active
            if (accessInfo.isHasAccess()) {
                return joinPoint.proceed();
            }

            // Graceful degradation: Allow read operations but block write operations
            if (isReadOperation && requiresPremium.allowReadOnly()) {
                // Proceed but add subscription info to response
                Object result = joinPoint.proceed();
                return addSubscriptionWarning(result, accessInfo, requiresPremium);
            }

            // For write operations or when read-only is not allowed, return upgrade message
            return createUpgradeResponse(accessInfo, requiresPremium);

        } catch (Exception e) {
            log.error("❌ Error in subscription aspect: {}", e.getMessage(), e);
            // On error, allow the request to proceed to avoid blocking users
            return joinPoint.proceed();
        }
    }

    private Object addSubscriptionWarning(Object result, SubscriptionManagementService.SubscriptionAccessDto accessInfo, RequiresPremium requiresPremium) {
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> response = (ResponseEntity<?>) result;
            Object body = response.getBody();

            if (body instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) body;
                responseMap.put("subscriptionWarning", Map.of(
                    "message", "Your subscription has expired. Upgrade to continue full access.",
                    "status", accessInfo.getStatus(),
                    "planType", accessInfo.getPlanType(),
                    "showUpgradeBanner", true,
                    "feature", requiresPremium.feature()
                ));

                return ResponseEntity.ok(responseMap);
            }
        }

        return result;
    }

    private ResponseEntity<Map<String, Object>> createUpgradeResponse(
            SubscriptionManagementService.SubscriptionAccessDto accessInfo, RequiresPremium requiresPremium) {

        String message = requiresPremium.message();
        if (message.equals("Upgrade to Premium to continue using this feature")) {
            // Customize message based on subscription status
            switch (accessInfo.getStatus()) {
                case TRIAL:
                    if (accessInfo.getDaysRemaining() > 0) {
                        message = String.format("You have %d days left in your trial. Upgrade now to continue after trial expires.",
                                accessInfo.getDaysRemaining());
                    } else {
                        message = "Your 14-day trial has expired. Upgrade to Premium to continue using this feature.";
                    }
                    break;
                case EXPIRED:
                    message = "Your subscription has expired. Renew your plan to continue using premium features.";
                    break;
                default:
                    message = "Premium subscription required to access this feature.";
                    break;
            }
        }

        Map<String, Object> response = Map.of(
            "success", false,
            "requiresUpgrade", true,
            "message", message,
            "subscriptionStatus", accessInfo.getStatus(),
            "planType", accessInfo.getPlanType() != null ? accessInfo.getPlanType() : "none",
            "daysRemaining", accessInfo.getDaysRemaining(),
            "feature", requiresPremium.feature(),
            "upgradeUrl", "/api/payments/checkout",
            "availablePlans", Map.of(
                "monthly", Map.of("price", 9.99, "duration", "30 days"),
                "quarterly", Map.of("price", 24.99, "duration", "90 days"),
                "yearly", Map.of("price", 99.99, "duration", "365 days")
            )
        );

        // Return 402 Payment Required for proper handling on frontend
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
    }
}
