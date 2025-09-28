package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.ProjectTaskDto.CreateProjectTaskRequestDto;
import com.example.taskmanagement_backend.dtos.ProjectTaskDto.UpdateProjectTaskRequestDto;
import com.example.taskmanagement_backend.dtos.ProjectTaskDto.ProjectTaskResponseDto;
import com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CreateCalendarEventRequestDto;
import com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CalendarEventResponseDto;
import com.example.taskmanagement_backend.entities.ProjectTask;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.enums.TaskPriority;
import com.example.taskmanagement_backend.services.ProjectTaskService;
import com.example.taskmanagement_backend.services.GoogleCalendarService;
import com.example.taskmanagement_backend.services.OnlineStatusService;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/project-tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = {"https://main.d2az19adxqfdf3.amplifyapp.com", "https://main.d4nz8d2yz1imm.amplifyapp.com", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class ProjectTaskController {

    private final ProjectTaskService projectTaskService;
    private final GoogleCalendarService googleCalendarService;
    private final UserJpaRepository userRepository;
    private final OnlineStatusService onlineStatusService;

    // ===== CRUD Operations =====

    /**
     * Create a new project task
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<ProjectTaskResponseDto> createProjectTask(
            @Valid @RequestBody CreateProjectTaskRequestDto createDto) {

        ProjectTask projectTask = convertToEntity(createDto);
        ProjectTask savedTask = projectTaskService.createProjectTask(projectTask);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(convertToResponseDto(savedTask));
    }

    /**
     * Get all project tasks with pagination and filtering
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Page<ProjectTaskResponseDto>> getAllProjectTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long creatorId) {

        Page<ProjectTask> tasks;

        if (projectId != null || status != null || priority != null || assigneeId != null || creatorId != null) {
            tasks = projectTaskService.getProjectTasksWithFilters(
                    projectId, status, priority, assigneeId, creatorId, page, size, sortBy, sortDir);
        } else {
            tasks = projectTaskService.getAllProjectTasks(page, size, sortBy, sortDir);
        }

        Page<ProjectTaskResponseDto> responseDtos = tasks.map(this::convertToResponseDto);
        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Get project task by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<ProjectTaskResponseDto> getProjectTaskById(@PathVariable Long id) {
        ProjectTask task = projectTaskService.getProjectTaskById(id);
        return ResponseEntity.ok(convertToResponseDto(task));
    }

    /**
     * Update project task
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<ProjectTaskResponseDto> updateProjectTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectTaskRequestDto updateDto) {

        ProjectTask updateData = convertToEntityForUpdate(updateDto);
        ProjectTask updatedTask = projectTaskService.updateProjectTask(id, updateData);

        return ResponseEntity.ok(convertToResponseDto(updatedTask));
    }

    /**
     * Update project task with file attachments
     * PUT /api/project-tasks/{id}/with-files
     */
    @PutMapping(value = "/{id}/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<ProjectTaskResponseDto> updateProjectTaskWithFiles(
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
        UpdateProjectTaskRequestDto dto = UpdateProjectTaskRequestDto.builder()
                .title(title)
                .description(description)
                .build();

        // Handle status if provided
        if (status != null && !status.isEmpty()) {
            try {
                dto.setStatus(TaskStatus.valueOf(status));
            } catch (IllegalArgumentException e) {
                // Handle invalid status value
            }
        }

        // Handle priority if provided
        if (priority != null && !priority.isEmpty()) {
            try {
                dto.setPriority(TaskPriority.valueOf(priority));
            } catch (IllegalArgumentException e) {
                // Handle invalid priority value
            }
        }

        // Handle comment if provided
        if (comment != null) {
            dto.setComment(comment);
        }

        // Handle file operations if present
        if (files != null && !files.isEmpty()) {
            // Upload new files and get their keys
            String uploadedFileKeys = projectTaskService.handleFileUploads(id, files);
            dto.setUrlFile(uploadedFileKeys);
        } else if (fileKeys != null && !fileKeys.isEmpty()) {
            // Use provided S3 file keys
            dto.setUrlFile(String.join(",", fileKeys));
        }

        // Handle file deletions
        if (filesToDelete != null && !filesToDelete.isEmpty()) {
            projectTaskService.handleFileDeletions(filesToDelete);
        }

        ProjectTask updateData = convertToEntityForUpdate(dto);
        ProjectTask updatedTask = projectTaskService.updateProjectTask(id, updateData);

        return ResponseEntity.ok(convertToResponseDto(updatedTask));
    }

    /**
     * Delete project task
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Void> deleteProjectTask(@PathVariable Long id) {
        projectTaskService.deleteProjectTask(id);
        return ResponseEntity.noContent().build();
    }

    // ===== Project-specific endpoints =====

    /**
     * Get tasks by project ID
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Page<ProjectTaskResponseDto>> getTasksByProject(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Page<ProjectTask> tasks = projectTaskService.getTasksByProjectId(projectId, page, size, sortBy, sortDir);
        Page<ProjectTaskResponseDto> responseDtos = tasks.map(this::convertToResponseDto);

        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Get project task statistics
     */
    @GetMapping("/project/{projectId}/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<ProjectTaskService.ProjectTaskStats> getProjectTaskStats(@PathVariable Long projectId) {
        ProjectTaskService.ProjectTaskStats stats = projectTaskService.getProjectTaskStats(projectId);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get overdue tasks by project
     */
    @GetMapping("/project/{projectId}/overdue")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<ProjectTaskResponseDto>> getOverdueTasksByProject(@PathVariable Long projectId) {
        List<ProjectTask> tasks = projectTaskService.getOverdueTasksByProject(projectId);
        List<ProjectTaskResponseDto> responseDtos = tasks.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    // ===== User-specific endpoints =====

    /**
     * Get current user's project tasks
     */
    @GetMapping("/my-tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Page<ProjectTaskResponseDto>> getCurrentUserProjectTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Page<ProjectTask> tasks = projectTaskService.getCurrentUserProjectTasks(page, size, sortBy, sortDir);
        Page<ProjectTaskResponseDto> responseDtos = tasks.map(this::convertToResponseDto);

        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Get user's project task statistics
     */
    @GetMapping("/users/{userId}/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER')")
    public ResponseEntity<ProjectTaskService.UserProjectTaskStats> getUserProjectTaskStats(@PathVariable Long userId) {
        ProjectTaskService.UserProjectTaskStats stats = projectTaskService.getUserProjectTaskStats(userId);
        return ResponseEntity.ok(stats);
    }

    // ===== Task operations =====

    /**
     * Get all users (members) in a specific project for task assignment
     * GET /api/project-tasks/project/{projectId}/members
     */
    @GetMapping("/project/{projectId}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<com.example.taskmanagement_backend.dtos.UserDto.UserLookupDto>> getProjectMembers(@PathVariable Long projectId) {
        try {
            List<User> projectMembers = projectTaskService.getProjectMembers(projectId);

            List<com.example.taskmanagement_backend.dtos.UserDto.UserLookupDto> memberDtos = projectMembers.stream()
                    .map(user -> {
                        // Get online status for each user
                        boolean isOnline = onlineStatusService.isUserOnline(user.getId());

                        return com.example.taskmanagement_backend.dtos.UserDto.UserLookupDto.builder()
                                .userId(user.getId())
                                .email(user.getEmail())
                                .firstName(user.getFirstName())
                                .lastName(user.getLastName())
                                .username(user.getUsername())
                                .avatarUrl(user.getAvatarUrl())
                                .exists(true)
                                .isOnline(isOnline)
                                .build();
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(memberDtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get available assignees for a specific project task
     * This includes project members who can be assigned to tasks
     * GET /api/project-tasks/{taskId}/available-assignees
     */
    @GetMapping("/{taskId}/available-assignees")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<com.example.taskmanagement_backend.dtos.UserDto.UserLookupDto>> getAvailableAssignees(@PathVariable Long taskId) {
        try {
            // Get the project task first to find the project
            ProjectTask task = projectTaskService.getProjectTaskById(taskId);
            Long projectId = task.getProject().getId();

            // Get all project members
            List<User> projectMembers = projectTaskService.getProjectMembers(projectId);

            List<com.example.taskmanagement_backend.dtos.UserDto.UserLookupDto> assigneeDtos = projectMembers.stream()
                    .map(user -> {
                        // Get online status for each user
                        boolean isOnline = onlineStatusService.isUserOnline(user.getId());

                        return com.example.taskmanagement_backend.dtos.UserDto.UserLookupDto.builder()
                                .userId(user.getId())
                                .email(user.getEmail())
                                .firstName(user.getFirstName())
                                .lastName(user.getLastName())
                                .username(user.getUsername())
                                .avatarUrl(user.getAvatarUrl())
                                .exists(true)
                                .isOnline(isOnline)
                                .build();
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(assigneeDtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Assign task to user (Enhanced with validation)
     */
    @PutMapping("/{taskId}/assign/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER')")
    public ResponseEntity<ProjectTaskResponseDto> assignTaskToUser(
            @PathVariable Long taskId,
            @PathVariable Long userId) {

        ProjectTask task = projectTaskService.assignTaskToUser(taskId, userId);
        return ResponseEntity.ok(convertToResponseDto(task));
    }

    /**
     * Update task progress
     */
    @PutMapping("/{taskId}/progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<ProjectTaskResponseDto> updateTaskProgress(
            @PathVariable Long taskId,
            @RequestParam Integer progressPercentage) {

        ProjectTask task = projectTaskService.updateTaskProgress(taskId, progressPercentage);
        return ResponseEntity.ok(convertToResponseDto(task));
    }

    /**
     * Get all tasks progress for a project
     * GET /api/project-tasks/project/{projectId}/progress
     */
    @GetMapping("/project/{projectId}/progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Map<String, Object>> getAllTasksProgress(@PathVariable Long projectId) {
        try {
            List<ProjectTask> tasks = projectTaskService.getTasksByProjectId(projectId);

            Map<String, Object> progressData = new HashMap<>();
            List<Map<String, Object>> taskProgressList = tasks.stream()
                .map(task -> {
                    Map<String, Object> taskProgress = new HashMap<>();
                    taskProgress.put("taskId", task.getId());
                    taskProgress.put("title", task.getTitle());
                    taskProgress.put("status", task.getStatus());
                    taskProgress.put("progressPercentage", task.getProgressPercentage() != null ? task.getProgressPercentage() : 0);
                    taskProgress.put("assigneeId", task.getAssignee() != null ? task.getAssignee().getId() : null);
                    taskProgress.put("assigneeName", task.getAssignee() != null ? getFullName(task.getAssignee()) : null);
                    taskProgress.put("deadline", task.getDeadline());
                    taskProgress.put("priority", task.getPriority());
                    taskProgress.put("isCompleted", task.getStatus() == TaskStatus.DONE);
                    taskProgress.put("updatedAt", task.getUpdatedAt());
                    return taskProgress;
                })
                .collect(Collectors.toList());

            // Calculate overall project progress
            int totalTasks = tasks.size();
            int completedTasks = (int) tasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
            double overallProgress = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

            progressData.put("projectId", projectId);
            progressData.put("totalTasks", totalTasks);
            progressData.put("completedTasks", completedTasks);
            progressData.put("overallProgressPercentage", Math.round(overallProgress * 100.0) / 100.0);
            progressData.put("tasks", taskProgressList);
            progressData.put("lastUpdated", LocalDateTime.now());

            return ResponseEntity.ok(progressData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to get tasks progress: " + e.getMessage()
            ));
        }
    }

    /**
     * Get all tasks progress for current user across all projects
     * GET /api/project-tasks/my-progress
     */
    @GetMapping("/my-progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Map<String, Object>> getMyTasksProgress() {
        try {
            List<ProjectTask> myTasks = projectTaskService.getCurrentUserProjectTasks();

            Map<String, Object> progressData = new HashMap<>();
            List<Map<String, Object>> taskProgressList = myTasks.stream()
                .map(task -> {
                    Map<String, Object> taskProgress = new HashMap<>();
                    taskProgress.put("taskId", task.getId());
                    taskProgress.put("title", task.getTitle());
                    taskProgress.put("status", task.getStatus());
                    taskProgress.put("progressPercentage", task.getProgressPercentage() != null ? task.getProgressPercentage() : 0);
                    taskProgress.put("projectId", task.getProject() != null ? task.getProject().getId() : null);
                    taskProgress.put("projectName", task.getProject() != null ? task.getProject().getName() : null);
                    taskProgress.put("deadline", task.getDeadline());
                    taskProgress.put("priority", task.getPriority());
                    taskProgress.put("isCompleted", task.getStatus() == TaskStatus.COMPLETED);
                    taskProgress.put("updatedAt", task.getUpdatedAt());
                    return taskProgress;
                })
                .collect(Collectors.toList());

            // Group tasks by project
            Map<Long, List<Map<String, Object>>> tasksByProject = taskProgressList.stream()
                .filter(task -> task.get("projectId") != null)
                .collect(Collectors.groupingBy(task -> (Long) task.get("projectId")));

            // Calculate progress by project
            List<Map<String, Object>> projectProgressList = tasksByProject.entrySet().stream()
                .map(entry -> {
                    Long projectId = entry.getKey();
                    List<Map<String, Object>> projectTasks = entry.getValue();

                    int totalTasks = projectTasks.size();
                    int completedTasks = (int) projectTasks.stream()
                        .filter(task -> (Boolean) task.get("isCompleted"))
                        .count();
                    double projectProgress = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

                    Map<String, Object> projectProgressData = new HashMap<>();
                    projectProgressData.put("projectId", projectId);
                    projectProgressData.put("projectName", projectTasks.get(0).get("projectName"));
                    projectProgressData.put("totalTasks", totalTasks);
                    projectProgressData.put("completedTasks", completedTasks);
                    projectProgressData.put("progressPercentage", Math.round(projectProgress * 100.0) / 100.0);

                    return projectProgressData;
                })
                .collect(Collectors.toList());

            // Calculate overall user progress
            int totalTasks = myTasks.size();
            int completedTasks = (int) myTasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
            double overallProgress = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

            progressData.put("totalTasks", totalTasks);
            progressData.put("completedTasks", completedTasks);
            progressData.put("overallProgressPercentage", Math.round(overallProgress * 100.0) / 100.0);
            progressData.put("tasks", taskProgressList);
            progressData.put("projectsProgress", projectProgressList);
            progressData.put("lastUpdated", LocalDateTime.now());

            return ResponseEntity.ok(progressData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to get my tasks progress: " + e.getMessage()
            ));
        }
    }

    /**
     * Mark task as completed (sets progress to 100% and status to COMPLETED)
     * PUT /api/project-tasks/{taskId}/complete
     */
    @PutMapping("/{taskId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<ProjectTaskResponseDto> markTaskAsCompleted(@PathVariable Long taskId) {
        try {
            ProjectTask task = projectTaskService.getProjectTaskById(taskId);

            // Update task to completed status
            task.setStatus(TaskStatus.COMPLETED);
            task.setProgressPercentage(100);
            task.setUpdatedAt(LocalDateTime.now());

            // If task has actual hours not set and estimated hours exist, set actual hours to estimated
            if (task.getActualHours() == null && task.getEstimatedHours() != null) {
                task.setActualHours(task.getEstimatedHours());
            }

            ProjectTask updatedTask = projectTaskService.updateProjectTask(taskId, task);

            return ResponseEntity.ok(convertToResponseDto(updatedTask));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(convertToResponseDto(null));
        }
    }

    /**
     * Reopen task (sets status back to IN_PROGRESS)
     * PUT /api/project-tasks/{taskId}/reopen
     */
    @PutMapping("/{taskId}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<ProjectTaskResponseDto> reopenTask(@PathVariable Long taskId) {
        try {
            ProjectTask task = projectTaskService.getProjectTaskById(taskId);

            // Update task to in progress status
            task.setStatus(TaskStatus.IN_PROGRESS);
            // Keep current progress percentage or set to 90% if it was 100%
            if (task.getProgressPercentage() != null && task.getProgressPercentage() == 100) {
                task.setProgressPercentage(90);
            }
            task.setUpdatedAt(LocalDateTime.now());

            ProjectTask updatedTask = projectTaskService.updateProjectTask(taskId, task);

            return ResponseEntity.ok(convertToResponseDto(updatedTask));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(convertToResponseDto(null));
        }
    }

    /**
     * Bulk update tasks progress
     * PUT /api/project-tasks/bulk-progress
     */
    @PutMapping("/bulk-progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Map<String, Object>> bulkUpdateTasksProgress(
            @RequestBody List<Map<String, Object>> tasksProgress) {
        try {
            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int errorCount = 0;

            for (Map<String, Object> taskProgress : tasksProgress) {
                try {
                    Long taskId = Long.valueOf(taskProgress.get("taskId").toString());
                    Integer progressPercentage = Integer.valueOf(taskProgress.get("progressPercentage").toString());

                    ProjectTask updatedTask = projectTaskService.updateTaskProgress(taskId, progressPercentage);

                    Map<String, Object> result = new HashMap<>();
                    result.put("taskId", taskId);
                    result.put("success", true);
                    result.put("progressPercentage", updatedTask.getProgressPercentage());
                    result.put("status", updatedTask.getStatus());
                    results.add(result);
                    successCount++;

                } catch (Exception e) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("taskId", taskProgress.get("taskId"));
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    results.add(result);
                    errorCount++;
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("successCount", successCount);
            response.put("errorCount", errorCount);
            response.put("totalProcessed", tasksProgress.size());
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to bulk update tasks progress: " + e.getMessage()
            ));
        }
    }

    // ===== GOOGLE CALENDAR INTEGRATION =====

    /**
     * Create a Google Calendar event for a project task
     * Google Calendar Service - Updated cho backend mới
     * Backend tự động lấy Google OAuth2 token từ user đã đăng nhập
     * POST /api/project-tasks/{taskId}/calendar-event
     */
    @PostMapping("/{taskId}/calendar-event")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<CalendarEventResponseDto> createCalendarEvent(
            @PathVariable Long taskId,
            @RequestBody CreateCalendarEventRequestDto dto,
            Authentication authentication) {

        System.out.println("📅 Creating Google Calendar event for project task: " + taskId);
        dto.setTaskId(taskId);

        try {
            // Tự động lấy user email từ authentication
            String userEmail = authentication.getName();
            System.out.println("✅ Using auto-retrieved Google token for user: " + userEmail);

            // ✅ FIXED: Use the ProjectTask-specific method instead of generic one
            CalendarEventResponseDto response = googleCalendarService.createProjectTaskEventWithUserToken(dto, userEmail);
            System.out.println("✅ Calendar event created successfully for project task: " + taskId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("❌ Failed to create calendar event: " + e.getMessage());
            return ResponseEntity.badRequest().body(CalendarEventResponseDto.builder()
                .message("Backend không thể tạo Google Calendar event. Có thể bạn chưa kết nối Google Calendar cho Project Tasks: " + e.getMessage())
                .build());
        }
    }

    /**
     * Update a Google Calendar event for a project task
     * Backend tự động lấy Google OAuth2 token từ user đã đăng nhập
     * PUT /api/project-tasks/{taskId}/calendar-event
     */
    @PutMapping("/{taskId}/calendar-event")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<CalendarEventResponseDto> updateCalendarEvent(
            @PathVariable Long taskId,
            @RequestBody CreateCalendarEventRequestDto dto,
            Authentication authentication) {

        try {
            // Find the task to get the event ID
            ProjectTask task = projectTaskService.getProjectTaskById(taskId);

            if (task.getGoogleCalendarEventId() == null) {
                return ResponseEntity.badRequest().body(CalendarEventResponseDto.builder()
                    .message("Task chưa có Google Calendar event để cập nhật")
                    .build());
            }

            dto.setTaskId(taskId);

            // Tự động lấy user email từ authentication
            String userEmail = authentication.getName();
            String accessToken = googleCalendarService.getGoogleAccessTokenForUser(userEmail);
            dto.setAccessToken(accessToken);

            // Use the actual GoogleCalendarService
            CalendarEventResponseDto updatedEvent = googleCalendarService.updateEvent(task.getGoogleCalendarEventId(), dto);

            return ResponseEntity.ok(updatedEvent);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(CalendarEventResponseDto.builder()
                .message("Failed to update calendar event: " + e.getMessage())
                .build());
        }
    }

    /**
     * Update a Google Calendar event with specific event ID
     * PUT /api/project-tasks/{taskId}/calendar-event/{eventId}
     */
    @PutMapping("/{taskId}/calendar-event/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<CalendarEventResponseDto> updateCalendarEventWithId(
            @PathVariable Long taskId,
            @PathVariable String eventId,
            @RequestBody CreateCalendarEventRequestDto dto,
            Authentication authentication) {

        System.out.println("📝 Updating Google Calendar event: " + eventId + " for project task: " + taskId);
        dto.setTaskId(taskId);

        try {
            // Tự động lấy user email từ authentication
            String userEmail = authentication.getName();
            String accessToken = googleCalendarService.getGoogleAccessTokenForUser(userEmail);
            dto.setAccessToken(accessToken);

            CalendarEventResponseDto response = googleCalendarService.updateEvent(eventId, dto);
            System.out.println("✅ Calendar event updated successfully: " + eventId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("❌ Failed to update calendar event: " + e.getMessage());
            return ResponseEntity.badRequest().body(CalendarEventResponseDto.builder()
                .message("Backend không thể cập nhật Google Calendar event: " + e.getMessage())
                .build());
        }
    }

    /**
     * Delete a Google Calendar event for a project task
     * Backend tự động lấy Google OAuth2 token từ user đã đăng nhập
     * DELETE /api/project-tasks/{taskId}/calendar-event
     */
    @DeleteMapping("/{taskId}/calendar-event")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Map<String, Object>> deleteCalendarEvent(
            @PathVariable Long taskId,
            Authentication authentication) {

        try {
            // Find the task to get the event ID
            ProjectTask task = projectTaskService.getProjectTaskById(taskId);

            if (task.getGoogleCalendarEventId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Task chưa có Google Calendar event"));
            }

            // Tự động lấy user email từ authentication
            String userEmail = authentication.getName();

            // Use the actual GoogleCalendarService
            googleCalendarService.deleteEvent(task.getGoogleCalendarEventId());

            // Clear the calendar event data from the task
            task.setGoogleCalendarEventId(null);
            task.setGoogleMeetLink(null);
            task.setGoogleCalendarEventUrl(null);
            task.setIsSyncedToCalendar(false);
            task.setCalendarSyncedAt(null);
            projectTaskService.updateProjectTask(taskId, task);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Sự kiện Google Calendar đã được xóa thành công"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Backend không thể xóa Google Calendar event: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete a Google Calendar event with specific event ID
     * DELETE /api/project-tasks/{taskId}/calendar-event/{eventId}
     */
    @DeleteMapping("/{taskId}/calendar-event/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Map<String, String>> deleteCalendarEventWithId(
            @PathVariable Long taskId,
            @PathVariable String eventId,
            Authentication authentication) {

        System.out.println("🗑️ Deleting Google Calendar event: " + eventId + " for project task: " + taskId);

        try {
            googleCalendarService.deleteEvent(eventId);

            // Clear the calendar event data from the task if this is the task's event
            ProjectTask task = projectTaskService.getProjectTaskById(taskId);
            if (eventId.equals(task.getGoogleCalendarEventId())) {
                task.setGoogleCalendarEventId(null);
                task.setGoogleMeetLink(null);
                task.setGoogleCalendarEventUrl(null);
                task.setIsSyncedToCalendar(false);
                task.setCalendarSyncedAt(null);
                projectTaskService.updateProjectTask(taskId, task);
            }

            Map<String, String> response = new HashMap<>();
            response.put("message", "Sự kiện Google Calendar đã được xóa thành công");
            response.put("eventId", eventId);
            response.put("taskId", taskId.toString());

            System.out.println("✅ Calendar event deleted successfully: " + eventId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("❌ Failed to delete calendar event: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Backend không thể xóa Google Calendar event: " + e.getMessage()
            ));
        }
    }

    /**
     * Get information about a Google Calendar event for a project task
     * GET /api/project-tasks/{taskId}/calendar-event
     */
    @GetMapping("/{taskId}/calendar-event")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Map<String, Object>> getCalendarEvent(@PathVariable Long taskId) {
        try {
            ProjectTask task = projectTaskService.getProjectTaskById(taskId);

            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("hasCalendarEvent", task.getGoogleCalendarEventId() != null);

            if (task.getGoogleCalendarEventId() != null) {
                response.put("eventId", task.getGoogleCalendarEventId());
                response.put("googleMeetLink", task.getGoogleMeetLink());
                response.put("googleCalendarEventUrl", task.getGoogleCalendarEventUrl());
                response.put("isSyncedToCalendar", task.getIsSyncedToCalendar());
                response.put("calendarSyncedAt", task.getCalendarSyncedAt());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to get calendar event: " + e.getMessage()
            ));
        }
    }

    /**
     * Get calendar status for a project task
     * GET /api/project-tasks/{taskId}/calendar-status
     */
    @GetMapping("/{taskId}/calendar-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Map<String, Object>> getCalendarStatus(@PathVariable Long taskId) {
        System.out.println("🔍 Checking calendar status for project task: " + taskId);

        try {
            ProjectTask task = projectTaskService.getProjectTaskById(taskId);

            Map<String, Object> status = new HashMap<>();
            status.put("taskId", taskId);
            status.put("hasCalendarEvent", task.getGoogleCalendarEventId() != null);
            status.put("eventId", task.getGoogleCalendarEventId());
            status.put("taskTitle", task.getTitle());
            status.put("taskDeadline", task.getDeadline());
            status.put("isSyncedToCalendar", task.getIsSyncedToCalendar());
            status.put("calendarSyncedAt", task.getCalendarSyncedAt());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to get calendar status: " + e.getMessage()
            ));
        }
    }

    /**
     * Create a quick meeting for a project task
     * Backend tự động lấy Google OAuth2 token từ user đã đăng nhập
     * POST /api/project-tasks/{taskId}/quick-meeting
     */
    @PostMapping("/{taskId}/quick-meeting")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<CalendarEventResponseDto> createQuickMeeting(
            @PathVariable Long taskId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) Integer durationMinutes,
            @RequestParam(required = false) List<String> attendeeEmails,
            Authentication authentication) {

        System.out.println("🤝 Creating quick meeting for project task: " + taskId);

        try {
            // Get the task details
            ProjectTask task = projectTaskService.getProjectTaskById(taskId);

            // Format the startTime if it's a simple date format (YYYY-MM-DD)
            String formattedStartTime = startTime;
            if (startTime != null && startTime.matches("\\d{4}-\\d{2}-\\d{2}")) {
                // Convert date-only format to ISO datetime format with current time
                LocalDate date = LocalDate.parse(startTime);
                LocalTime now = LocalTime.now();
                LocalDateTime dateTime = LocalDateTime.of(date, now);
                formattedStartTime = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            // Create quick meeting request
            CreateCalendarEventRequestDto dto = CreateCalendarEventRequestDto.builder()
                    .taskId(taskId)
                    .customTitle(title != null ? title : "Meeting: " + task.getTitle())
                    .customDescription(description != null ? description : task.getDescription())
                    .customStartTime(formattedStartTime)
                    .durationMinutes(durationMinutes != null ? durationMinutes : 60)
                    .attendeeEmails(attendeeEmails)
                    .createMeet(true)
                    .build();

            // Tự động lấy user email từ authentication
            String userEmail = authentication.getName();
            // ✅ FIXED: Use ProjectTask-specific method
            CalendarEventResponseDto response = googleCalendarService.createProjectTaskEventWithUserToken(dto, userEmail);
            System.out.println("✅ Quick meeting created successfully for project task: " + taskId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("❌ Failed to create quick meeting: " + e.getMessage());
            return ResponseEntity.badRequest().body(CalendarEventResponseDto.builder()
                .message("Backend không thể tạo quick meeting cho Project Task: " + e.getMessage())
                .build());
        }
    }

    /**
     * Schedule a reminder for a project task
     * POST /api/project-tasks/{taskId}/schedule-reminder
     */
    @PostMapping("/{taskId}/schedule-reminder")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<CalendarEventResponseDto> scheduleTaskReminder(
            @PathVariable Long taskId,
            @RequestParam String reminderTime, // Format: "2025-08-30T09:00:00"
            @RequestParam(defaultValue = "30") Integer reminderMinutes,
            Authentication authentication) {

        System.out.println("⏰ Creating reminder for project task: " + taskId + " at " + reminderTime);

        try {
            CreateCalendarEventRequestDto reminderDto = CreateCalendarEventRequestDto.builder()
                    .taskId(taskId)
                    .customTitle("⏰ Nhắc nhở Project Task: ")
                    .customStartTime(reminderTime)
                    .durationMinutes(reminderMinutes)
                    .isReminder(true)
                    .createMeet(false)
                    .eventColor("3") // Blue color for reminders
                    .build();

            // Tự động lấy user email từ authentication
            String userEmail = authentication.getName();
            // ✅ FIXED: Use ProjectTask-specific method
            CalendarEventResponseDto response = googleCalendarService.createProjectTaskEventWithUserToken(reminderDto, userEmail);
            System.out.println("✅ Reminder scheduled successfully for project task: " + taskId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("❌ Failed to schedule reminder: " + e.getMessage());
            return ResponseEntity.badRequest().body(CalendarEventResponseDto.builder()
                .message("Backend không thể tạo reminder cho Project Task: " + e.getMessage())
                .build());
        }
    }

    // ===== Helper Methods =====

    private ProjectTask convertToEntity(CreateProjectTaskRequestDto dto) {
        ProjectTask.ProjectTaskBuilder builder = ProjectTask.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .startDate(dto.getStartDate())
                .deadline(dto.getDeadline())
                .estimatedHours(dto.getEstimatedHours())
                .actualHours(dto.getActualHours())
                .progressPercentage(dto.getProgressPercentage());

        // Set project
        if (dto.getProjectId() != null) {
            builder.project(com.example.taskmanagement_backend.entities.Project.builder()
                    .id(dto.getProjectId())
                    .build());
        }

        // Set assignee
        if (dto.getAssigneeId() != null) {
            builder.assignee(User.builder().id(dto.getAssigneeId()).build());
        }

        // Set additional assignees
        if (dto.getAdditionalAssigneeIds() != null && !dto.getAdditionalAssigneeIds().isEmpty()) {
            List<User> additionalAssignees = dto.getAdditionalAssigneeIds().stream()
                    .map(id -> User.builder().id(id).build())
                    .collect(Collectors.toList());
            builder.additionalAssignees(additionalAssignees);
        }

        // Set parent task
        if (dto.getParentTaskId() != null) {
            builder.parentTask(ProjectTask.builder().id(dto.getParentTaskId()).build());
        }

        return builder.build();
    }

    private ProjectTask convertToEntityForUpdate(UpdateProjectTaskRequestDto dto) {
        ProjectTask.ProjectTaskBuilder builder = ProjectTask.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(dto.getStatus())
                .priority(dto.getPriority())
                .startDate(dto.getStartDate())
                .deadline(dto.getDeadline())
                .estimatedHours(dto.getEstimatedHours())
                .actualHours(dto.getActualHours())
                .progressPercentage(dto.getProgressPercentage());

        // Set assignee
        if (dto.getAssigneeId() != null) {
            builder.assignee(User.builder().id(dto.getAssigneeId()).build());
        }

        // Set additional assignees
        if (dto.getAdditionalAssigneeIds() != null) {
            List<User> additionalAssignees = dto.getAdditionalAssigneeIds().stream()
                    .map(id -> User.builder().id(id).build())
                    .collect(Collectors.toList());
            builder.additionalAssignees(additionalAssignees);
        }

        // Set parent task
        if (dto.getParentTaskId() != null) {
            builder.parentTask(ProjectTask.builder().id(dto.getParentTaskId()).build());
        }

        return builder.build();
    }

    private ProjectTaskResponseDto convertToResponseDto(ProjectTask task) {
        ProjectTaskResponseDto.ProjectTaskResponseDtoBuilder builder = ProjectTaskResponseDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .startDate(task.getStartDate())
                .deadline(task.getDeadline())
                .estimatedHours(task.getEstimatedHours())
                .actualHours(task.getActualHours())
                .progressPercentage(task.getProgressPercentage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt());

        // Project information
        if (task.getProject() != null) {
            builder.projectId(task.getProject().getId())
                   .projectName(task.getProject().getName());
        }

        // Creator information
        if (task.getCreator() != null) {
            builder.creatorId(task.getCreator().getId())
                   .creatorName(getFullName(task.getCreator()))
                   .creatorEmail(task.getCreator().getEmail());
        }

        // Assignee information
        if (task.getAssignee() != null) {
            builder.assigneeId(task.getAssignee().getId())
                   .assigneeName(getFullName(task.getAssignee()))
                   .assigneeEmail(task.getAssignee().getEmail());
        }

        // Additional assignees
        if (task.getAdditionalAssignees() != null && !task.getAdditionalAssignees().isEmpty()) {
            List<ProjectTaskResponseDto.AssigneeDto> additionalAssignees = task.getAdditionalAssignees().stream()
                    .map(user -> ProjectTaskResponseDto.AssigneeDto.builder()
                            .id(user.getId())
                            .name(getFullName(user))
                            .email(user.getEmail())
                            .build())
                    .collect(Collectors.toList());
            builder.additionalAssignees(additionalAssignees);
        }

        // Parent task information
        if (task.getParentTask() != null) {
            builder.parentTaskId(task.getParentTask().getId())
                   .parentTaskTitle(task.getParentTask().getTitle());
        }

        // Subtasks information
        if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
            List<ProjectTaskResponseDto.SubtaskDto> subtasks = task.getSubtasks().stream()
                    .map(subtask -> ProjectTaskResponseDto.SubtaskDto.builder()
                            .id(subtask.getId())
                            .title(subtask.getTitle())
                            .status(subtask.getStatus())
                            .progressPercentage(subtask.getProgressPercentage())
                            .deadline(subtask.getDeadline())
                            .build())
                    .collect(Collectors.toList());
            builder.subtasks(subtasks)
                   .subtaskCount(subtasks.size());
        }

        return builder.build();
    }

    private String getFullName(User user) {
        if (user.getUserProfile() != null) {
            String firstName = user.getUserProfile().getFirstName();
            String lastName = user.getUserProfile().getLastName();
            if (firstName != null || lastName != null) {
                return String.format("%s %s",
                        firstName != null ? firstName : "",
                        lastName != null ? lastName : "").trim();
            }
        }
        return user.getEmail();
    }
}

