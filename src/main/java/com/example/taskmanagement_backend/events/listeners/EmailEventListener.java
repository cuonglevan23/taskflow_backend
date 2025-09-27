package com.example.taskmanagement_backend.events.listeners;

import com.example.taskmanagement_backend.events.ProjectInvitationCreatedEvent;
import com.example.taskmanagement_backend.services.infrastructure.AutomatedEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailEventListener {

    private final AutomatedEmailService automatedEmailService;

    /**
     * Listen for project invitation events and send welcome email
     * Currently using existing ProjectInvitationCreatedEvent
     */
    @EventListener
    @Async
    public void handleProjectInvitationCreated(ProjectInvitationCreatedEvent event) {
        log.info("📧 Handling project invitation email for: {}", event.getEmail());

        try {
            // Since project invitation uses existing ProjectInvitationCreatedEvent
            // We'll send a simple welcome/invitation email
            automatedEmailService.sendWelcomeEmail(
                    event.getEmail(),
                    "New Member" // Default name since not provided in current event
            );
        } catch (Exception e) {
            log.error("❌ Failed to send project invitation welcome email: {}", e.getMessage(), e);
        }
    }

    // TODO: Add event listeners for the 5 core email types when events are available:

    /**
     * Example: Listen for Google OAuth2 user registration
     * This should be triggered when a user first logs in with Google
     */
    // @EventListener
    // @Async
    // public void handleGoogleUserRegistration(GoogleUserRegistrationEvent event) {
    //     automatedEmailService.sendWelcomeEmail(event.getUserEmail(), event.getUserName());
    // }

    /**
     * Example: Listen for payment success events
     * This should be triggered after successful Stripe payment
     */
    // @EventListener
    // @Async
    // public void handlePaymentSuccess(PaymentSuccessEvent event) {
    //     automatedEmailService.sendPaymentSuccessEmail(
    //         event.getUserEmail(), event.getUserName(), event.getPlanName(),
    //         event.getAmount(), event.getTransactionId(), event.getNextBillingDate()
    //     );
    // }

    /**
     * Example: Listen for task deadline reminders
     * This should be triggered by scheduled service checking upcoming deadlines
     */
    // @EventListener
    // @Async
    // public void handleTaskDeadlineReminder(TaskDeadlineReminderEvent event) {
    //     automatedEmailService.sendTaskDeadlineReminder(
    //         event.getUserEmail(), event.getUserName(), event.getTaskTitle(),
    //         event.getProjectName(), event.getDeadline(), event.getTaskDescription(),
    //         event.getTaskId(), event.getProjectId()
    //     );
    // }

    /**
     * Example: Listen for task assignment events
     * This should be triggered when a task is assigned to a user
     */
    // @EventListener
    // @Async
    // public void handleTaskAssignment(TaskAssignmentEvent event) {
    //     automatedEmailService.sendTaskAssignmentEmail(
    //         event.getUserEmail(), event.getUserName(), event.getTaskTitle(),
    //         event.getProjectName(), event.getAssignedByName(), event.getDueDate(),
    //         event.getTaskId(), event.getProjectId()
    //     );
    // }

    /**
     * Example: Listen for subscription expiring events
     * This should be triggered by scheduled service checking subscription expiration
     */
    // @EventListener
    // @Async
    // public void handleSubscriptionExpiring(SubscriptionExpiringEvent event) {
    //     automatedEmailService.sendSubscriptionExpiringEmail(
    //         event.getUserEmail(), event.getUserName(), event.getPlanName(),
    //         event.getExpirationDate()
    //     );
    // }
}
