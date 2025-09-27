package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto;
import com.example.taskmanagement_backend.services.ProjectTaskActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/project-task-activities")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ProjectTaskActivityController {

    private final ProjectTaskActivityService projectTaskActivityService;

    /**
     * Get all activities for a project task
     */
    @GetMapping("/project-task/{projectTaskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TaskActivityResponseDto>> getProjectTaskActivities(
            @PathVariable Long projectTaskId) {

        List<TaskActivityResponseDto> activities = projectTaskActivityService.getProjectTaskActivities(projectTaskId);
        return ResponseEntity.ok(activities);
    }

    /**
     * Get project task activities with pagination
     */
    @GetMapping("/project-task/{projectTaskId}/paginated")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Page<TaskActivityResponseDto>> getProjectTaskActivitiesPaginated(
            @PathVariable Long projectTaskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<TaskActivityResponseDto> activities = projectTaskActivityService.getProjectTaskActivities(projectTaskId, page, size);
        return ResponseEntity.ok(activities);
    }

    /**
     * Get recent activities for a project task (5 most recent)
     */
    @GetMapping("/project-task/{projectTaskId}/recent")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TaskActivityResponseDto>> getRecentProjectTaskActivities(
            @PathVariable Long projectTaskId) {

        List<TaskActivityResponseDto> recentActivities = projectTaskActivityService.getRecentProjectTaskActivities(projectTaskId);
        return ResponseEntity.ok(recentActivities);
    }

    /**
     * Get activity count for a project task
     */
    @GetMapping("/project-task/{projectTaskId}/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Long> getProjectTaskActivityCount(@PathVariable Long projectTaskId) {
        Long count = projectTaskActivityService.countProjectTaskActivities(projectTaskId);
        return ResponseEntity.ok(count);
    }
}
