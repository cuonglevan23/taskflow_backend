package com.example.taskmanagement_backend.agent.tools;

import com.example.taskmanagement_backend.dtos.TaskDto.CreateTaskRequestDto;
import com.example.taskmanagement_backend.dtos.TaskDto.UpdateTaskRequestDto;
import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.MyTaskSummaryDto;
import com.example.taskmanagement_backend.enums.TaskPriority;
import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.services.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * MyTask API Tools for AI Agent
 * Allows AI to perform task management operations
 * Integrates with existing TaskService
 * Note: Uses custom annotations instead of Spring AI @Tool for compatibility
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MyTaskTools {

    private final TaskService taskService;

    /**
     * Create a new task - ACTION TOOL
     */
    public String createTask(String title, String description, String priority, String deadline, Long projectId, Long userId) {
        try {
            log.info("AI Agent creating task: title={}, priority={}, userId={}", title, priority, userId);

            CreateTaskRequestDto taskDto = new CreateTaskRequestDto();
            taskDto.setTitle(title);
            taskDto.setDescription(description);
            taskDto.setCreatorId(userId); // Set creator ID

            // Set intelligent priority default - DTOs expect String values
            if (priority != null && !priority.isEmpty()) {
                try {
                    TaskPriority.valueOf(priority.toUpperCase()); // Validate enum exists
                    taskDto.setPriority(priority.toUpperCase()); // Store as String
                } catch (IllegalArgumentException e) {
                    taskDto.setPriority("MEDIUM"); // Default fallback as String
                }
            } else {
                // AI-based priority detection - convert enum to String
                TaskPriority detectedPriority = detectPriorityFromContent(title + " " + description);
                taskDto.setPriority(detectedPriority.name());
            }

            // Parse deadline if provided - DTOs expect LocalDate, not LocalDateTime
            if (deadline != null && !deadline.isEmpty()) {
                try {
                    LocalDateTime deadlineTime = LocalDateTime.parse(deadline);
                    taskDto.setDeadline(deadlineTime.toLocalDate()); // Convert to LocalDate
                } catch (Exception e) {
                    log.warn("Could not parse deadline: {}", deadline);
                }
            }

            if (projectId != null) {
                taskDto.setProjectId(projectId);
            }

            TaskResponseDto createdTask = taskService.createTask(taskDto);

            return String.format("✅ Task created successfully! " +
                                "ID: %d, Title: '%s', Priority: %s, Status: %s" +
                                (createdTask.getDeadline() != null ? ", Deadline: %s" : ""),
                                createdTask.getId(),
                                createdTask.getTitle(),
                                createdTask.getPriority(),
                                createdTask.getStatus(),
                                createdTask.getDeadline() != null ?
                                    createdTask.getDeadline().toString() : ""); // FIXED: Use toString() instead of format()

        } catch (Exception e) {
            log.error("Error creating task via AI Agent", e);
            return "❌ Sorry, I couldn't create the task. Error: " + e.getMessage() +
                   ". Please check if you have permission to create tasks.";
        }
    }

    /**
     * Get user's tasks - INFORMATION RETRIEVAL TOOL
     */
    public String getUserTasks(Long userId, String status, String priority, Long projectId, Integer limit) {
        try {
            log.info("AI Agent retrieving tasks for user: {}, status={}, priority={}", userId, status, priority);

            // Use a simplified approach - get task stats and provide basic information
            // Since we can't directly access task lists without authentication context,
            // we'll provide a helpful response and suggest using the web interface
            try {
                // Try to get basic task statistics
                Map<String, Object> stats = taskService.getMyTasksStats();
                long totalTasks = (Long) stats.get("totalParticipatingTasks");

                if (totalTasks == 0) {
                    return "📋 You don't have any tasks yet. Would you like me to help you create your first task?";
                }

                StringBuilder result = new StringBuilder();
                result.append(String.format("📋 You have %d task%s in total.\n\n", totalTasks, totalTasks == 1 ? "" : "s"));

                // Add filter information if provided
                if (status != null || priority != null || projectId != null) {
                    result.append("For detailed filtering by ");
                    if (status != null) result.append("status (").append(status).append(") ");
                    if (priority != null) result.append("priority (").append(priority).append(") ");
                    if (projectId != null) result.append("project (").append(projectId).append(") ");
                    result.append(", please use the web interface or specify the exact task you're looking for.\n\n");
                }

                result.append("💡 You can also ask me to:\n");
                result.append("- Create a new task: 'Create task [title]'\n");
                result.append("- Get task statistics: 'Show my task progress'\n");
                result.append("- Update a specific task: 'Update task [ID] to completed'\n");

                return result.toString();

            } catch (Exception e) {
                log.warn("Could not retrieve task stats for user {}: {}", userId, e.getMessage());
                return "❌ Sorry, I couldn't retrieve your task information. This might be due to authentication requirements. " +
                       "Please use the web interface to view your tasks, or let me know if you'd like to create a new task!";
            }

        } catch (Exception e) {
            log.error("Error retrieving tasks via AI Agent", e);
            return "❌ Sorry, I couldn't retrieve your tasks. Error: " + e.getMessage();
        }
    }

    /**
     * Update a task - ACTION TOOL
     */
    public String updateTask(Long taskId, String title, String description, String status,
                           String priority, String deadline, Long userId) {
        try {
            log.info("AI Agent updating task: taskId={}, userId={}", taskId, userId);

            TaskResponseDto existingTask = taskService.getTaskById(taskId);
            if (existingTask == null) {
                return "❌ Task not found with ID: " + taskId;
            }

            UpdateTaskRequestDto updateDto = new UpdateTaskRequestDto();

            // Update fields if provided - DTOs expect String values, not enums
            if (title != null && !title.isEmpty()) {
                updateDto.setTitle(title);
            }
            if (description != null && !description.isEmpty()) {
                updateDto.setDescription(description);
            }
            if (status != null && !status.isEmpty()) {
                try {
                    TaskStatus.valueOf(status.toUpperCase()); // Validate enum exists
                    updateDto.setStatus(status.toUpperCase()); // Store as String
                } catch (IllegalArgumentException e) {
                    return "❌ Invalid status. Valid values: PENDING, IN_PROGRESS, COMPLETED, CANCELLED";
                }
            }
            if (priority != null && !priority.isEmpty()) {
                try {
                    TaskPriority.valueOf(priority.toUpperCase()); // Validate enum exists
                    updateDto.setPriority(priority.toUpperCase()); // Store as String
                } catch (IllegalArgumentException e) {
                    return "❌ Invalid priority. Valid values: LOW, MEDIUM, HIGH, URGENT";
                }
            }
            if (deadline != null && !deadline.isEmpty()) {
                try {
                    LocalDateTime deadlineTime = LocalDateTime.parse(deadline);
                    updateDto.setDeadline(deadlineTime.toLocalDate()); // Convert to LocalDate
                } catch (Exception e) {
                    return "❌ Invalid deadline format. Please use ISO format (e.g., 2024-12-25T15:30:00)";
                }
            }

            TaskResponseDto updatedTask = taskService.updateTask(taskId, updateDto);

            return String.format("✅ Task updated successfully! " +
                                "ID: %d, Title: '%s', Status: %s, Priority: %s",
                                updatedTask.getId(),
                                updatedTask.getTitle(),
                                updatedTask.getStatus(),
                                updatedTask.getPriority());

        } catch (Exception e) {
            log.error("Error updating task via AI Agent", e);
            return "❌ Sorry, I couldn't update the task. Error: " + e.getMessage();
        }
    }

    /**
     * Delete a task - ACTION TOOL
     */
    public String deleteTask(Long taskId, Long userId) {
        try {
            log.info("AI Agent deleting task: taskId={}, userId={}", taskId, userId);

            TaskResponseDto existingTask = taskService.getTaskById(taskId);
            if (existingTask == null) {
                return "❌ Task not found with ID: " + taskId;
            }

            String taskTitle = existingTask.getTitle();
            taskService.deleteTask(taskId);

            return String.format("✅ Task deleted successfully! Removed task: '%s' (ID: %d)", taskTitle, taskId);

        } catch (Exception e) {
            log.error("Error deleting task via AI Agent", e);
            return "❌ Sorry, I couldn't delete the task. Error: " + e.getMessage();
        }
    }

    /**
     * Get current date and time - INFORMATION RETRIEVAL TOOL
     */
    public String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now();
        return String.format("Current date and time: %s (%s)",
                           now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                           now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    /**
     * Get task statistics - INFORMATION RETRIEVAL TOOL
     */
    public String getTaskStatistics(Long userId) {
        try {
            log.info("AI Agent getting task statistics for user: {}", userId);

            // Use the available getMyTasksStats method to get basic statistics
            try {
                Map<String, Object> stats = taskService.getMyTasksStats();
                long totalTasks = (Long) stats.get("totalParticipatingTasks");
                long regularTasks = (Long) stats.get("totalRegularTasks");
                long projectTasks = (Long) stats.get("totalProjectTasks");

                if (totalTasks == 0) {
                    return "📊 You don't have any tasks yet. Would you like to create your first task?";
                }

                StringBuilder result = new StringBuilder();
                result.append("📊 **Your Task Statistics**\n\n");
                result.append(String.format("**Total Tasks:** %d\n", totalTasks));
                result.append(String.format("**Regular Tasks:** %d\n", regularTasks));
                result.append(String.format("**Project Tasks:** %d\n", projectTasks));

                // Add motivational message based on total tasks
                if (totalTasks >= 20) {
                    result.append("\n🎯 You're managing a lot of tasks! Great productivity!");
                } else if (totalTasks >= 10) {
                    result.append("\n💪 Good task management! Keep up the momentum!");
                } else if (totalTasks >= 5) {
                    result.append("\n📈 You're building good task management habits!");
                } else {
                    result.append("\n🌟 Great start! Consider adding more tasks to stay organized.");
                }

                result.append("\n\n💡 **Tip:** For detailed task analysis including completion rates and priorities, ");
                result.append("please use the web interface where you can see more detailed statistics.");

                return result.toString();

            } catch (Exception e) {
                log.warn("Could not retrieve task statistics for user {}: {}", userId, e.getMessage());
                return "❌ Sorry, I couldn't get your task statistics. This might be due to authentication requirements. " +
                       "Please use the web interface to view detailed statistics, or let me know if you'd like to create a new task!";
            }

        } catch (Exception e) {
            log.error("Error getting task statistics via AI Agent", e);
            return "❌ Sorry, I couldn't get your task statistics. Error: " + e.getMessage();
        }
    }

    /**
     * Helper method to detect priority from content using AI-based analysis
     */
    private TaskPriority detectPriorityFromContent(String content) {
        String lowerContent = content.toLowerCase();

        // Urgent keywords
        if (lowerContent.contains("urgent") || lowerContent.contains("asap") ||
            lowerContent.contains("emergency") || lowerContent.contains("critical") ||
            lowerContent.contains("khẩn cấp") || lowerContent.contains("gấp")) {
            return TaskPriority.HIGH; // Use HIGH instead of URGENT since URGENT may not exist
        }

        // High priority keywords
        if (lowerContent.contains("important") || lowerContent.contains("priority") ||
            lowerContent.contains("deadline") || lowerContent.contains("soon") ||
            lowerContent.contains("quan trọng") || lowerContent.contains("ưu tiên")) {
            return TaskPriority.HIGH;
        }

        // Low priority keywords
        if (lowerContent.contains("when possible") || lowerContent.contains("someday") ||
            lowerContent.contains("optional") || lowerContent.contains("khi rảnh") ||
            lowerContent.contains("không gấp")) {
            return TaskPriority.LOW;
        }

        // Default to MEDIUM
        return TaskPriority.MEDIUM;
    }
}
