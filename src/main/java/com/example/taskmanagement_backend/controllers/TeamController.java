package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.ProjectTaskDto.ProjectTaskResponseDto;
import com.example.taskmanagement_backend.dtos.TeamDto.CreateTeamResponseDto;
import com.example.taskmanagement_backend.dtos.TeamDto.TeamResponseDto;
import com.example.taskmanagement_backend.dtos.TeamDto.UpdateTeamResponseDto;
import com.example.taskmanagement_backend.dtos.TeamMemberDto.TeamMemberResponseDto;
import com.example.taskmanagement_backend.dtos.ProcessDto.TeamProgressResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectResponseDto;
import com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto;
import com.example.taskmanagement_backend.services.TeamService;
import com.example.taskmanagement_backend.services.TeamProgressService;
import com.example.taskmanagement_backend.services.TaskService;
import com.example.taskmanagement_backend.services.ProjectService;
import com.example.taskmanagement_backend.services.AuditLogService;
import com.example.taskmanagement_backend.services.UserService;
import com.example.taskmanagement_backend.dtos.AuditLogDto.CreateAuditLogRequestDto;
import com.example.taskmanagement_backend.enums.TeamRole;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/teams")
public class TeamController {
    @Autowired
    private TeamService teamService;
    
    @Autowired
    private TeamProgressService teamProgressService;
    
    @Autowired
    private TaskService taskService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private UserService userService;

