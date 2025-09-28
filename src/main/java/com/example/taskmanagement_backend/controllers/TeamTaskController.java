package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.TeamTaskDto.CreateTeamTaskRequestDto;
import com.example.taskmanagement_backend.dtos.TeamTaskDto.UpdateTeamTaskRequestDto;
import com.example.taskmanagement_backend.dtos.TeamTaskDto.TeamTaskResponseDto;
import com.example.taskmanagement_backend.entities.TeamTask;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.enums.TaskPriority;
import com.example.taskmanagement_backend.services.TeamTaskService;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/team-tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = {"https://main.d2az19adxqfdf3.amplifyapp.com", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class TeamTaskController {

    private final TeamTaskService teamTaskService;
    private final UserJpaRepository userRepository;

    // ===== CRUD Operations =====

    /**
     * Create a new team task
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<TeamTaskResponseDto> createTeamTask(
            @Valid @RequestBody CreateTeamTaskRequestDto createDto) {

        TeamTask teamTask = convertToEntity(createDto);
        TeamTask savedTask = teamTaskService.createTeamTask(teamTask);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(convertToResponseDto(savedTask));
    }

    /**
     * Get all team tasks with pagination and filtering
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Page<TeamTaskResponseDto>> getAllTeamTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) String taskCategory,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) Long relatedProjectId) {

        Page<TeamTask> tasks;

        if (teamId != null || status != null || priority != null || taskCategory != null ||
            assigneeId != null || creatorId != null || relatedProjectId != null) {
            tasks = teamTaskService.getTeamTasksWithFilters(
                    teamId, status, priority, taskCategory, assigneeId, creatorId, relatedProjectId,
                    page, size, sortBy, sortDir);
        } else {
            tasks = teamTaskService.getAllTeamTasks(page, size, sortBy, sortDir);
        }

        Page<TeamTaskResponseDto> responseDtos = tasks.map(this::convertToResponseDto);
        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Get team task by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<TeamTaskResponseDto> getTeamTaskById(@PathVariable Long id) {
        TeamTask task = teamTaskService.getTeamTaskById(id);
        return ResponseEntity.ok(convertToResponseDto(task));
    }

    /**
     * Update team task
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<TeamTaskResponseDto> updateTeamTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTeamTaskRequestDto updateDto) {

        TeamTask updateData = convertToEntityForUpdate(updateDto);
        TeamTask updatedTask = teamTaskService.updateTeamTask(id, updateData);

        return ResponseEntity.ok(convertToResponseDto(updatedTask));
    }

    /**
     * Delete team task
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER')")
    public ResponseEntity<Void> deleteTeamTask(@PathVariable Long id) {
        teamTaskService.deleteTeamTask(id);
        return ResponseEntity.noContent().build();
    }

    // ===== Team-specific endpoints =====

    /**
     * Get tasks by team ID
     */
    @GetMapping("/team/{teamId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Page<TeamTaskResponseDto>> getTasksByTeam(
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Page<TeamTask> tasks = teamTaskService.getTasksByTeamId(teamId, page, size, sortBy, sortDir);
        Page<TeamTaskResponseDto> responseDtos = tasks.map(this::convertToResponseDto);

        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Get team task statistics
     */
    @GetMapping("/team/{teamId}/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<TeamTaskService.TeamTaskStats> getTeamTaskStats(@PathVariable Long teamId) {
        TeamTaskService.TeamTaskStats stats = teamTaskService.getTeamTaskStats(teamId);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get tasks by team and category
     */
    @GetMapping("/team/{teamId}/category/{category}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TeamTaskResponseDto>> getTasksByTeamAndCategory(
            @PathVariable Long teamId,
            @PathVariable String category) {

        List<TeamTask> tasks = teamTaskService.getTasksByTeamAndCategory(teamId, category);
        List<TeamTaskResponseDto> responseDtos = tasks.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Get recurring tasks by team
     */
    @GetMapping("/team/{teamId}/recurring")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TeamTaskResponseDto>> getRecurringTasksByTeam(@PathVariable Long teamId) {
        List<TeamTask> tasks = teamTaskService.getRecurringTasksByTeam(teamId);
        List<TeamTaskResponseDto> responseDtos = tasks.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Get overdue tasks by team
     */
    @GetMapping("/team/{teamId}/overdue")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TeamTaskResponseDto>> getOverdueTasksByTeam(@PathVariable Long teamId) {
        List<TeamTask> tasks = teamTaskService.getOverdueTasksByTeam(teamId);
        List<TeamTaskResponseDto> responseDtos = tasks.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Get tasks for current week
     */
    @GetMapping("/team/{teamId}/current-week")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TeamTaskResponseDto>> getTasksForCurrentWeek(@PathVariable Long teamId) {
        List<TeamTask> tasks = teamTaskService.getTasksForCurrentWeek(teamId);
        List<TeamTaskResponseDto> responseDtos = tasks.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Get upcoming recurring tasks
     */
    @GetMapping("/team/{teamId}/upcoming-recurring")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TeamTaskResponseDto>> getUpcomingRecurringTasks(
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "30") int daysAhead) {

        List<TeamTask> tasks = teamTaskService.getUpcomingRecurringTasks(teamId, daysAhead);
        List<TeamTaskResponseDto> responseDtos = tasks.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    // ===== User-specific endpoints =====

    /**
     * Get current user's team tasks
     */
    @GetMapping("/my-tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Page<TeamTaskResponseDto>> getCurrentUserTeamTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Page<TeamTask> tasks = teamTaskService.getCurrentUserTeamTasks(page, size, sortBy, sortDir);
        Page<TeamTaskResponseDto> responseDtos = tasks.map(this::convertToResponseDto);

        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Get user's team task statistics
     */
    @GetMapping("/users/{userId}/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER')")
    public ResponseEntity<TeamTaskService.UserTeamTaskStats> getUserTeamTaskStats(@PathVariable Long userId) {
        TeamTaskService.UserTeamTaskStats stats = teamTaskService.getUserTeamTaskStats(userId);
        return ResponseEntity.ok(stats);
    }

    // ===== Task operations =====

    /**
     * Assign task to team member
     */
    @PutMapping("/{taskId}/assign/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER')")
    public ResponseEntity<TeamTaskResponseDto> assignTaskToMember(
            @PathVariable Long taskId,
            @PathVariable Long userId) {

        TeamTask task = teamTaskService.assignTaskToMember(taskId, userId);
        return ResponseEntity.ok(convertToResponseDto(task));
    }

    /**
     * Update task progress
     */
    @PutMapping("/{taskId}/progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<TeamTaskResponseDto> updateTaskProgress(
            @PathVariable Long taskId,
            @RequestParam Integer progressPercentage) {

        TeamTask task = teamTaskService.updateTaskProgress(taskId, progressPercentage);
        return ResponseEntity.ok(convertToResponseDto(task));
    }

    /**
     * Link task to project
     */
    @PutMapping("/{taskId}/link-project/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER')")
    public ResponseEntity<TeamTaskResponseDto> linkTaskToProject(
            @PathVariable Long taskId,
            @PathVariable Long projectId) {

        TeamTask task = teamTaskService.linkTaskToProject(taskId, projectId);
        return ResponseEntity.ok(convertToResponseDto(task));
    }

    /**
     * Get subtasks
     */
    @GetMapping("/{taskId}/subtasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TeamTaskResponseDto>> getSubtasks(@PathVariable Long taskId) {
        List<TeamTask> subtasks = teamTaskService.getSubtasks(taskId);
        List<TeamTaskResponseDto> responseDtos = subtasks.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    // ===== Utility endpoints =====

    /**
     * Get available task categories
     */
    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<String>> getTaskCategories() {
        List<String> categories = teamTaskService.getTaskCategories();
        return ResponseEntity.ok(categories);
    }

    // ===== Helper Methods =====

    private TeamTask convertToEntity(CreateTeamTaskRequestDto dto) {
        TeamTask.TeamTaskBuilder builder = TeamTask.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .startDate(dto.getStartDate())
                .deadline(dto.getDeadline())
                .estimatedHours(dto.getEstimatedHours())
                .actualHours(dto.getActualHours())
                .progressPercentage(dto.getProgressPercentage())
                .taskCategory(dto.getTaskCategory())
                .isRecurring(dto.getIsRecurring())
                .recurrencePattern(dto.getRecurrencePattern())
                .recurrenceEndDate(dto.getRecurrenceEndDate());

        // Set team
        if (dto.getTeamId() != null) {
            builder.team(com.example.taskmanagement_backend.entities.Team.builder()
                    .id(dto.getTeamId())
                    .build());
        }

        // Set assignee
        if (dto.getAssigneeId() != null) {
            builder.assignee(User.builder().id(dto.getAssigneeId()).build());
        }

        // Set assigned members
        if (dto.getAssignedMemberIds() != null && !dto.getAssignedMemberIds().isEmpty()) {
            List<User> assignedMembers = dto.getAssignedMemberIds().stream()
                    .map(id -> User.builder().id(id).build())
                    .collect(Collectors.toList());
            builder.assignedMembers(assignedMembers);
        }

        // Set related project
        if (dto.getRelatedProjectId() != null) {
            builder.relatedProject(Project.builder().id(dto.getRelatedProjectId()).build());
        }

        // Set parent task
        if (dto.getParentTaskId() != null) {
            builder.parentTask(TeamTask.builder().id(dto.getParentTaskId()).build());
        }

        return builder.build();
    }

    private TeamTask convertToEntityForUpdate(UpdateTeamTaskRequestDto dto) {
        TeamTask.TeamTaskBuilder builder = TeamTask.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(dto.getStatus())
                .priority(dto.getPriority())
                .startDate(dto.getStartDate())
                .deadline(dto.getDeadline())
                .estimatedHours(dto.getEstimatedHours())
                .actualHours(dto.getActualHours())
                .progressPercentage(dto.getProgressPercentage())
                .taskCategory(dto.getTaskCategory())
                .isRecurring(dto.getIsRecurring())
                .recurrencePattern(dto.getRecurrencePattern())
                .recurrenceEndDate(dto.getRecurrenceEndDate());

        // Set assignee
        if (dto.getAssigneeId() != null) {
            builder.assignee(User.builder().id(dto.getAssigneeId()).build());
        }

        // Set assigned members
        if (dto.getAssignedMemberIds() != null) {
            List<User> assignedMembers = dto.getAssignedMemberIds().stream()
                    .map(id -> User.builder().id(id).build())
                    .collect(Collectors.toList());
            builder.assignedMembers(assignedMembers);
        }

        // Set related project
        if (dto.getRelatedProjectId() != null) {
            builder.relatedProject(Project.builder().id(dto.getRelatedProjectId()).build());
        }

        // Set parent task
        if (dto.getParentTaskId() != null) {
            builder.parentTask(TeamTask.builder().id(dto.getParentTaskId()).build());
        }

        return builder.build();
    }

    private TeamTaskResponseDto convertToResponseDto(TeamTask task) {
        TeamTaskResponseDto.TeamTaskResponseDtoBuilder builder = TeamTaskResponseDto.builder()
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
                .taskCategory(task.getTaskCategory())
                .isRecurring(task.getIsRecurring())
                .recurrencePattern(task.getRecurrencePattern())
                .recurrenceEndDate(task.getRecurrenceEndDate())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt());

        // Team information
        if (task.getTeam() != null) {
            builder.teamId(task.getTeam().getId())
                   .teamName(task.getTeam().getName());
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

        // Assigned members
        if (task.getAssignedMembers() != null && !task.getAssignedMembers().isEmpty()) {
            List<TeamTaskResponseDto.AssignedMemberDto> assignedMembers = task.getAssignedMembers().stream()
                    .map(user -> TeamTaskResponseDto.AssignedMemberDto.builder()
                            .id(user.getId())
                            .name(getFullName(user))
                            .email(user.getEmail())
                            .role("MEMBER") // Default role, could be enhanced with actual team roles
                            .build())
                    .collect(Collectors.toList());
            builder.assignedMembers(assignedMembers);
        }

        // Related project information
        if (task.getRelatedProject() != null) {
            builder.relatedProjectId(task.getRelatedProject().getId())
                   .relatedProjectName(task.getRelatedProject().getName());
        }

        // Parent task information
        if (task.getParentTask() != null) {
            builder.parentTaskId(task.getParentTask().getId())
                   .parentTaskTitle(task.getParentTask().getTitle());
        }

        // Subtasks information
        if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
            List<TeamTaskResponseDto.SubtaskDto> subtasks = task.getSubtasks().stream()
                    .map(subtask -> TeamTaskResponseDto.SubtaskDto.builder()
                            .id(subtask.getId())
                            .title(subtask.getTitle())
                            .status(subtask.getStatus())
                            .progressPercentage(subtask.getProgressPercentage())
                            .deadline(subtask.getDeadline())
                            .taskCategory(subtask.getTaskCategory())
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