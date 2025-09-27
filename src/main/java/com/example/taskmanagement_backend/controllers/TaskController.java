package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.annotations.RequiresPremium;
import com.example.taskmanagement_backend.dtos.TaskDto.CreateTaskRequestDto;
import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.UpdateTaskRequestDto;
import com.example.taskmanagement_backend.dtos.TaskDto.MyTaskSummaryDto;
import com.example.taskmanagement_backend.dtos.ProjectTaskDto.ProjectTaskResponseDto;
import com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto;
import com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CreateCalendarEventRequestDto;
import com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CalendarEventResponseDto;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.services.TaskServiceCached;
import com.example.taskmanagement_backend.services.TaskActivityService;
import com.example.taskmanagement_backend.services.TaskAttachmentService;
import com.example.taskmanagement_backend.services.GoogleCalendarService;
import com.example.taskmanagement_backend.services.DashboardService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@Slf4j
public class TaskController {

    @Autowired
    private TaskServiceCached taskService;

    @Autowired
    private TaskActivityService taskActivityService;

    @Autowired
    private TaskAttachmentService taskAttachmentService;

    @Autowired
    private GoogleCalendarService googleCalendarService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserJpaRepository userRepository;

    /**
     * ✏️ CREATE operations - Premium feature (write operation)
     */
    @PostMapping("/my-tasks")
    @RequiresPremium(message = "Upgrade to Premium to create new tasks",
                    feature = "task-creation")
    public ResponseEntity<TaskResponseDto> createMyTask(@Valid @RequestBody CreateTaskRequestDto dto) {
        TaskResponseDto task = taskService.createTask(dto);
        return ResponseEntity.ok(task);
    }

    @PostMapping
    @RequiresPremium(message = "Upgrade to Premium to create new tasks",
                    feature = "task-creation")
    public ResponseEntity<TaskResponseDto> createTask(@Valid @RequestBody CreateTaskRequestDto dto) {
        TaskResponseDto task = taskService.createTask(dto);
        return ResponseEntity.ok(task);
    }

    /**
     * 📖 READ operations - Allow with graceful degradation (show upgrade banner)
     */
    @GetMapping
    @RequiresPremium(message = "Upgrade to Premium for advanced task filtering and unlimited access",
                    feature = "task-viewing",
                    allowReadOnly = true)
    public ResponseEntity<List<TaskResponseDto>> getAllTasks(@RequestParam(required = false) String status) {
        List<TaskResponseDto> tasks = taskService.getAllTasks(status);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/my-tasks")
    @RequiresPremium(message = "Upgrade to Premium for unlimited task access and advanced features",
                    feature = "my-tasks-viewing",
                    allowReadOnly = true)
    public ResponseEntity<Page<TaskResponseDto>> getMyTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(taskService.getMyTasks(page, size, sortBy, sortDir));
    }

    @GetMapping("/my-tasks/summary")
    @RequiresPremium(message = "Task summary view requires Premium subscription",
                    feature = "task-summary",
                    allowReadOnly = true)
    public ResponseEntity<Page<MyTaskSummaryDto>> getMyTasksSummary(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(taskService.getMyTasksSummary(page, size, sortBy, sortDir));
    }

    @GetMapping("/my-tasks/stats")
    @RequiresPremium(message = "Task statistics and analytics require Premium subscription",
                    feature = "task-statistics",
                    allowReadOnly = true)
    public ResponseEntity<Map<String, Object>> getMyTasksStats() {
        return ResponseEntity.ok(taskService.getMyTasksStats());
    }

