package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.TaskDto.CreateTaskRequestDto;
import com.example.taskmanagement_backend.dtos.TaskDto.CreateTaskWithEmailRequestDto;
import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.UpdateTaskRequestDto;
import com.example.taskmanagement_backend.dtos.TaskDto.MyTaskSummaryDto;
import com.example.taskmanagement_backend.dtos.ProjectTaskDto.ProjectTaskResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Cached Task Service - Delegates to TaskService with caching layer
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TaskServiceCached {

    private final TaskService taskService; // Delegate to the original TaskService
    private final DashboardService dashboardService; // For cache invalidation

    // ========== TASK CRUD OPERATIONS ==========

    @Transactional
    public TaskResponseDto createTask(CreateTaskRequestDto dto) {
        TaskResponseDto result = taskService.createTask(dto);

        // Invalidate dashboard cache for task creator
        dashboardService.invalidateDashboardCache(result.getCreatorId());

        return result;
    }

    @Transactional
    public TaskResponseDto createTaskWithEmail(CreateTaskWithEmailRequestDto dto) {
        TaskResponseDto result = taskService.createTaskWithEmail(dto);

        // Invalidate dashboard cache for task creator
        dashboardService.invalidateDashboardCache(result.getCreatorId());

        return result;
    }

    public TaskResponseDto getTaskById(Long id) {
        return taskService.getTaskById(id);
    }

    public List<TaskResponseDto> getAllTasks() {
        return taskService.getAllTasks();
    }

    /**
     * Get all tasks filtered by status
     * @param status Optional filter for task status
     * @return List of tasks matching the status filter
     */
    public List<TaskResponseDto> getAllTasks(String status) {
        if (status == null || status.isEmpty()) {
            return getAllTasks();
        }
        // Delegate to the taskService with the status filter
        return taskService.getAllTasks().stream()
                .filter(task -> status.equalsIgnoreCase(task.getStatus()))
                .toList();
    }

    @Transactional
    public TaskResponseDto updateTask(Long id, UpdateTaskRequestDto dto) {
        // Get task before update to know which user's cache to invalidate
        TaskResponseDto originalTask = taskService.getTaskById(id);
        TaskResponseDto result = taskService.updateTask(id, dto);

        // Invalidate dashboard cache for task creator and potentially assignees
        dashboardService.invalidateDashboardCache(result.getCreatorId());

        // If task has assignees, invalidate their cache too
        if (result.getAssignedToIds() != null && !result.getAssignedToIds().isEmpty()) {
            result.getAssignedToIds().forEach(assigneeId -> {
                dashboardService.invalidateDashboardCache(assigneeId);
            });
        }

        return result;
    }

    @Transactional
    public void deleteTask(Long id) {
        // Get task before deletion to know which user's cache to invalidate
        TaskResponseDto task = taskService.getTaskById(id);
        taskService.deleteTask(id);

        // Invalidate dashboard cache for task creator
        dashboardService.invalidateDashboardCache(task.getCreatorId());

        // If task has assignees, invalidate their cache too
        if (task.getAssignedToIds() != null && !task.getAssignedToIds().isEmpty()) {
            task.getAssignedToIds().forEach(assigneeId -> {
                dashboardService.invalidateDashboardCache(assigneeId);
            });
        }
    }

    // ========== MY TASKS OPERATIONS ==========

    public Page<TaskResponseDto> getMyTasks(int page, int size, String sortBy, String sortDir) {
        return taskService.getMyTasks(page, size, sortBy, sortDir);
    }

    public Page<MyTaskSummaryDto> getMyTasksSummary(int page, int size, String sortBy, String sortDir) {
        return taskService.getMyTasksSummary(page, size, sortBy, sortDir);
    }

    public Map<String, Object> getMyTasksStats() {
        return taskService.getMyTasksStats();
    }

    // ========== PROJECT & TEAM TASKS ==========

    public List<TaskResponseDto> getTasksByProjectId(Long projectId) {
        return taskService.getTasksByProjectId(projectId);
    }

    public List<TaskResponseDto> getTasksByTeamId(Long teamId) {
        return taskService.getTasksByTeamId(teamId);
    }

    public List<ProjectTaskResponseDto> getAllTasksFromAllProjectsOfTeam(Long teamId) {
        return taskService.getAllTasksFromAllProjectsOfTeam(teamId);
    }

    // ========== COMBINED TASKS ==========

    public Page<TaskResponseDto> getMyCombinedTasks(int page, int size, String sortBy, String sortDir) {
        return taskService.getMyCombinedTasks(page, size, sortBy, sortDir);
    }

    public Page<MyTaskSummaryDto> getMyCombinedTasksSummary(int page, int size, String sortBy, String sortDir) {
        return taskService.getMyCombinedTasksSummary(page, size, sortBy, sortDir);
    }

    // ========== FILE UPLOAD OPERATIONS (NEW) ==========

    /**
     * Handle multiple file uploads for a task using TransferManager
     * Returns comma-separated file keys for storage in task.urlFile
     */
    public String handleFileUploads(Long taskId, List<MultipartFile> files) {
        return taskService.handleFileUploads(taskId, files);
    }

    /**
     * Handle file deletions from S3
     */
    public void handleFileDeletions(List<String> fileKeys) {
        taskService.handleFileDeletions(fileKeys);
    }

    /**
     * Parse file URLs from task.urlFile field and generate download URLs
     */
    public List<String> generateDownloadUrls(String urlFile) {
        return taskService.generateDownloadUrls(urlFile);
    }

    // ========== GOOGLE CALENDAR OPERATIONS ==========

    /**
     * Xóa Google Calendar Event ID khỏi task
     */
    @Transactional
    public void clearCalendarEventId(Long taskId) {
        taskService.clearCalendarEventId(taskId);
    }

    /**
     * ✅ NEW: Tạo task với option Google Calendar event
     */
    @Transactional
    public TaskResponseDto createTaskWithCalendarOption(CreateTaskRequestDto dto) {
        TaskResponseDto result = taskService.createTaskWithCalendarOption(dto);

        // Invalidate dashboard cache for task creator
        dashboardService.invalidateDashboardCache(result.getCreatorId());

        return result;
    }
}
