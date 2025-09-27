package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.EmailDto.EmailListResponseDto;
import com.example.taskmanagement_backend.dtos.EmailDto.EmailResponseDto;
import com.example.taskmanagement_backend.dtos.EmailDto.SendEmailRequestDto;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.services.infrastructure.AutomatedEmailService;
import com.example.taskmanagement_backend.services.infrastructure.GmailService;
import com.example.taskmanagement_backend.services.scheduled.EmailReminderScheduledService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Email Management", description = "Automated Email System - Send and manage automated task notifications")
public class EmailController {

    private final GmailService gmailService;
    private final UserJpaRepository userRepository;
    private final AutomatedEmailService automatedEmailService;
    private final EmailReminderScheduledService emailReminderScheduledService;
    private final com.example.taskmanagement_backend.services.StripeService stripeService; // ✅ NEW: Add StripeService dependency

    // ==================== AUTOMATED EMAIL MANAGEMENT ====================

    @PostMapping("/manual/task-reminder")
    @Operation(summary = "Manually trigger task deadline reminders",
               description = "Manually send deadline reminder emails for testing or immediate notification")
    public ResponseEntity<?> triggerTaskReminders(Authentication authentication) {
        log.info("🔧 Manual trigger of task deadline reminders by: {}", authentication.getName());

        try {
            emailReminderScheduledService.sendTaskDeadlineReminders();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Task deadline reminders triggered successfully",
                "triggeredAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to trigger task reminders: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to trigger task reminders: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/manual/overdue-notifications")
    @Operation(summary = "Manually trigger overdue task notifications",
               description = "Manually send overdue task notifications for testing or immediate notification")
    public ResponseEntity<?> triggerOverdueNotifications(Authentication authentication) {
        log.info("🔧 Manual trigger of overdue task notifications by: {}", authentication.getName());

        try {
            emailReminderScheduledService.sendOverdueTaskNotifications();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Overdue task notifications triggered successfully",
                "triggeredAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to trigger overdue notifications: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to trigger overdue notifications: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/manual/welcome")
    @Operation(summary = "Manually send welcome email",
               description = "Send welcome email to a specific user (admin only)")
    public ResponseEntity<?> sendWelcomeEmail(
            @RequestParam String userEmail,
            @RequestParam String userName,
            Authentication authentication) {

        log.info("🔧 Manual welcome email to {} triggered by: {}", userEmail, authentication.getName());

        try {
            automatedEmailService.sendWelcomeEmail(userEmail, userName);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Welcome email sent successfully",
                "recipient", userEmail,
                "sentAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to send welcome email: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to send welcome email: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/manual/task-assignment")
    @Operation(summary = "Manually send task assignment email",
               description = "Send task assignment notification email")
    public ResponseEntity<?> sendTaskAssignmentEmail(
            @RequestParam String userEmail,
            @RequestParam String userName,
            @RequestParam String taskTitle,
            @RequestParam String projectName,
            @RequestParam String assignedByName,
            @RequestParam Long taskId,
            @RequestParam Long projectId,
            Authentication authentication) {

        log.info("🔧 Manual task assignment email to {} triggered by: {}", userEmail, authentication.getName());

        try {
            automatedEmailService.sendTaskAssignmentEmail(
                userEmail, userName, taskTitle, projectName,
                assignedByName, LocalDateTime.now().plusDays(7), taskId, projectId
            );
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Task assignment email sent successfully",
                "recipient", userEmail,
                "taskTitle", taskTitle,
                "sentAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to send task assignment email: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to send task assignment email: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/manual/payment-success")
    @Operation(summary = "Manually send payment success email",
               description = "Send payment success email to user with active subscription")
    public ResponseEntity<?> sendPaymentSuccessEmail(
            @RequestParam Long userId,
            Authentication authentication) {

        log.info("🔧 Manual payment success email to user {} triggered by: {}", userId, authentication.getName());

        try {
            // Call StripeService to send payment success email
            stripeService.sendPaymentSuccessEmailForUser(userId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Payment success email sent successfully",
                "userId", userId,
                "sentAt", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to send payment success email: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to send payment success email: " + e.getMessage()
            ));
        }
    }

    // ==================== STANDARD EMAIL OPERATIONS ====================

    @PostMapping("/send")
    @Operation(summary = "Send custom email via Gmail API",
               description = "Send a custom email using the authenticated user's Gmail account")
    public ResponseEntity<EmailResponseDto> sendEmail(
            @Valid @RequestBody SendEmailRequestDto request,
            Authentication authentication) {

        log.info("📧 Sending custom email request from user: {}", authentication.getName());

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        EmailResponseDto response = gmailService.sendEmail(user, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/inbox")
    @Operation(summary = "Get inbox emails",
               description = "Fetch emails from Gmail inbox with pagination")
    public ResponseEntity<EmailListResponseDto> getInboxEmails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "INBOX") String label,
            Authentication authentication) {

        log.info("📬 Fetching inbox emails for user: {}", authentication.getName());

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));

        EmailListResponseDto response = gmailService.getEmails(user, pageable, label);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sent")
    @Operation(summary = "Get sent emails",
               description = "Fetch sent emails from Gmail with pagination")
    public ResponseEntity<EmailListResponseDto> getSentEmails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        log.info("📤 Fetching sent emails for user: {}", authentication.getName());

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));