    /**
     * ✏️ UPDATE operations - Premium feature (write operation)
     */
    @PutMapping("/my-tasks/{id}")
    @RequiresPremium(message = "Upgrade to Premium to edit and update tasks",
                    feature = "task-editing")
    public ResponseEntity<TaskResponseDto> updateMyTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequestDto dto) {
        return ResponseEntity.ok(taskService.updateTask(id, dto));
    }

    // ✅ Enhanced PUT endpoint with file upload - Premium feature
    @PutMapping(value = "/my-tasks/{id}/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPremium(message = "File uploads and advanced task editing require Premium subscription",
                    feature = "task-file-upload")
    public ResponseEntity<TaskResponseDto> updateMyTaskWithFiles(
            @PathVariable Long id,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "comment", required = false) String comment,
            @RequestParam(value = "fileKeys", required = false) List<String> fileKeys,
            @RequestParam(value = "filesToDelete", required = false) List<String> filesToDelete,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        // Build standard DTO for existing functionality
        UpdateTaskRequestDto dto = UpdateTaskRequestDto.builder()
                .title(title)
                .description(description)
                .status(status)
                .priority(priority)
                .comment(comment)
                .build();

        // Handle file operations if present
        if (files != null && !files.isEmpty()) {
            // Upload new files and get their keys
            String uploadedFileKeys = taskService.handleFileUploads(id, files);
            dto.setUrlFile(uploadedFileKeys);
        } else if (fileKeys != null && !fileKeys.isEmpty()) {
            // Use provided S3 file keys
            dto.setUrlFile(String.join(",", fileKeys));
        }

        // Handle file deletions
        if (filesToDelete != null && !filesToDelete.isEmpty()) {
            taskService.handleFileDeletions(filesToDelete);
        }

        return ResponseEntity.ok(taskService.updateTask(id, dto));
    }

    /**
     * 🗑️ DELETE operations - Premium feature (write operation)
     */
    @DeleteMapping("/my-tasks/{id}")
    @RequiresPremium(message = "Upgrade to Premium to delete tasks",
                    feature = "task-deletion")
    public ResponseEntity<String> deleteMyTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok("My task deleted successfully.");
    }

    /**
     * 📖 Individual task viewing - Allow with graceful degradation
     */
    @GetMapping("/{id}")
    @RequiresPremium(message = "Upgrade to Premium for full task details and features",
                    feature = "task-details",
                    allowReadOnly = true)
    public ResponseEntity<TaskResponseDto> getTaskById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    /**
     * ✏️ General task updates - Premium feature
     */
    @PutMapping("/{id}")
    @RequiresPremium(message = "Upgrade to Premium to edit tasks",
                    feature = "task-editing")
    public ResponseEntity<TaskResponseDto> updateTask(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequestDto dto) {
        return ResponseEntity.ok(taskService.updateTask(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequiresPremium(message = "Upgrade to Premium to delete tasks",
                    feature = "task-deletion")
    public ResponseEntity<String> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok("Task deleted successfully.");
    }

    // ========== PROJECT & TEAM TASK APIs - Premium features ==========

    @GetMapping("/project/{projectId}")
    @RequiresPremium(message = "Project task management requires Premium subscription",
                    feature = "project-tasks",
                    allowReadOnly = true)
    public ResponseEntity<List<TaskResponseDto>> getTasksByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(taskService.getTasksByProjectId(projectId));
    }

    @GetMapping("/team/{teamId}")
    @RequiresPremium(message = "Team collaboration features require Premium subscription",
                    feature = "team-tasks",
                    allowReadOnly = true)
    public ResponseEntity<List<TaskResponseDto>> getTasksByTeam(@PathVariable Long teamId) {
        return ResponseEntity.ok(taskService.getTasksByTeamId(teamId));
    }

    @GetMapping("/team/{teamId}/all-projects")
    @RequiresPremium(message = "Advanced team project views require Premium subscription",
                    feature = "team-project-overview",
                    allowReadOnly = true)
    public ResponseEntity<List<ProjectTaskResponseDto>> getAllTasksFromAllProjectsOfTeam(@PathVariable Long teamId) {
        return ResponseEntity.ok(taskService.getAllTasksFromAllProjectsOfTeam(teamId));
    }

    @GetMapping("/my-tasks/combined")
    @RequiresPremium(message = "Combined task views require Premium subscription",
                    feature = "combined-task-view",
                    allowReadOnly = true)
    public ResponseEntity<Page<TaskResponseDto>> getMyCombinedTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(taskService.getMyCombinedTasks(page, size, sortBy, sortDir));
    }

    @GetMapping("/my-tasks/combined-summary")
    @RequiresPremium(message = "Advanced task summaries require Premium subscription",
                    feature = "combined-task-summary",
                    allowReadOnly = true)
    public ResponseEntity<Page<MyTaskSummaryDto>> getMyCombinedTasksSummary(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(taskService.getMyCombinedTasksSummary(page, size, sortBy, sortDir));
    }

    // ========== TASK ACTIVITY APIs - Premium features ==========

    @GetMapping("/{taskId}/activities")
    @RequiresPremium(message = "Task activity tracking requires Premium subscription",
                    feature = "task-activities",
                    allowReadOnly = true)
    public ResponseEntity<List<TaskActivityResponseDto>> getTaskActivities(@PathVariable Long taskId) {
        List<TaskActivityResponseDto> activities = taskActivityService.getTaskActivities(taskId);
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/{taskId}/activities/paginated")
    @RequiresPremium(message = "Advanced activity views require Premium subscription",
                    feature = "task-activities-paginated",
                    allowReadOnly = true)
    public ResponseEntity<Page<TaskActivityResponseDto>> getTaskActivitiesPaginated(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<TaskActivityResponseDto> activities = taskActivityService.getTaskActivities(taskId, page, size);
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/{taskId}/activities/recent")
    @RequiresPremium(message = "Recent activity tracking requires Premium subscription",
                    feature = "recent-activities",
                    allowReadOnly = true)
    public ResponseEntity<List<TaskActivityResponseDto>> getRecentTaskActivities(@PathVariable Long taskId) {
        List<TaskActivityResponseDto> recentActivities = taskActivityService.getRecentTaskActivities(taskId);
        return ResponseEntity.ok(recentActivities);
    }

    @GetMapping("/{taskId}/activities/count")
    @RequiresPremium(message = "Activity statistics require Premium subscription",
                    feature = "activity-count",
                    allowReadOnly = true)
    public ResponseEntity<Map<String, Long>> getTaskActivitiesCount(@PathVariable Long taskId) {
        Long count = taskActivityService.getTaskActivitiesCount(taskId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // ========== TASK ATTACHMENTS APIs - Premium features ==========

    @GetMapping("/{taskId}/attachments")
    @RequiresPremium(message = "File attachments require Premium subscription",
                    feature = "task-attachments",
                    allowReadOnly = true)
    public ResponseEntity<List<com.example.taskmanagement_backend.dtos.TaskAttachmentDto.TaskAttachmentResponseDto>> getTaskAttachments(@PathVariable Long taskId) {
        try {
            List<com.example.taskmanagement_backend.dtos.TaskAttachmentDto.TaskAttachmentResponseDto> attachments =
                taskAttachmentService.getTaskAttachments(taskId);
            return ResponseEntity.ok(attachments);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{taskId}/attachments/stats")
    @RequiresPremium(message = "File statistics require Premium subscription",
                    feature = "attachment-stats",
                    allowReadOnly = true)
    public ResponseEntity<com.example.taskmanagement_backend.services.TaskAttachmentService.TaskAttachmentStatsDto> getTaskAttachmentStats(@PathVariable Long taskId) {
        try {
            com.example.taskmanagement_backend.services.TaskAttachmentService.TaskAttachmentStatsDto stats =
                taskAttachmentService.getAttachmentStats(taskId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/attachments/{attachmentId}/download-url")
    @RequiresPremium(message = "File downloads require Premium subscription",
                    feature = "file-download")
    public ResponseEntity<Map<String, String>> generateNewDownloadUrl(@PathVariable Long attachmentId) {
        try {
            String newDownloadUrl = taskAttachmentService.generateNewDownloadUrl(attachmentId);
            return ResponseEntity.ok(Map.of("downloadUrl", newDownloadUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @RequiresPremium(message = "File management requires Premium subscription",
                    feature = "file-deletion")
    public ResponseEntity<Map<String, Object>> deleteAttachment(@PathVariable Long attachmentId) {
        try {
            boolean deleted = taskAttachmentService.deleteAttachment(attachmentId);
            return ResponseEntity.ok(Map.of(
                "success", deleted,
                "message", "Attachment deleted successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    // ========== GOOGLE CALENDAR INTEGRATION - Premium features ==========

    @PostMapping("/my-tasks/{taskId}/calendar-event")
    @RequiresPremium(message = "Google Calendar integration requires Premium subscription",
                    feature = "calendar-integration")
    public ResponseEntity<CalendarEventResponseDto> createCalendarEventForMyTask(
            @PathVariable Long taskId,
            @RequestBody CreateCalendarEventRequestDto dto,
            Authentication authentication) {

        dto.setTaskId(taskId); // Ensure task ID is set

        // Tự động lấy user email từ authentication
        String userEmail = authentication.getName();

        CalendarEventResponseDto response = googleCalendarService.createEventWithUserToken(dto, userEmail);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/my-tasks/{taskId}/calendar-event")
    @RequiresPremium(message = "Calendar event editing requires Premium subscription",
                    feature = "calendar-editing")
    public ResponseEntity<CalendarEventResponseDto> updateMyTaskCalendarEvent(
            @PathVariable Long taskId,
            @RequestBody CreateCalendarEventRequestDto dto,
            Authentication authentication) {

        // Tìm task để lấy eventId
        TaskResponseDto task = taskService.getTaskById(taskId);
        if (task.getGoogleCalendarEventId() == null) {
            return ResponseEntity.badRequest().build();
        }

        dto.setTaskId(taskId);

        // Tự động lấy user email từ authentication
        String userEmail = authentication.getName();
        String accessToken = googleCalendarService.getGoogleAccessTokenForUser(userEmail);
        dto.setAccessToken(accessToken);

        CalendarEventResponseDto updatedEvent = googleCalendarService.updateEvent(task.getGoogleCalendarEventId(), dto);
        return ResponseEntity.ok(updatedEvent);
    }

    @DeleteMapping("/my-tasks/{taskId}/calendar-event")
    @RequiresPremium(message = "Calendar management requires Premium subscription",
                    feature = "calendar-management")
    public ResponseEntity<Map<String, String>> deleteMyTaskCalendarEvent(
            @PathVariable Long taskId,
            Authentication authentication) {

        // Tìm task để lấy eventId
        TaskResponseDto task = taskService.getTaskById(taskId);
        if (task.getGoogleCalendarEventId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Task không có sự kiện calendar"));
        }

        googleCalendarService.deleteEvent(task.getGoogleCalendarEventId());

        // Xóa eventId khỏi task
        taskService.clearCalendarEventId(taskId);

        return ResponseEntity.ok(Map.of("message", "Sự kiện calendar đã được xóa thành công"));
    }

    @GetMapping("/my-tasks/{taskId}/calendar-event")
    @RequiresPremium(message = "Calendar integration requires Premium subscription",
                    feature = "calendar-integration",
                    allowReadOnly = true)
    public ResponseEntity<Map<String, Object>> getMyTaskCalendarEvent(@PathVariable Long taskId) {
        TaskResponseDto task = taskService.getTaskById(taskId);

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("hasCalendarEvent", task.getGoogleCalendarEventId() != null);

        if (task.getGoogleCalendarEventId() != null) {
            response.put("eventId", task.getGoogleCalendarEventId());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/my-tasks/{taskId}/quick-meeting")
    @RequiresPremium(message = "Quick meeting creation requires Premium subscription",
                    feature = "quick-meetings")
    public ResponseEntity<CalendarEventResponseDto> createQuickMeetingForTask(
            @PathVariable Long taskId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) Integer durationMinutes,
            @RequestParam(required = false) List<String> attendeeEmails,
            Authentication authentication) {

        // Build quick meeting request
        CreateCalendarEventRequestDto meetingDto = CreateCalendarEventRequestDto.builder()
                .taskId(taskId)
                .customTitle(title)
                .customDescription(description)
                .customStartTime(startTime)
                .durationMinutes(durationMinutes != null ? durationMinutes : 60)
                .attendeeEmails(attendeeEmails)
                .createMeet(true)
                .build();

        // Tự động lấy user email từ authentication
        String userEmail = authentication.getName();
        CalendarEventResponseDto response = googleCalendarService.createEventWithUserToken(meetingDto, userEmail);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/my-tasks/{taskId}/schedule-reminder")
    @RequiresPremium(message = "Task reminders require Premium subscription",
                    feature = "task-reminders")
    public ResponseEntity<CalendarEventResponseDto> scheduleTaskReminder(
            @PathVariable Long taskId,
            @RequestParam String reminderTime,
            @RequestParam(defaultValue = "30") Integer reminderMinutes,
            Authentication authentication) {

        CreateCalendarEventRequestDto reminderDto = CreateCalendarEventRequestDto.builder()
                .taskId(taskId)
                .customTitle("⏰ Nhắc nhở: ")
                .customStartTime(reminderTime)
                .durationMinutes(reminderMinutes)
                .isReminder(true)
                .createMeet(false)
                .build();

        // Tự động lấy user email từ authentication
        String userEmail = authentication.getName();
        CalendarEventResponseDto response = googleCalendarService.createEventWithUserToken(reminderDto, userEmail);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/my-tasks/{taskId}/calendar-event/{eventId}")
    @RequiresPremium(message = "Advanced calendar management requires Premium subscription",
                    feature = "advanced-calendar-management")
    public ResponseEntity<CalendarEventResponseDto> updateCalendarEventForMyTask(
            @PathVariable Long taskId,
            @PathVariable String eventId,
            @RequestBody CreateCalendarEventRequestDto dto) {

        dto.setTaskId(taskId);

        CalendarEventResponseDto response = googleCalendarService.updateEvent(eventId, dto);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/my-tasks/{taskId}/calendar-event/{eventId}")
    @RequiresPremium(message = "Calendar event management requires Premium subscription",
                    feature = "calendar-event-management")
    public ResponseEntity<Map<String, String>> deleteCalendarEventForMyTask(
            @PathVariable Long taskId,
            @PathVariable String eventId) {

        googleCalendarService.deleteEvent(eventId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Sự kiện Google Calendar đã được xóa thành công");
        response.put("eventId", eventId);
        response.put("taskId", taskId.toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-tasks/{taskId}/calendar-status")
    @RequiresPremium(message = "Calendar status tracking requires Premium subscription",
                    feature = "calendar-status",
                    allowReadOnly = true)
    public ResponseEntity<Map<String, Object>> getCalendarStatusForTask(@PathVariable Long taskId) {

        TaskResponseDto task = taskService.getTaskById(taskId);

        Map<String, Object> status = new HashMap<>();
        status.put("taskId", taskId);
        status.put("hasCalendarEvent", task.getGoogleCalendarEventId() != null);
        status.put("eventId", task.getGoogleCalendarEventId());
        status.put("taskTitle", task.getTitle());
        status.put("taskDeadline", task.getDeadline());

        return ResponseEntity.ok(status);
    }

    @PostMapping("/my-tasks/bulk-calendar-sync")
    @RequiresPremium(message = "Bulk calendar synchronization requires Premium subscription",
                    feature = "bulk-calendar-sync")
    public ResponseEntity<Map<String, Object>> bulkSyncTasksToCalendar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "false") Boolean includeCompleted) {

        System.out.println("📅 Bulk syncing tasks to Google Calendar");

        // TODO: Implement bulk sync logic
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Bulk calendar sync initiated");
        response.put("status", "in_progress");

        return ResponseEntity.ok(response);
    }

    // ========== DASHBOARD APIs - Premium analytics features ==========

    @GetMapping("/dashboard/overview")
    @RequiresPremium(message = "Advanced dashboard analytics require Premium subscription",
                    feature = "dashboard-analytics",
                    allowReadOnly = true)
    public ResponseEntity<com.example.taskmanagement_backend.dtos.DashboardDto.DashboardOverviewResponseDto> getDashboardOverview() {
        log.info("📊 [TaskController] Getting dashboard overview");
        com.example.taskmanagement_backend.dtos.DashboardDto.DashboardOverviewResponseDto dashboard =
                dashboardService.getDashboardOverview();
        return ResponseEntity.ok(dashboard);
    }

    @DeleteMapping("/dashboard/cache")
    @RequiresPremium(message = "Cache management requires Premium subscription",
                    feature = "cache-management")
    public ResponseEntity<Map<String, Object>> clearDashboardCache(Authentication authentication) {
        String userEmail = authentication.getName();
        // Get user ID to clear cache
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

        dashboardService.invalidateDashboardCache(currentUser.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Dashboard cache cleared successfully");
        response.put("userId", currentUser.getId());
        response.put("timestamp", LocalDateTime.now());

        log.info("🗑️ [TaskController] Dashboard cache cleared for user: {}", currentUser.getId());
        return ResponseEntity.ok(response);
    }
}
