package com.example.taskmanagement_backend.services.infrastructure;

import com.example.taskmanagement_backend.dtos.EmailDto.SendEmailRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutomatedEmailService {

    private final EmailTemplateService emailTemplateService;
    private final SmtpEmailService smtpEmailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * Send welcome email to new Google OAuth2 user
     */
    public void sendWelcomeEmail(String userEmail, String userName) {
        try {
            log.info("🚀 Sending welcome email to new Google user: {}", userEmail);

            String emailContent = emailTemplateService.generateWelcomeEmail(userName, userEmail);

            SendEmailRequestDto emailRequest = SendEmailRequestDto.builder()
                    .to(Arrays.asList(userEmail))
                    .subject("Welcome to TaskFlow - Let's Get Started! 🚀")
                    .body(emailContent)
                    .isHtml(true)
                    .build();

            // ✅ FIXED: Use SMTP instead of Gmail OAuth2
            smtpEmailService.sendEmail(emailRequest);
            log.info("✅ Welcome email sent successfully to: {}", userEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send welcome email to {}: {}", userEmail, e.getMessage());
            // Don't rethrow - welcome email failure shouldn't block user registration
        }
    }

    /**
     * 🎁 Send welcome email WITH automatic trial initialization
     */
    public void sendWelcomeEmailWithTrial(String userEmail, String userName, Long userId) {
        try {
            log.info("🎁 Sending welcome email with trial initialization to: {}", userEmail);

            // Đầu tiên gửi welcome email
            sendWelcomeEmail(userEmail, userName);

            // Sau đó khởi tạo trial (tách biệt để tránh conflict)
            // This will be handled by a separate service call after user creation
            log.info("✅ Welcome email sent, trial will be initialized separately for: {}", userEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send welcome email with trial to {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Send payment success confirmation email
     */
    public void sendPaymentSuccessEmail(String userEmail, String userName, String planName,
                                       BigDecimal amount, String transactionId, LocalDateTime nextBillingDate) {
        try {
            log.info("💳 Sending payment success email to: {} for plan: {}", userEmail, planName);

            String emailContent = emailTemplateService.generatePaymentSuccessEmail(
                    userName, planName, amount, transactionId, nextBillingDate);

            SendEmailRequestDto emailRequest = SendEmailRequestDto.builder()
                    .to(Arrays.asList(userEmail))
                    .subject("🎉 Payment Successful - Welcome to Premium!")
                    .body(emailContent)
                    .isHtml(true)
                    .build();

            // ✅ FIXED: Use SMTP instead of Gmail OAuth2
            smtpEmailService.sendEmail(emailRequest);
            log.info("✅ Payment success email sent successfully to: {}", userEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send payment success email to {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Send task deadline reminder email
     */
    public void sendTaskDeadlineReminder(String userEmail, String userName, String taskTitle,
                                       String projectName, LocalDateTime deadline,
                                       String taskDescription, Long taskId, Long projectId) {
        try {
            log.info("⏰ Sending task deadline reminder to: {} for task: {}", userEmail, taskTitle);

            String taskUrl = frontendUrl + "/projects/" + projectId + "/tasks/" + taskId;
            String emailContent = emailTemplateService.generateTaskDeadlineEmail(
                    userName, taskTitle, projectName, deadline, taskDescription, taskUrl);

            SendEmailRequestDto emailRequest = SendEmailRequestDto.builder()
                    .to(Arrays.asList(userEmail))
                    .subject("⚠️ Task Deadline Reminder: " + taskTitle)
                    .body(emailContent)
                    .isHtml(true)
                    .build();

            // ✅ FIXED: Use SMTP instead of Gmail OAuth2
            smtpEmailService.sendEmail(emailRequest);
            log.info("✅ Task deadline reminder email sent successfully to: {}", userEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send task deadline reminder to {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Send task assignment notification email
     */
    public void sendTaskAssignmentEmail(String userEmail, String userName, String taskTitle,
                                      String projectName, String assignedByName, LocalDateTime dueDate,
                                      Long taskId, Long projectId) {
        try {
            log.info("📋 Sending task assignment email to: {} for task: {}", userEmail, taskTitle);

            String taskUrl = frontendUrl + "/projects/" + projectId + "/tasks/" + taskId;
            String emailContent = emailTemplateService.generateTaskAssignmentEmail(
                    userName, taskTitle, projectName, assignedByName, dueDate, taskUrl);

            SendEmailRequestDto emailRequest = SendEmailRequestDto.builder()
                    .to(Arrays.asList(userEmail))
                    .subject("New Task Assigned: " + taskTitle + " 📋")
                    .body(emailContent)
                    .isHtml(true)
                    .build();

            // ✅ FIXED: Use SMTP instead of Gmail OAuth2
            smtpEmailService.sendEmail(emailRequest);
            log.info("✅ Task assignment email sent successfully to: {}", userEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send task assignment email to {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Send subscription expiring warning email
     */
    public void sendSubscriptionExpiringEmail(String userEmail, String userName, String planName,
                                            LocalDateTime expirationDate) {
        try {
            log.info("⚠️ Sending subscription expiring email to: {} for plan: {}", userEmail, planName);

            String renewUrl = frontendUrl + "/billing/upgrade";
            String emailContent = emailTemplateService.generateSubscriptionExpiringEmail(
                    userName, planName, expirationDate, renewUrl);

            SendEmailRequestDto emailRequest = SendEmailRequestDto.builder()
                    .to(Arrays.asList(userEmail))
                    .subject("⚠️ Your " + planName + " Subscription is Expiring Soon")
                    .body(emailContent)
                    .isHtml(true)
                    .build();

            // ✅ FIXED: Use SMTP instead of Gmail OAuth2
            smtpEmailService.sendEmail(emailRequest);
            log.info("✅ Subscription expiring email sent successfully to: {}", userEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send subscription expiring email to {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * ✅ FIXED: Send task deadline reminder email with enhanced parameters
     */
    public void sendTaskDeadlineReminder(String userEmail, String userName, String taskTitle,
                                       String taskDescription, LocalDateTime deadline,
                                       String urgencyLevel, String userRole, String customSubject) {
        try {
            log.info("⏰ Sending {} task deadline reminder to: {} for task: {}", urgencyLevel, userEmail, taskTitle);

            String emailContent = emailTemplateService.generateTaskDeadlineReminderEmail(
                    userName, taskTitle, taskDescription, deadline, urgencyLevel, userRole);

            SendEmailRequestDto emailRequest = SendEmailRequestDto.builder()
                    .to(Arrays.asList(userEmail))
                    .subject(customSubject != null ? customSubject : getDefaultSubject(urgencyLevel, taskTitle))
                    .body(emailContent)
                    .isHtml(true)
                    .build();

            // ✅ FIXED: Use SMTP instead of Gmail OAuth2
            smtpEmailService.sendEmail(emailRequest);
            log.info("✅ Task deadline reminder email sent successfully to: {}", userEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send task deadline reminder to {}: {}", userEmail, e.getMessage());
            throw new RuntimeException("Failed to send task deadline reminder email", e);
        }
    }

    /**
     * Helper method to get default subject based on urgency level
     */
    private String getDefaultSubject(String urgencyLevel, String taskTitle) {
        return switch (urgencyLevel.toUpperCase()) {
            case "OVERDUE" -> "🚨 Task Overdue Alert - " + taskTitle;
            case "HIGH" -> "🔥 Urgent: Task Due Soon - " + taskTitle;
            case "MODERATE" -> "⏰ Task Deadline Reminder - " + taskTitle;
            default -> "📋 Task Reminder - " + taskTitle;
        };
    }

    /**
     * Check if email service is available
     */
    public boolean isEmailServiceAvailable() {
        return smtpEmailService.isEmailServiceAvailable();
    }

    // ===== TRIAL MANAGEMENT EMAIL NOTIFICATIONS =====

    /**
     * 🎯 Gửi email chào mừng khi bắt đầu trial 14 ngày
     */
    public void sendTrialWelcomeEmail(String userEmail, String userName, LocalDateTime trialEndDate, int trialDays) {
        try {
            log.info("🎁 Sending trial welcome email to: {} ({} days trial)", userEmail, trialDays);

            String emailContent = emailTemplateService.generateTrialWelcomeEmail(
                    userName, trialEndDate, trialDays, frontendUrl);

            SendEmailRequestDto emailRequest = SendEmailRequestDto.builder()
                    .to(Arrays.asList(userEmail))
                    .subject("🎉 Chào mừng bạn đến với TaskFlow Premium Trial!")
                    .body(emailContent)
                    .isHtml(true)
                    .build();

            // ✅ FIXED: Use SMTP instead of Gmail OAuth2
            smtpEmailService.sendEmail(emailRequest);
            log.info("✅ Trial welcome email sent successfully to: {}", userEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send trial welcome email to {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * ⚠️ Gửi email cảnh báo trial sắp hết hạn
     */
    public void sendTrialWarningEmail(String userEmail, String userName, LocalDateTime trialEndDate, int daysRemaining) {
        try {
            log.info("⚠️ Sending trial warning email to: {} ({} days remaining)", userEmail, daysRemaining);

            String emailContent = emailTemplateService.generateTrialWarningEmail(
                    userName, trialEndDate, daysRemaining, frontendUrl);

            String subject = daysRemaining == 1
                    ? "⏰ Trial của bạn sẽ hết hạn vào ngày mai!"
                    : String.format("⏰ Trial của bạn còn %d ngày - Đừng bỏ lỡ!", daysRemaining);

            SendEmailRequestDto emailRequest = SendEmailRequestDto.builder()
                    .to(Arrays.asList(userEmail))
                    .subject(subject)
                    .body(emailContent)
                    .isHtml(true)
                    .build();

            // ✅ FIXED: Use SMTP instead of Gmail OAuth2
            smtpEmailService.sendEmail(emailRequest);
            log.info("✅ Trial warning email sent successfully to: {}", userEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send trial warning email to {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * 💔 Gửi email thông báo trial đã hết hạn
     */
    public void sendTrialExpiredEmail(String userEmail, String userName, LocalDateTime expiredDate) {
        try {
            log.info("💔 Sending trial expired email to: {}", userEmail);

            String emailContent = emailTemplateService.generateTrialExpiredEmail(
                    userName, expiredDate, frontendUrl);

            SendEmailRequestDto emailRequest = SendEmailRequestDto.builder()
                    .to(Arrays.asList(userEmail))
                    .subject("💔 Trial của bạn đã hết hạn - Tiếp tục với Premium!")
                    .body(emailContent)
                    .isHtml(true)
                    .build();

            // ✅ FIXED: Use SMTP instead of Gmail OAuth2
            smtpEmailService.sendEmail(emailRequest);
            log.info("✅ Trial expired email sent successfully to: {}", userEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send trial expired email to {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * 🔄 Gửi email thông báo gia hạn thành công từ trial lên premium
     */
    public void sendTrialUpgradeSuccessEmail(String userEmail, String userName, String planName,
                                           BigDecimal amount, LocalDateTime nextBillingDate) {
        try {
            log.info("🔄 Sending trial upgrade success email to: {} - Plan: {}", userEmail, planName);

            String emailContent = emailTemplateService.generateTrialUpgradeSuccessEmail(
                    userName, planName, amount, nextBillingDate, frontendUrl);

            SendEmailRequestDto emailRequest = SendEmailRequestDto.builder()
                    .to(Arrays.asList(userEmail))
                    .subject("🎉 Chúc mừng! Bạn đã nâng cấp lên Premium thành công!")
                    .body(emailContent)
                    .isHtml(true)
                    .build();

            // ✅ FIXED: Use SMTP instead of Gmail OAuth2
            smtpEmailService.sendEmail(emailRequest);
            log.info("✅ Trial upgrade success email sent successfully to: {}", userEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send trial upgrade success email to {}: {}", userEmail, e.getMessage());
        }
    }
}