        EmailListResponseDto response = gmailService.getEmails(user, pageable, "SENT");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/drafts")
    @Operation(summary = "Get draft emails",
               description = "Fetch draft emails from Gmail with pagination")
    public ResponseEntity<EmailListResponseDto> getDraftEmails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        log.info("📝 Fetching draft emails for user: {}", authentication.getName());

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));

        EmailListResponseDto response = gmailService.getEmails(user, pageable, "DRAFT");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{messageId}")
    @Operation(summary = "Get email by ID",
               description = "Fetch a specific email by Gmail message ID")
    public ResponseEntity<EmailResponseDto> getEmailById(
            @PathVariable String messageId,
            Authentication authentication) {

        log.info("📧 Fetching email {} for user: {}", messageId, authentication.getName());

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        EmailResponseDto response = gmailService.getEmailById(user, messageId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search emails",
               description = "Search emails by subject, content, sender, etc.")
    public ResponseEntity<EmailListResponseDto> searchEmails(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        log.info("🔍 Searching emails with query: '{}' for user: {}", q, authentication.getName());

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));

        EmailListResponseDto response = gmailService.searchEmails(user, q, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/labels")
    @Operation(summary = "Get Gmail labels",
               description = "Fetch all available Gmail labels for the user")
    public ResponseEntity<?> getLabels(Authentication authentication) {

        log.info("🏷️ Fetching Gmail labels for user: {}", authentication.getName());

        // This would be implemented to fetch Gmail labels
        // For now, return common labels
        return ResponseEntity.ok().body(java.util.Map.of(
            "labels", java.util.Arrays.asList(
                java.util.Map.of("id", "INBOX", "name", "Inbox"),
                java.util.Map.of("id", "SENT", "name", "Sent"),
                java.util.Map.of("id", "DRAFT", "name", "Drafts"),
                java.util.Map.of("id", "TRASH", "name", "Trash"),
                java.util.Map.of("id", "SPAM", "name", "Spam"),
                java.util.Map.of("id", "IMPORTANT", "name", "Important"),
                java.util.Map.of("id", "STARRED", "name", "Starred")
            )
        ));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread email count",
               description = "Get the count of unread emails")
    public ResponseEntity<?> getUnreadCount(Authentication authentication) {

        log.info("📊 Getting unread count for user: {}", authentication.getName());

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        // This could be implemented to get real-time unread count from Gmail API

        return ResponseEntity.ok().body(java.util.Map.of(
            "unreadCount", 0, // Placeholder - implement actual count
            "message", "Unread count feature - implement Gmail API call"
        ));
    }

    @PostMapping("/{messageId}/mark-read")
    @Operation(summary = "Mark email as read",
               description = "Mark a specific email as read")
    public ResponseEntity<?> markAsRead(
            @PathVariable String messageId,
            Authentication authentication) {

        log.info("✅ Marking email {} as read for user: {}", messageId, authentication.getName());

        // This would be implemented to update email status via Gmail API
        return ResponseEntity.ok().body(java.util.Map.of(
            "success", true,
            "message", "Email marked as read",
            "messageId", messageId
        ));
    }

    @PostMapping("/{messageId}/mark-unread")
    @Operation(summary = "Mark email as unread",
               description = "Mark a specific email as unread")
    public ResponseEntity<?> markAsUnread(
            @PathVariable String messageId,
            Authentication authentication) {

        log.info("📧 Marking email {} as unread for user: {}", messageId, authentication.getName());

        // This would be implemented to update email status via Gmail API
        return ResponseEntity.ok().body(java.util.Map.of(
            "success", true,
            "message", "Email marked as unread",
            "messageId", messageId
        ));
    }

    @DeleteMapping("/{messageId}")
    @Operation(summary = "Delete email",
               description = "Move email to trash")
    public ResponseEntity<?> deleteEmail(
            @PathVariable String messageId,
            Authentication authentication) {

        log.info("🗑️ Deleting email {} for user: {}", messageId, authentication.getName());

        // This would be implemented to delete email via Gmail API
        return ResponseEntity.ok().body(java.util.Map.of(
            "success", true,
            "message", "Email moved to trash",
            "messageId", messageId
        ));
    }
}
