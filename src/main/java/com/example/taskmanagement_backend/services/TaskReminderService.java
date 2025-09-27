package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.TaskAssignee;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.TaskJpaRepository;
import com.example.taskmanagement_backend.repositories.TasksAssigneeJpaRepository;
import com.example.taskmanagement_backend.services.infrastructure.AutomatedEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskReminderService {

    private final TaskJpaRepository taskRepository;
    private final TasksAssigneeJpaRepository tasksAssigneeRepository;
    private final AutomatedEmailService automatedEmailService;

    // Lưu trữ các task đã gửi email để tránh spam
    private final Set<String> sentReminders = new HashSet<>();

    /**
     * Chạy mỗi giờ để kiểm tra task sắp quá hạn
     * Cron: 0 0 * * * * = mỗi giờ đúng
     */
    @Scheduled(cron = "0 0 * * * *")
    public void checkOverdueTasks() {
        log.info("🕒 [TaskReminderService] Starting scheduled check for overdue tasks");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderThreshold24h = now.plusDays(1); // 24h trước deadline
        LocalDateTime reminderThreshold3h = now.plusHours(3);  // 3h trước deadline

        try {
            // 1. Kiểm tra task sắp quá hạn trong 24h
            checkTasksNearDeadline(now, reminderThreshold24h, "24_HOURS");

            // 2. Kiểm tra task sắp quá hạn trong 3h (urgent)
            checkTasksNearDeadline(now, reminderThreshold3h, "3_HOURS");

            // 3. Kiểm tra task đã quá hạn
            checkOverdueTasksNow(now);

            log.info("✅ [TaskReminderService] Completed overdue tasks check");

        } catch (Exception e) {
            log.error("❌ [TaskReminderService] Error during overdue tasks check: {}", e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra task sắp quá hạn trong khoảng thời gian nhất định
     */
    private void checkTasksNearDeadline(LocalDateTime now, LocalDateTime deadline, String reminderType) {
        // Convert LocalDateTime to LocalDate for repository query
        java.time.LocalDate startDate = now.toLocalDate();
        java.time.LocalDate endDate = deadline.toLocalDate();

        // Tìm tất cả task có deadline trong khoảng thời gian này và chưa hoàn thành
        List<Task> tasksNearDeadline = taskRepository.findTasksWithDeadlineBetween(startDate, endDate);

        log.info("🔍 [TaskReminderService] Found {} tasks approaching deadline ({} reminder)",
                tasksNearDeadline.size(), reminderType);

        for (Task task : tasksNearDeadline) {
            try {
                // Tạo unique key cho reminder này
                String reminderKey = task.getId() + "_" + reminderType + "_" +
                                   now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));

                // Kiểm tra xem đã gửi email cho task này chưa
                if (sentReminders.contains(reminderKey)) {
                    continue; // Skip nếu đã gửi
                }

                // Gửi email cho creator
                sendReminderToCreator(task, reminderType);

                // Gửi email cho tất cả assignees
                sendReminderToAssignees(task, reminderType);

                // Đánh dấu đã gửi
                sentReminders.add(reminderKey);

                log.info("📧 [TaskReminderService] Sent {} reminder for task: {} - '{}'",
                        reminderType, task.getId(), task.getTitle());

            } catch (Exception e) {
                log.error("❌ [TaskReminderService] Failed to send reminder for task {}: {}",
                         task.getId(), e.getMessage());
            }
        }
    }

    /**
     * Kiểm tra task đã quá hạn
     */
    private void checkOverdueTasksNow(LocalDateTime now) {
        // Convert LocalDateTime to LocalDate for repository query
        java.time.LocalDate currentDate = now.toLocalDate();

        List<Task> overdueTasks = taskRepository.findOverdueTasksForReminder(currentDate);

        log.info("🚨 [TaskReminderService] Found {} overdue tasks", overdueTasks.size());

        for (Task task : overdueTasks) {
            try {
                // Tạo unique key cho overdue reminder
                String reminderKey = task.getId() + "_OVERDUE_" +
                                   now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                // Chỉ gửi 1 lần mỗi ngày cho overdue
                if (sentReminders.contains(reminderKey)) {
                    continue;
                }

                // Gửi email overdue cho creator và assignees
                sendOverdueReminderToCreator(task);
                sendOverdueReminderToAssignees(task);

                // Đánh dấu đã gửi
                sentReminders.add(reminderKey);

                log.info("🚨 [TaskReminderService] Sent overdue reminder for task: {} - '{}'",
                        task.getId(), task.getTitle());

            } catch (Exception e) {
                log.error("❌ [TaskReminderService] Failed to send overdue reminder for task {}: {}",
                         task.getId(), e.getMessage());
            }
        }
    }

    /**
     * Gửi email nhắc nhở cho creator
     */
    private void sendReminderToCreator(Task task, String reminderType) {
        User creator = task.getCreator();
        String userName = getUserDisplayName(creator);

        String subject = getSubjectByReminderType(reminderType, false);
        String urgencyLevel = getUrgencyLevel(reminderType);

        try {
            // Convert LocalDate to LocalDateTime (deadline at end of day)
            LocalDateTime deadlineDateTime = task.getDeadline() != null ?
                task.getDeadline().atTime(23, 59, 59) : LocalDateTime.now();

            // ✅ FIX: Always send FROM system admin, TO the user
            automatedEmailService.sendTaskDeadlineReminder(
                creator.getEmail(), // TO: user's email
                userName,
                task.getTitle(),
                task.getDescription(),
                deadlineDateTime,
                urgencyLevel,
                "creator",
                subject
            );

            log.info("📧 Sent {} reminder to creator: {} for task: {} (sent from system admin)",
                    reminderType, creator.getEmail(), task.getId());

        } catch (Exception e) {
            log.error("❌ Failed to send reminder to creator {}: {}", creator.getEmail(), e.getMessage());
        }
    }

    /**
     * Gửi email nhắc nhở cho tất cả assignees
     */
    private void sendReminderToAssignees(Task task, String reminderType) {
        List<TaskAssignee> assignees = tasksAssigneeRepository.findByTask(task);

        String subject = getSubjectByReminderType(reminderType, false);
        String urgencyLevel = getUrgencyLevel(reminderType);

        for (TaskAssignee assignee : assignees) {
            User user = assignee.getUser();
            String userName = getUserDisplayName(user);

            try {
                // Convert LocalDate to LocalDateTime (deadline at end of day)
                LocalDateTime deadlineDateTime = task.getDeadline() != null ?
                    task.getDeadline().atTime(23, 59, 59) : LocalDateTime.now();

                // ✅ FIX: Always send FROM system admin, TO the user
                automatedEmailService.sendTaskDeadlineReminder(
                    user.getEmail(), // TO: user's email
                    userName,
                    task.getTitle(),
                    task.getDescription(),
                    deadlineDateTime,
                    urgencyLevel,
                    "assignee",
                    subject
                );

                log.info("📧 Sent {} reminder to assignee: {} for task: {} (sent from system admin)",
                        reminderType, user.getEmail(), task.getId());

            } catch (Exception e) {
                log.error("❌ Failed to send reminder to assignee {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    /**
     * Gửi email overdue cho creator
     */
    private void sendOverdueReminderToCreator(Task task) {
        User creator = task.getCreator();
        String userName = getUserDisplayName(creator);

        try {
            // Convert LocalDate to LocalDateTime (deadline at end of day)
            LocalDateTime deadlineDateTime = task.getDeadline() != null ?
                task.getDeadline().atTime(23, 59, 59) : LocalDateTime.now();

            // ✅ FIX: Always send FROM system admin, TO the user
            automatedEmailService.sendTaskDeadlineReminder(
                creator.getEmail(), // TO: user's email
                userName,
                task.getTitle(),
                task.getDescription(),
                deadlineDateTime,
                "OVERDUE",
                "creator",
                "🚨 Task Overdue Alert - Immediate Action Required"
            );

            log.info("🚨 Sent overdue reminder to creator: {} for task: {} (sent from system admin)",
                    creator.getEmail(), task.getId());

        } catch (Exception e) {
            log.error("❌ Failed to send overdue reminder to creator {}: {}", creator.getEmail(), e.getMessage());
        }
    }

    /**
     * Gửi email overdue cho assignees
     */
    private void sendOverdueReminderToAssignees(Task task) {
        List<TaskAssignee> assignees = tasksAssigneeRepository.findByTask(task);

        for (TaskAssignee assignee : assignees) {
            User user = assignee.getUser();
            String userName = getUserDisplayName(user);

            try {
                // Convert LocalDate to LocalDateTime (deadline at end of day)
                LocalDateTime deadlineDateTime = task.getDeadline() != null ?
                    task.getDeadline().atTime(23, 59, 59) : LocalDateTime.now();

                // ✅ FIX: Always send FROM system admin, TO the user
                automatedEmailService.sendTaskDeadlineReminder(
                    user.getEmail(), // TO: user's email
                    userName,
                    task.getTitle(),
                    task.getDescription(),
                    deadlineDateTime,
                    "OVERDUE",
                    "assignee",
                    "🚨 Task Overdue Alert - Immediate Action Required"
                );

                log.info("🚨 Sent overdue reminder to assignee: {} for task: {} (sent from system admin)",
                        user.getEmail(), task.getId());

            } catch (Exception e) {
                log.error("❌ Failed to send overdue reminder to assignee {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    /**
     * Helper: Get subject by reminder type
     */
    private String getSubjectByReminderType(String reminderType, boolean isOverdue) {
        if (isOverdue) {
            return "🚨 Task Overdue Alert - Immediate Action Required";
        }

        return switch (reminderType) {
            case "24_HOURS" -> "⏰ Task Deadline Reminder - Due in 24 Hours";
            case "3_HOURS" -> "🔥 Urgent: Task Due in 3 Hours";
            default -> "⏰ Task Deadline Reminder";
        };
    }

    /**
     * Helper: Get urgency level
     */
    private String getUrgencyLevel(String reminderType) {
        return switch (reminderType) {
            case "24_HOURS" -> "MODERATE";
            case "3_HOURS" -> "HIGH";
            default -> "LOW";
        };
    }

    /**
     * Helper: Get user display name
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

    /**
     * 🗑️ Cleanup sent reminders periodically (mỗi ngày lúc 2:00 AM)
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupSentReminders() {
        log.info("🧹 [TaskReminderService] Cleaning up sent reminders cache");

        // Clear cache để tránh memory leak
        sentReminders.clear();

        log.info("✅ [TaskReminderService] Cleaned up {} reminder entries", sentReminders.size());
    }

    /**
     * Manual method to send reminder for specific task (for testing)
     */
    public void sendManualReminder(Long taskId, String reminderType) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        log.info("📧 [TaskReminderService] Sending manual {} reminder for task: {}", reminderType, taskId);

        sendReminderToCreator(task, reminderType);
        sendReminderToAssignees(task, reminderType);

        log.info("✅ [TaskReminderService] Manual reminder sent successfully");
    }
}