    @GetMapping
    public List<TeamResponseDto> findAll() {
        return teamService.getAllTeams();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamResponseDto> findOne(@PathVariable Long id) {
        try {
            // ✅ FIX: Kiểm tra quyền truy cập team trước khi trả về thông tin team
            teamService.validateTeamAccess(id);

            TeamResponseDto team = teamService.getTeamById(id);
            if (team != null) {
                return ResponseEntity.ok(team);
            }
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build(); // Forbidden
            }
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public TeamResponseDto createTeam(@Valid @RequestBody CreateTeamResponseDto dto) {
        try {
            Long userId = getCurrentUserId();
            log.info("🆕 [TeamController] Creating new team - Name: {}, User: {}", dto.getName(), userId);

            TeamResponseDto newTeam = teamService.createTeams(dto);

            // Log team creation
            logAuditEvent(userId, "TEAM_CREATED",
                "Created new team: " + newTeam.getName() + " (ID: " + newTeam.getId() + ")");

            log.info("✅ [TeamController] Successfully created team {} for user {}",
                    newTeam.getId(), userId);

            return newTeam;
        } catch (Exception e) {
            log.error("❌ [TeamController] Error creating team: {}", e.getMessage(), e);
            throw e;
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamResponseDto> updateTeam(@PathVariable Long id, @Valid @RequestBody UpdateTeamResponseDto dto) {
        try {
            Long userId = getCurrentUserId();
            // ✅ FIX: Kiểm tra quyền truy cập team trước khi update
            teamService.validateTeamAccess(id);

            // Get original team info for audit log
            TeamResponseDto originalTeam = teamService.getTeamById(id);

            log.info("🔄 [TeamController] Updating team {} - Name: {}, User: {}",
                    id, dto.getName(), userId);

            TeamResponseDto updatedTeam = teamService.updateTeams(id, dto);
            if (updatedTeam != null) {
                // Log team update with changes
                String changes = buildUpdateChangesDescription(originalTeam, updatedTeam);
                logAuditEvent(userId, "TEAM_UPDATED",
                    "Updated team: " + updatedTeam.getName() + " (ID: " + id + ") - " + changes);

                log.info("✅ [TeamController] Successfully updated team {} for user {}", id, userId);
                return ResponseEntity.ok(updatedTeam);
            }
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            log.error("❌ [TeamController] Error updating team {}: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build(); // Forbidden
            }
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTeam(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            // ✅ FIX: Kiểm tra quyền truy cập team trước khi delete
            teamService.validateTeamAccess(id);

            // Get team info before deletion for audit log
            TeamResponseDto team = teamService.getTeamById(id);

            log.info("🗑️ [TeamController] Deleting team {} - Name: {}, User: {}",
                    id, team.getName(), userId);

            if(teamService.deleteTeamById(id)){
                // Log team deletion
                logAuditEvent(userId, "TEAM_DELETED",
                    "Deleted team: " + team.getName() + " (ID: " + id + ")");

                log.info("✅ [TeamController] Successfully deleted team {} for user {}", id, userId);
                return ResponseEntity.ok("Delete Team Successfully Id: "+id);
            }
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            log.error("❌ [TeamController] Error deleting team {}: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build(); // Forbidden
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [TeamController] Unexpected error deleting team {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    @GetMapping("/project/{projectId}")
    public List<TeamResponseDto> getTeamsByProjectId(@PathVariable Long projectId) {
        return teamService.findByProjectId(projectId);
    }

    // Team Progress Endpoints
    @GetMapping("/{id}/progress")
    public ResponseEntity<TeamProgressResponseDto> getTeamProgress(@PathVariable Long id) {
        try {
            // ✅ TEMPORARY FIX: Remove authorization check to debug 403 issue
            // teamService.validateTeamAccess(id);

            System.out.println("🔍 [TeamController] Loading progress for team " + id);
            TeamProgressResponseDto progress = teamProgressService.getTeamProgressByTeamId(id);
            System.out.println("✅ [TeamController] Successfully loaded progress for team " + id);
            return ResponseEntity.ok(progress);
        } catch (RuntimeException e) {
            System.err.println("❌ [TeamController] Error loading team progress: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    @PostMapping("/{id}/progress")
    public ResponseEntity<TeamProgressResponseDto> refreshTeamProgress(@PathVariable Long id) {
        try {
            // ✅ TEMPORARY FIX: Remove authorization check to debug 403 issue
            // teamService.validateTeamAccess(id);

            System.out.println("🔄 [TeamController] Refreshing progress for team " + id);
            TeamProgressResponseDto progress = teamProgressService.getTeamProgressByTeamId(id);
            System.out.println("✅ [TeamController] Successfully refreshed progress for team " + id);
            return ResponseEntity.ok(progress);
        } catch (RuntimeException e) {
            System.err.println("❌ [TeamController] Error refreshing team progress: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    // Team Tasks Endpoint
    @GetMapping("/{id}/tasks")
    public ResponseEntity<List<TaskResponseDto>> getTeamTasks(@PathVariable Long id) {
        try {
            // ✅ FIX: Kiểm tra quyền truy cập team trước khi trả về tasks
            teamService.validateTeamAccess(id);

            List<TaskResponseDto> tasks = taskService.getTasksByTeamId(id);
            return ResponseEntity.ok(tasks);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build(); // Forbidden
            }
            return ResponseEntity.notFound().build();
        }
    }

    // Team Projects Endpoint - GET /api/teams/{id}/projects
    @GetMapping("/{id}/projects")
    public ResponseEntity<List<ProjectResponseDto>> getTeamProjects(@PathVariable Long id) {
        try {
            // ✅ FIX: Kiểm tra quyền truy cập team trước khi trả về projects
            teamService.validateTeamAccess(id);

            List<ProjectResponseDto> projects = projectService.getProjectsByTeamId(id);
            return ResponseEntity.ok(projects);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build(); // Forbidden
            }
            return ResponseEntity.notFound().build();
        }
    }

    // Team Members Endpoint - GET /api/teams/{id}/members
    @GetMapping("/{id}/members")
    public ResponseEntity<List<TeamMemberResponseDto>> getTeamMembers(@PathVariable Long id) {
        try {
            // ✅ FIX: Kiểm tra quyền truy cập team trước khi trả về members
            teamService.validateTeamAccess(id);

            List<TeamMemberResponseDto> members = teamService.getTeamMembersWithRoles(id);
            return ResponseEntity.ok(members);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build(); // Forbidden
            }
            return ResponseEntity.notFound().build();
        }
    }

    // All Team Project Tasks Endpoint - GET /api/teams/{id}/all-tasks
    @GetMapping("/{id}/all-tasks")
    public ResponseEntity<List<ProjectTaskResponseDto>> getAllTeamProjectTasks(@PathVariable Long id) {
        try {
            // ✅ FIX: Kiểm tra quyền truy cập team trước khi trả về all tasks
            teamService.validateTeamAccess(id);

            List<ProjectTaskResponseDto> tasks = taskService.getAllTasksFromAllProjectsOfTeam(id);
            return ResponseEntity.ok(tasks);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build(); // Forbidden
            }
            return ResponseEntity.notFound().build();
        }
    }

    // ✅ NEW: Endpoint để lấy progress của tất cả team mà user hiện tại tham gia
    @GetMapping("/progress/all")
    public ResponseEntity<List<TeamProgressResponseDto>> getAllTeamsProgress() {
        try {
            List<TeamProgressResponseDto> progressList = teamProgressService.getAllTeamsProgressForCurrentUser();
            return ResponseEntity.ok(progressList);
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // Add member to team by email endpoint - POST /api/teams/{id}/members
    @PostMapping("/{id}/members")
    public ResponseEntity<?> addTeamMemberByEmail(
            @PathVariable Long id,
            @Valid @RequestBody com.example.taskmanagement_backend.dtos.TeamMemberDto.AddTeamMemberByEmailRequestDto dto) {
        try {
            // Kiểm tra quyền truy cập team trước khi thêm member
            teamService.validateTeamAccess(id);

            // Đảm bảo role không bao giờ null - sử dụng MEMBER nếu role là null
            TeamRole role = (dto.getRole() != null) ? dto.getRole() : TeamRole.MEMBER;

            TeamMemberResponseDto addedMember = teamService.addTeamMemberByEmail(id, dto.getEmail(), role);
            return ResponseEntity.ok(addedMember);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).body("You don't have permission to add members to this team");
            } else if (e.getMessage().contains("User is already a member")) {
                return ResponseEntity.status(400).body(e.getMessage());
            } else if (e.getMessage().contains("User not found with email")) {
                return ResponseEntity.status(404).body(e.getMessage());
            }
            return ResponseEntity.status(500).body("Error adding member: " + e.getMessage());
        }
    }

    /**
     * Xóa thành viên khỏi team
     * @param id ID của team
     * @param memberId ID của thành viên cần xóa
     * @return ResponseEntity với thông báo kết quả xóa
     */
    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<?> removeTeamMember(@PathVariable Long id, @PathVariable Long memberId) {
        try {
            // Kiểm tra quyền truy cập team trước khi xóa thành viên
            teamService.validateTeamAccess(id);

            boolean success = teamService.removeTeamMember(id, memberId);
            if (success) {
                return ResponseEntity.ok("Team member removed successfully");
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).body("You don't have permission to remove members from this team");
            } else if (e.getMessage().contains("Member does not belong")) {
                return ResponseEntity.status(400).body(e.getMessage());
            } else if (e.getMessage().contains("Cannot remove the only owner")) {
                return ResponseEntity.status(400).body(e.getMessage());
            } else if (e.getMessage().contains("Team member not found")) {
                return ResponseEntity.status(404).body(e.getMessage());
            }
            return ResponseEntity.status(500).body("Error removing team member: " + e.getMessage());
        }
    }

    // ✅ NEW: Team Timeline/Activity endpoints
    /**
     * Lấy timeline activities của team (tất cả hoạt động của team tasks và project tasks)
     * GET /api/teams/{id}/timeline
     */
    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto>> getTeamTimeline(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            teamService.validateTeamAccess(id);

            List<com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto> timeline =
                teamService.getTeamTimeline(id, page, size);
            return ResponseEntity.ok(timeline);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Lấy recent activities của team (20 hoạt động gần nhất)
     * GET /api/teams/{id}/recent-activities
     */
    @GetMapping("/{id}/recent-activities")
    public ResponseEntity<List<com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto>> getTeamRecentActivities(
            @PathVariable Long id) {
        try {
            teamService.validateTeamAccess(id);

            List<com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto> activities =
                teamService.getTeamRecentActivities(id);
            return ResponseEntity.ok(activities);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Lấy team activities theo loại (task created, status changed, etc.)
     * GET /api/teams/{id}/activities/by-type?type=TASK_CREATED
     */
    @GetMapping("/{id}/activities/by-type")
    public ResponseEntity<List<com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto>> getTeamActivitiesByType(
            @PathVariable Long id,
            @RequestParam String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            teamService.validateTeamAccess(id);

            List<com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto> activities =
                teamService.getTeamActivitiesByType(id, type, page, size);
            return ResponseEntity.ok(activities);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Lấy team activities trong khoảng thời gian
     * GET /api/teams/{id}/activities/by-date?startDate=2025-09-01&endDate=2025-09-15
     */
    @GetMapping("/{id}/activities/by-date")
    public ResponseEntity<List<com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto>> getTeamActivitiesByDateRange(
            @PathVariable Long id,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            teamService.validateTeamAccess(id);

            List<com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto> activities =
                teamService.getTeamActivitiesByDateRange(id, startDate, endDate, page, size);
            return ResponseEntity.ok(activities);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get comprehensive team dashboard data
     * GET /api/teams/{id}/dashboard
     *
     * Returns detailed analytics and statistics for the team including:
     * - Team statistics (members, projects, tasks, efficiency)
     * - Member breakdown by role and workload
     * - Project breakdown by status and progress
     * - Upcoming deadlines (this week, next week, overdue)
     * - Team performance trends (monthly data)
     */
    @GetMapping("/{id}/dashboard")
    public ResponseEntity<TeamDashboardResponseDto> getTeamDashboard(@PathVariable Long id) {
        try {
            // Validate team access before returning dashboard data
            teamService.validateTeamAccess(id);

            TeamDashboardResponseDto dashboard = teamService.getTeamDashboard(id);
            return ResponseEntity.ok(dashboard);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Access denied")) {
                return ResponseEntity.status(403).build(); // Forbidden
            } else if (e.getMessage().contains("Team not found")) {
                return ResponseEntity.notFound().build();
            }
            System.err.println("❌ [TeamController] Error loading team dashboard: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get team dashboard overview (alias for dashboard endpoint)
     * GET /api/teams/{id}/dashboard/overview
     *
     * This is an alias endpoint that returns the same comprehensive team dashboard data
     * to support frontend applications that expect the /overview path.
     */
    @GetMapping("/{id}/dashboard/overview")
    public ResponseEntity<TeamDashboardResponseDto> getTeamDashboardOverview(@PathVariable Long id) {
        // This endpoint returns the same data as the main dashboard endpoint
        // to support frontend applications that expect the /overview path
        return getTeamDashboard(id);
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
            log.error("❌ [TeamController] Error getting current user ID: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get current user ID", e);
        }
    }

    /**
     * Log audit event for team activities
     */
    private void logAuditEvent(Long userId, String action, String description) {
        try {
            CreateAuditLogRequestDto auditLog = CreateAuditLogRequestDto.builder()
                    .userId(userId)
                    .action(action + " - " + description)
                    .build();

            auditLogService.create(auditLog);

            log.info("📝 [TeamController] Audit log created - User: {}, Action: {}",
                    userId, action);
        } catch (Exception e) {
            log.error("❌ [TeamController] Error logging audit event: {}", e.getMessage(), e);
        }
    }

    /**
     * Build description of changes for team update audit log
     */
    private String buildUpdateChangesDescription(TeamResponseDto original, TeamResponseDto updated) {
        StringBuilder changes = new StringBuilder();

        if (!original.getName().equals(updated.getName())) {
            changes.append("Name: '").append(original.getName()).append("' -> '").append(updated.getName()).append("'; ");
        }

        if (original.getDescription() != null && !original.getDescription().equals(updated.getDescription())) {
            changes.append("Description changed; ");
        } else if (original.getDescription() == null && updated.getDescription() != null) {
            changes.append("Description added; ");
        }

        return changes.length() > 0 ? changes.toString() : "Minor updates";
    }
}
