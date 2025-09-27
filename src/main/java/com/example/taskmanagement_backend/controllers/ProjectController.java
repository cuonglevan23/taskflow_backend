package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.ProjectDto.CreateProjectRequestDto;
import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectResponseDto;
import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectDashboardResponseDto;
import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectTimelineResponseDto;
import com.example.taskmanagement_backend.dtos.ProjectDto.UpdateProjectRequestDto;
import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectAllInOneDashboardResponseDto;
import com.example.taskmanagement_backend.dtos.ProcessDto.ProjectProgressResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
import com.example.taskmanagement_backend.services.ProjectService;
import com.example.taskmanagement_backend.services.ProjectProgressService;
import com.example.taskmanagement_backend.services.ProjectDashboardService;
import com.example.taskmanagement_backend.services.ProjectTimelineService;
import com.example.taskmanagement_backend.services.ProjectAllInOneDashboardService;
import com.example.taskmanagement_backend.services.TaskService;
import com.example.taskmanagement_backend.services.AuditLogService;
import com.example.taskmanagement_backend.services.UserService;
import com.example.taskmanagement_backend.dtos.AuditLogDto.CreateAuditLogRequestDto;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    @Autowired
    private ProjectService projectService;
    
    @Autowired
    private ProjectProgressService projectProgressService;
    
    @Autowired
    private TaskService taskService;

    @Autowired
    private ProjectDashboardService projectDashboardService;

    @Autowired
    private ProjectTimelineService projectTimelineService;

    @Autowired
    private ProjectAllInOneDashboardService projectAllInOneDashboardService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private UserService userService;

    @GetMapping
    public List<ProjectResponseDto> getAllProjects() {
        return projectService.getAllProjects();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponseDto> getProjectById(@Valid @PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    @PostMapping
    public ResponseEntity<ProjectResponseDto> createProject(@Valid @RequestBody CreateProjectRequestDto projectDto) {
        try {
            Long userId = getCurrentUserId();
            log.info("🆕 [ProjectController] Creating new project - Name: {}, User: {}", projectDto.getName(), userId);

            ProjectResponseDto createdProject = projectService.createProject(projectDto);

            // Log project creation
            logAuditEvent(userId, "PROJECT_CREATED",
                "Created new project: " + createdProject.getName() + " (ID: " + createdProject.getId() + ")");

            log.info("✅ [ProjectController] Successfully created project {} for user {}",
                    createdProject.getId(), userId);

            return ResponseEntity.ok(createdProject);
        } catch (Exception e) {
            log.error("❌ [ProjectController] Error creating project: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponseDto> updateProject(@PathVariable Long id, @Valid @RequestBody UpdateProjectRequestDto projectDto) {
        try {
            Long userId = getCurrentUserId();
            log.info("🔄 [ProjectController] Updating project with ID: {} - Name: {}, StartDate: {}, EndDate: {}",
                    id, projectDto.getName(), projectDto.getStartDate(), projectDto.getEndDate());

            // Get original project info for audit log
            ProjectResponseDto originalProject = projectService.getProjectById(id);

            // Validate dates if both are provided
            if (projectDto.getStartDate() != null && projectDto.getEndDate() != null) {
                if (projectDto.getStartDate().isAfter(projectDto.getEndDate())) {
                    log.warn("⚠️ [ProjectController] Invalid date range for project {}: startDate {} is after endDate {}",
                            id, projectDto.getStartDate(), projectDto.getEndDate());
                    return ResponseEntity.badRequest().build();
                }
            }

            ProjectResponseDto updatedProject = projectService.updateProject(id, projectDto);

            // Log project update with changes
            String changes = buildUpdateChangesDescription(originalProject, updatedProject);
            logAuditEvent(userId, "PROJECT_UPDATED",
                "Updated project: " + updatedProject.getName() + " (ID: " + id + ") - " + changes);

            log.info("✅ [ProjectController] Successfully updated project {} - New name: {}, StartDate: {}, EndDate: {}",
                    id, updatedProject.getName(), updatedProject.getStartDate(), updatedProject.getEndDate());

            return ResponseEntity.ok(updatedProject);
        } catch (RuntimeException e) {
            log.error("❌ [ProjectController] Error updating project {}: {}", id, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ProjectResponseDto> deleteProject(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();

            // Get project info before deletion for audit log
            ProjectResponseDto project = projectService.getProjectById(id);

            log.info("🗑️ [ProjectController] Deleting project {} - Name: {}, User: {}",
                    id, project.getName(), userId);

            projectService.deleteProjectById(id);

            // Log project deletion
            logAuditEvent(userId, "PROJECT_DELETED",
                "Deleted project: " + project.getName() + " (ID: " + id + ")");

            log.info("✅ [ProjectController] Successfully deleted project {} for user {}", id, userId);

            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("❌ [ProjectController] Error deleting project {}: {}", id, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [ProjectController] Unexpected error deleting project {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Project Progress Endpoints
    @GetMapping("/{id}/progress")
    public ResponseEntity<ProjectProgressResponseDto> getProjectProgress(@PathVariable Long id) {
        try {
            // ✅ FIX: Thêm authorization check trước khi truy cập progress
            ProjectResponseDto project = projectService.getProjectById(id); // Đã có checkProjectPermission bên trong

            ProjectProgressResponseDto progress = projectProgressService.getProjectProgress(id);
            return ResponseEntity.ok(progress);
        } catch (RuntimeException e) {
            log.error("❌ [ProjectController] Error getting project progress for project {}: {}", id, e.getMessage());

            // Check if it's authorization error
            if (e.getMessage().contains("don't have permission")) {
                return ResponseEntity.status(403).build(); // Return 403 Forbidden for authorization errors
            }

            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/progress")
    public ResponseEntity<ProjectProgressResponseDto> refreshProjectProgress(@PathVariable Long id) {
        try {
            log.info("🔄 [ProjectController] Starting refresh progress for projectId={}", id);

            // ✅ FIX: Lấy thông tin project để biết teamId
            ProjectResponseDto project = projectService.getProjectById(id);
            Long teamId = project.getTeamId();

            log.info("🔍 [ProjectController] Project info - projectId={}, teamId={}, isPersonal={}",
                    id, teamId, project.isPersonal());

            // ✅ FIX: Gọi refreshAllProgressData thay vì chỉ getProjectProgress
            if (teamId != null) {
                log.info("🚀 [ProjectController] Calling refreshAllProgressData for teamId={}, projectId={}", teamId, id);
                projectProgressService.refreshAllProgressData(teamId, id);
                log.info("✅ [ProjectController] Refreshed all progress for teamId={}, projectId={}", teamId, id);
            } else {
                // Project cá nhân - chỉ refresh project progress
                log.info("👤 [ProjectController] Personal project detected - only refreshing project progress");
                projectProgressService.refreshProjectProgressData(id);
                log.info("✅ [ProjectController] Refreshed project progress for personal project={}", id);
            }

            // Trả về progress đã được refresh
            ProjectProgressResponseDto progress = projectProgressService.getProjectProgress(id);
            log.info("📊 [ProjectController] Final progress for project {}: {}/{} tasks ({}%)",
                    id, progress.getCompletedTasks(), progress.getTotalTasks(), progress.getCompletionPercentage());

            return ResponseEntity.ok(progress);
        } catch (RuntimeException e) {
            log.error("❌ [ProjectController] Error refreshing progress for project {}: {}", id, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    // Project Tasks Endpoint
    @GetMapping("/{id}/tasks")
    public ResponseEntity<List<TaskResponseDto>> getProjectTasks(@PathVariable Long id) {
        try {
            List<TaskResponseDto> tasks = taskService.getTasksByProjectId(id);
            return ResponseEntity.ok(tasks);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 📊 Project Dashboard Overview - Tổng hợp thống kê project với Redis cache
     * GET /api/projects/{id}/dashboard/overview
     */
    @GetMapping("/{id}/dashboard/overview")
    public ResponseEntity<ProjectDashboardResponseDto> getProjectDashboardOverview(@PathVariable Long id) {
        log.info("📊 [ProjectController] Getting dashboard overview for project ID: {}", id);
        try {
            ProjectDashboardResponseDto dashboard = projectDashboardService.getProjectDashboard(id);
            return ResponseEntity.ok(dashboard);
        } catch (RuntimeException e) {
            log.error("❌ [ProjectController] Error getting dashboard for project {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 🗑️ Clear Project Dashboard Cache - Force refresh cache
     * DELETE /api/projects/{id}/dashboard/cache
     */
    @DeleteMapping("/{id}/dashboard/cache")
    public ResponseEntity<Map<String, Object>> clearProjectDashboardCache(@PathVariable Long id) {
        try {
            log.info("🗑️ [ProjectController] Clearing dashboard cache for project: {}", id);

            // Validate project exists
            ProjectResponseDto project = projectService.getProjectById(id);

            // Clear cache
            projectDashboardService.invalidateProjectDashboardCache(id);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Project dashboard cache cleared successfully");
            response.put("projectId", id);
            response.put("projectName", project.getName());
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("❌ [ProjectController] Error clearing dashboard cache for project {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    // Project Timeline Endpoint
    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<ProjectTimelineResponseDto>> getProjectTimeline(@PathVariable Long id) {
        try {
            List<ProjectTimelineResponseDto> timeline = projectTimelineService.getProjectTimeline(id);
            return ResponseEntity.ok(timeline);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 🎯 Project All-in-One Dashboard - Single endpoint for complete project dashboard
     * GET /api/projects/{projectId}/dashboard
     *
     * This endpoint provides everything the frontend needs to render a complete project dashboard:
     * - Project stats (completion rate, task counts, team members, etc.)
     * - Task breakdown by status, priority, and assignee
     * - Upcoming tasks, deadlines, and overdue items
     * - Progress trends and velocity metrics
     * - Team information and workload distribution
     */
    @GetMapping("/{projectId}/dashboard")
    public ResponseEntity<ProjectAllInOneDashboardResponseDto> getProjectDashboard(@PathVariable Long projectId) {
        log.info("🎯 [ProjectController] Getting comprehensive dashboard for project ID: {}", projectId);
        try {
            ProjectAllInOneDashboardResponseDto dashboard = projectAllInOneDashboardService.getProjectAllInOneDashboard(projectId);
            log.info("✅ [ProjectController] Successfully generated dashboard for project {} with {} total tasks",
                    projectId, dashboard.getStats().getTotalTasks());
            return ResponseEntity.ok(dashboard);
        } catch (RuntimeException e) {
            log.error("❌ [ProjectController] Error getting comprehensive dashboard for project {}: {}", projectId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 📊 Project All-in-One Dashboard - Tổng hợp toàn bộ thông tin project
     * GET /api/projects/{id}/dashboard/all-in-one
     */
    @GetMapping("/{id}/dashboard/all-in-one")
    public ResponseEntity<ProjectAllInOneDashboardResponseDto> getProjectAllInOneDashboard(@PathVariable Long id) {
        log.info("📊 [ProjectController] Getting all-in-one dashboard for project ID: {}", id);
        try {
            ProjectAllInOneDashboardResponseDto dashboard = projectAllInOneDashboardService.getProjectAllInOneDashboard(id);
            return ResponseEntity.ok(dashboard);
        } catch (RuntimeException e) {
            log.error("❌ [ProjectController] Error getting all-in-one dashboard for project {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    // ===== UTILITY METHODS =====

    /**
     * Get current user ID from authentication context
     */
    private Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();

            Long userId = userService.getUserIdByEmailDirect(email);
            if (userId == null) {
                throw new RuntimeException("User not found for email: " + email);
            }
            return userId;
        } catch (Exception e) {
            log.error("❌ [ProjectController] Error getting current user ID: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get current user ID", e);
        }
    }

    /**
     * Log audit event for project activities
     */
    private void logAuditEvent(Long userId, String action, String description) {
        try {
            CreateAuditLogRequestDto auditLog = CreateAuditLogRequestDto.builder()
                    .userId(userId)
                    .action(action + " - " + description)
                    .build();

            auditLogService.create(auditLog);

            log.info("📝 [ProjectController] Audit log created - User: {}, Action: {}",
                    userId, action);
        } catch (Exception e) {
            log.error("❌ [ProjectController] Error logging audit event: {}", e.getMessage(), e);
        }
    }

    /**
     * Build description of changes for project update audit log
     */
    private String buildUpdateChangesDescription(ProjectResponseDto original, ProjectResponseDto updated) {
        StringBuilder changes = new StringBuilder();

        if (!original.getName().equals(updated.getName())) {
            changes.append("Name: '").append(original.getName()).append("' -> '").append(updated.getName()).append("'; ");
        }

        if (original.getDescription() != null && !original.getDescription().equals(updated.getDescription())) {
            changes.append("Description changed; ");
        } else if (original.getDescription() == null && updated.getDescription() != null) {
            changes.append("Description added; ");
        }

        if (original.getStartDate() != null && !original.getStartDate().equals(updated.getStartDate())) {
            changes.append("Start date: ").append(original.getStartDate()).append(" -> ").append(updated.getStartDate()).append("; ");
        } else if (original.getStartDate() == null && updated.getStartDate() != null) {
            changes.append("Start date set to: ").append(updated.getStartDate()).append("; ");
        }

        if (original.getEndDate() != null && !original.getEndDate().equals(updated.getEndDate())) {
            changes.append("End date: ").append(original.getEndDate()).append(" -> ").append(updated.getEndDate()).append("; ");
        } else if (original.getEndDate() == null && updated.getEndDate() != null) {
            changes.append("End date set to: ").append(updated.getEndDate()).append("; ");
        }

        return changes.length() > 0 ? changes.toString() : "Minor updates";
    }
}
