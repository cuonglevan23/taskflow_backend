package com.example.taskmanagement_backend.services.scheduled;

import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.TaskAssignee;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.TaskJpaRepository;
import com.example.taskmanagement_backend.services.infrastructure.AutomatedEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailReminderScheduledService {

    private final TaskJpaRepository taskRepository;
    private final AutomatedEmailService automatedEmailService;

    /**
     * Auto-send task deadline reminders
     * Runs every day at 9 AM to check for tasks due in 1 day
     * ⏰ SCHEDULE: Every day at 9:00 AM
     */
    @Scheduled(cron = "0 0 9 * * ?") // 9 AM daily
    public void sendTaskDeadlineReminders() {
        try {
            log.info("⏰ Starting daily task deadline reminder check...");

            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);

            // ✅ FIX: Use correct repository method to find tasks due tomorrow
            List<Task> upcomingTasks = taskRepository.findTasksDueBetween(tomorrow, tomorrow);

            log.info("📋 Found {} tasks with deadlines tomorrow ({})", upcomingTasks.size(), tomorrow);

            int emailsSent = 0;
            for (Task task : upcomingTasks) {
                try {
                    // ✅ FIX: Handle multiple assignees instead of single assignedUser
                    if (task.getAssignees() != null && !task.getAssignees().isEmpty() && task.getDeadline() != null) {

                        for (TaskAssignee taskAssignee : task.getAssignees()) {
                            User assignedUser = taskAssignee.getUser();
                            String userName = getUserDisplayName(assignedUser);

                            log.info("📧 Sending deadline reminder for task '{}' to user: {} (due: {})",
                                    task.getTitle(), assignedUser.getEmail(), task.getDeadline());

                            // ✅ FIX: Convert LocalDate deadline to LocalDateTime for email service
                            LocalDateTime deadlineDateTime = task.getDeadline().atTime(23, 59, 59);

                            automatedEmailService.sendTaskDeadlineReminder(
                                    assignedUser.getEmail(),
                                    userName,
                                    task.getTitle(),
                                    task.getProject() != null ? task.getProject().getName() : "Personal Task",
                                    deadlineDateTime,
                                    task.getDescription() != null ? task.getDescription() : "No description",
                                    task.getId(),
                                    task.getProject() != null ? task.getProject().getId() : null
                            );
                            emailsSent++;

                            // ✅ PERFORMANCE: Add small delay between emails to avoid overwhelming email service
                            Thread.sleep(100); // 100ms delay between emails
                        }
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to send reminder for task {}: {}", task.getId(), e.getMessage());
                }
            }

            log.info("✅ Completed task deadline reminder check - sent {} emails for {} tasks",
                    emailsSent, upcomingTasks.size());

        } catch (Exception e) {
            log.error("❌ Error in task deadline reminder scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * ✅ NEW: Send immediate overdue task notifications
     * Runs every day at 10 AM to check for overdue tasks
     * ⏰ SCHEDULE: Every day at 10:00 AM
     */
    @Scheduled(cron = "0 0 10 * * ?") // 10 AM daily
    public void sendOverdueTaskNotifications() {
        try {
            log.info("🚨 Starting overdue task notification check...");

            LocalDate today = LocalDate.now();

            // Find tasks that are overdue (deadline < today)
            List<Task> overdueTasks = taskRepository.findOverdueTasks(today);

            log.info("🚨 Found {} overdue tasks", overdueTasks.size());

            int emailsSent = 0;
            for (Task task : overdueTasks) {
                try {
                    if (task.getAssignees() != null && !task.getAssignees().isEmpty() && task.getDeadline() != null) {

                        long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(task.getDeadline(), today);

                        for (TaskAssignee taskAssignee : task.getAssignees()) {
                            User assignedUser = taskAssignee.getUser();
                            String userName = getUserDisplayName(assignedUser);

                            log.info("🚨 Sending overdue notification for task '{}' to user: {} ({} days overdue)",
                                    task.getTitle(), assignedUser.getEmail(), daysOverdue);

                            // Send overdue notification (reuse deadline reminder with different subject)
                            LocalDateTime deadlineDateTime = task.getDeadline().atTime(23, 59, 59);

                            // TODO: Create specific overdue email template in AutomatedEmailService
                            // For now, use deadline reminder with clear overdue indication
                            String overdueTitle = "🚨 OVERDUE: " + task.getTitle() + " (" + daysOverdue + " days overdue)";

                            automatedEmailService.sendTaskDeadlineReminder(
                                    assignedUser.getEmail(),
                                    userName,
                                    overdueTitle,
                                    task.getProject() != null ? task.getProject().getName() : "Personal Task",
                                    deadlineDateTime,
                                    task.getDescription() != null ? task.getDescription() : "No description",
                                    task.getId(),
                                    task.getProject() != null ? task.getProject().getId() : null
                            );
                            emailsSent++;

                            Thread.sleep(100); // Small delay between emails
                        }
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to send overdue notification for task {}: {}", task.getId(), e.getMessage());
                }
            }

            log.info("✅ Completed overdue task notification check - sent {} emails for {} tasks",
                    emailsSent, overdueTasks.size());

        } catch (Exception e) {
            log.error("❌ Error in overdue task notification scheduler: {}", e.getMessage(), e);
        }
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
}
