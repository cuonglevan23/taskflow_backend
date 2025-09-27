 package com.example.taskmanagement_backend.services.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateService {

    private final TemplateEngine templateEngine;

    /**
     * Generate welcome email content for new Google OAuth2 users
     */
    public String generateWelcomeEmail(String userName, String userEmail) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("userEmail", userEmail);
        context.setVariable("currentYear", LocalDateTime.now().getYear());
        context.setVariable("supportEmail", "support@taskflow.com");

        return templateEngine.process("emails/welcome", context);
    }

    /**
     * Generate payment success email content
     */
    public String generatePaymentSuccessEmail(String userName, String planName,
                                            BigDecimal amount, String transactionId,
                                            LocalDateTime nextBillingDate) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("planName", planName);
        context.setVariable("amount", amount);
        context.setVariable("currency", "USD");
        context.setVariable("transactionId", transactionId);
        context.setVariable("paymentDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        context.setVariable("nextBillingDate", nextBillingDate != null ?
            nextBillingDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) : "N/A");
        context.setVariable("currentYear", LocalDateTime.now().getYear());
        context.setVariable("supportEmail", "support@taskflow.com");
        context.setVariable("billingEmail", "billing@taskflow.com");

        return templateEngine.process("emails/payment-success", context);
    }

    /**
     * Generate task deadline reminder email content
     */
    public String generateTaskDeadlineEmail(String userName, String taskTitle,
                                          String projectName, LocalDateTime deadline,
                                          String taskDescription, String taskUrl) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("taskTitle", taskTitle);
        context.setVariable("projectName", projectName);
        context.setVariable("deadline", deadline.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a")));
        context.setVariable("taskDescription", taskDescription);
        context.setVariable("taskUrl", taskUrl);
        context.setVariable("daysLeft", getDaysUntilDeadline(deadline));
        context.setVariable("currentYear", LocalDateTime.now().getYear());
        context.setVariable("supportEmail", "support@taskflow.com");

        return templateEngine.process("emails/task-deadline", context);
    }

    /**
     * Generate task assignment notification email content
     */
    public String generateTaskAssignmentEmail(String userName, String taskTitle,
                                            String projectName, String assignedByName,
                                            LocalDateTime dueDate, String taskUrl) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("taskTitle", taskTitle);
        context.setVariable("projectName", projectName);
        context.setVariable("assignedByName", assignedByName);
        context.setVariable("dueDate", dueDate != null ?
            dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a")) : "No due date");
        context.setVariable("taskUrl", taskUrl);
        context.setVariable("currentYear", LocalDateTime.now().getYear());
        context.setVariable("supportEmail", "support@taskflow.com");

        return templateEngine.process("emails/task-assignment", context);
    }

    /**
     * Generate subscription expiring warning email content
     */
    public String generateSubscriptionExpiringEmail(String userName, String planName,
                                                   LocalDateTime expirationDate, String renewUrl) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("planName", planName);
        context.setVariable("expirationDate", expirationDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        context.setVariable("daysLeft", getDaysUntilDeadline(expirationDate));
        context.setVariable("renewUrl", renewUrl);
        context.setVariable("currentYear", LocalDateTime.now().getYear());
        context.setVariable("supportEmail", "support@taskflow.com");
        context.setVariable("billingEmail", "billing@taskflow.com");

        return templateEngine.process("emails/subscription-expiring", context);
    }

    /**
     * ✅ NEW: Generate task deadline reminder email with enhanced parameters
     */
    public String generateTaskDeadlineReminderEmail(String userName, String taskTitle,
                                                   String taskDescription, LocalDateTime deadline,
                                                   String urgencyLevel, String userRole) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("taskTitle", taskTitle);
        context.setVariable("taskDescription", taskDescription);
        context.setVariable("deadline", deadline.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a")));
        context.setVariable("urgencyLevel", urgencyLevel);
        context.setVariable("userRole", userRole); // "creator" or "assignee"
        context.setVariable("currentYear", LocalDateTime.now().getYear());
        context.setVariable("supportEmail", "support@taskflow.com");

        // Calculate time remaining
        long hoursLeft = java.time.Duration.between(LocalDateTime.now(), deadline).toHours();
        long daysLeft = hoursLeft / 24;

        context.setVariable("hoursLeft", hoursLeft);
        context.setVariable("daysLeft", daysLeft);

        // Set urgency-specific variables
        context.setVariable("isOverdue", deadline.isBefore(LocalDateTime.now()));
        context.setVariable("isUrgent", urgencyLevel.equals("HIGH") || urgencyLevel.equals("OVERDUE"));
        context.setVariable("isModerate", urgencyLevel.equals("MODERATE"));

        // Set role-specific message
        String roleMessage = userRole.equals("creator") ?
            "You created this task" : "This task was assigned to you";
        context.setVariable("roleMessage", roleMessage);

        return templateEngine.process("emails/task-deadline-reminder", context);
    }

    // ===== TRIAL MANAGEMENT EMAIL TEMPLATES =====

    /**
     * 🎁 Generate trial welcome email template
     */
    public String generateTrialWelcomeEmail(String userName, LocalDateTime trialEndDate,
                                           int trialDays, String frontendUrl) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("trialDays", trialDays);
        context.setVariable("trialEndDate", trialEndDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        context.setVariable("trialEndDateFormatted", trialEndDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")));
        context.setVariable("dashboardUrl", frontendUrl + "/dashboard");
        context.setVariable("upgradeUrl", frontendUrl + "/billing/upgrade");
        context.setVariable("featuresUrl", frontendUrl + "/premium-features");
        context.setVariable("currentYear", LocalDateTime.now().getYear());
        context.setVariable("supportEmail", "support@taskflow.com");

        return templateEngine.process("emails/trial-welcome", context);
    }

    /**
     * ⚠️ Generate trial warning email template (sắp hết hạn)
     */
    public String generateTrialWarningEmail(String userName, LocalDateTime trialEndDate,
                                          int daysRemaining, String frontendUrl) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("daysRemaining", daysRemaining);
        context.setVariable("trialEndDate", trialEndDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        context.setVariable("trialEndDateFormatted", trialEndDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")));
        context.setVariable("urgencyLevel", daysRemaining <= 1 ? "CRITICAL" : "HIGH");
        context.setVariable("upgradeUrl", frontendUrl + "/billing/upgrade");
        context.setVariable("pricingUrl", frontendUrl + "/pricing");
        context.setVariable("featuresUrl", frontendUrl + "/premium-features");
        context.setVariable("isLastDay", daysRemaining == 1);
        context.setVariable("currentYear", LocalDateTime.now().getYear());
        context.setVariable("supportEmail", "support@taskflow.com");

        return templateEngine.process("emails/trial-warning", context);
    }

    /**
     * 💔 Generate trial expired email template
     */
    public String generateTrialExpiredEmail(String userName, LocalDateTime expiredDate, String frontendUrl) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("expiredDate", expiredDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        context.setVariable("expiredDateFormatted", expiredDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")));
        context.setVariable("upgradeUrl", frontendUrl + "/billing/upgrade");
        context.setVariable("pricingUrl", frontendUrl + "/pricing");
        context.setVariable("featuresUrl", frontendUrl + "/premium-features");
        context.setVariable("loginUrl", frontendUrl + "/login");
        context.setVariable("currentYear", LocalDateTime.now().getYear());
        context.setVariable("supportEmail", "support@taskflow.com");

        // Tạo discount code cho người dùng hết hạn trial
        String discountCode = "COMEBACK" + String.valueOf(System.currentTimeMillis()).substring(8);
        context.setVariable("discountCode", discountCode);
        context.setVariable("discountPercent", 20);

        return templateEngine.process("emails/trial-expired", context);
    }

    /**
     * 🔄 Generate trial upgrade success email template
     */
    public String generateTrialUpgradeSuccessEmail(String userName, String planName,
                                                 BigDecimal amount, LocalDateTime nextBillingDate,
                                                 String frontendUrl) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("planName", planName);
        context.setVariable("amount", amount);
        context.setVariable("currency", "USD");
        context.setVariable("upgradeDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        context.setVariable("nextBillingDate", nextBillingDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        context.setVariable("nextBillingDateFormatted", nextBillingDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")));
        context.setVariable("dashboardUrl", frontendUrl + "/dashboard");
        context.setVariable("billingUrl", frontendUrl + "/billing");
        context.setVariable("premiumFeaturesUrl", frontendUrl + "/premium-features");
        context.setVariable("currentYear", LocalDateTime.now().getYear());
        context.setVariable("supportEmail", "support@taskflow.com");
        context.setVariable("billingEmail", "billing@taskflow.com");

        return templateEngine.process("emails/trial-upgrade-success", context);
    }

    private long getDaysUntilDeadline(LocalDateTime deadline) {
        return java.time.Duration.between(LocalDateTime.now(), deadline).toDays();
    }
}
