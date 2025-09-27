package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.services.TaskReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/task-reminders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Task Reminder Management", description = "Admin endpoints for managing task deadline reminders")
public class TaskReminderController {

    private final TaskReminderService taskReminderService;

    @PostMapping("/check-now")
    @Operation(summary = "Manually trigger overdue task check",
               description = "Manually run the scheduled task to check and send reminder emails for overdue/approaching deadline tasks")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> triggerOverdueTaskCheck() {
        try {
            log.info("🔧 [TaskReminderController] Manual trigger of overdue task check requested");

            taskReminderService.checkOverdueTasks();

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Overdue task check completed successfully",
                "timestamp", java.time.LocalDateTime.now().toString()
            ));

        } catch (Exception e) {
            log.error("❌ [TaskReminderController] Failed to trigger overdue task check: {}", e.getMessage(), e);

            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to trigger overdue task check: " + e.getMessage(),
                "timestamp", java.time.LocalDateTime.now().toString()
            ));
        }
    }

    @PostMapping("/send-manual-reminder/{taskId}")
    @Operation(summary = "Send manual reminder for specific task",
               description = "Send a manual deadline reminder email for a specific task")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> sendManualReminder(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "MODERATE") String reminderType) {

        try {
            log.info("📧 [TaskReminderController] Manual reminder requested for task: {} with type: {}", taskId, reminderType);

            taskReminderService.sendManualReminder(taskId, reminderType);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Manual reminder sent successfully for task " + taskId,
                "reminderType", reminderType,
                "timestamp", java.time.LocalDateTime.now().toString()
            ));

        } catch (Exception e) {
            log.error("❌ [TaskReminderController] Failed to send manual reminder for task {}: {}", taskId, e.getMessage(), e);

            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to send manual reminder: " + e.getMessage(),
                "taskId", taskId.toString(),
                "timestamp", java.time.LocalDateTime.now().toString()
            ));
        }
    }

    @PostMapping("/cleanup-cache")
    @Operation(summary = "Cleanup reminder cache",
               description = "Manually cleanup the sent reminders cache to allow re-sending reminders")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> cleanupReminderCache() {
        try {
            log.info("🧹 [TaskReminderController] Manual cleanup of reminder cache requested");

            taskReminderService.cleanupSentReminders();

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Reminder cache cleaned up successfully",
                "timestamp", java.time.LocalDateTime.now().toString()
            ));

        } catch (Exception e) {
            log.error("❌ [TaskReminderController] Failed to cleanup reminder cache: {}", e.getMessage(), e);

            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to cleanup reminder cache: " + e.getMessage(),
                "timestamp", java.time.LocalDateTime.now().toString()
            ));
        }
    }

    @GetMapping("/status")
    @Operation(summary = "Get reminder system status",
               description = "Get current status and configuration of the task reminder system")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getReminderSystemStatus() {
        try {
            return ResponseEntity.ok(Map.of(
                "status", "active",
                "message", "Task reminder system is running",
                "schedule", "Every hour (0 0 * * * *)",
                "cleanup", "Daily at 2:00 AM (0 0 2 * * *)",
                "features", Map.of(
                    "24HourReminder", "Sends reminder 24 hours before deadline",
                    "3HourReminder", "Sends urgent reminder 3 hours before deadline",
                    "OverdueReminder", "Sends daily reminder for overdue tasks",
                    "DeduplicationCache", "Prevents spam by tracking sent reminders"
                ),
                "timestamp", java.time.LocalDateTime.now().toString()
            ));

        } catch (Exception e) {
            log.error("❌ [TaskReminderController] Failed to get reminder system status: {}", e.getMessage(), e);

            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to get system status: " + e.getMessage(),
                "timestamp", java.time.LocalDateTime.now().toString()
            ));
        }
    }
}
