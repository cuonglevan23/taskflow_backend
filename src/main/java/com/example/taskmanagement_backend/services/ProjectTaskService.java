package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.entities.ProjectTask;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.ProjectMember;
import com.example.taskmanagement_backend.entities.User;

import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.enums.TaskPriority;
import com.example.taskmanagement_backend.repositories.ProjectTaskJpaRepository;
import com.example.taskmanagement_backend.repositories.ProjectJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectTaskService {

    private final ProjectTaskJpaRepository projectTaskRepository;
    private final ProjectJpaRepository projectRepository;
    private final UserJpaRepository userRepository;
    private final ProgressUpdateService progressUpdateService;
    private final ProjectTaskActivityService projectTaskActivityService; // ✅ UPDATED: Use ProjectTaskActivityService

    // ===== CRUD Operations =====

    /**
     * Create a new project task
     */
    public ProjectTask createProjectTask(ProjectTask projectTask) {
        // Validate project exists
        if (projectTask.getProject() == null || projectTask.getProject().getId() == null) {
            throw new IllegalArgumentException("Project is required for project task");
        }

        Project project = projectRepository.findById(projectTask.getProject().getId())
                .orElseThrow(() -> new EntityNotFoundException("Project not found with id: " + projectTask.getProject().getId()));

        // Set current user as creator if not set
        if (projectTask.getCreator() == null) {
            projectTask.setCreator(getCurrentUser());
        }

        projectTask.setProject(project);
        ProjectTask savedTask = projectTaskRepository.save(projectTask);

        // ✅ NEW: Log project task creation activity
        projectTaskActivityService.logProjectTaskCreated(savedTask);

        // ✅ AUTO-UPDATE: Update progress when new task is created
        progressUpdateService.updateProgressOnProjectTaskChange(savedTask);

        return savedTask;
    }

    /**
     * Get all project tasks with pagination
     */
    @Transactional(readOnly = true)
    public Page<ProjectTask> getAllProjectTasks(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                   Sort.by(sortBy).descending() :
                   Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return projectTaskRepository.findAll(pageable);
    }

    /**
     * Get project task by ID
     */
    @Transactional(readOnly = true)
    public ProjectTask getProjectTaskById(Long id) {
        return projectTaskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Project task not found with id: " + id));
    }

    /**
     * Update project task
     */
    public ProjectTask updateProjectTask(Long id, ProjectTask updateData) {
        ProjectTask existingTask = getProjectTaskById(id);

        // ✅ NEW: Store old values for activity logging
        String oldTitle = existingTask.getTitle();
        String oldDescription = existingTask.getDescription();
        TaskStatus oldStatus = existingTask.getStatus();
        TaskPriority oldPriority = existingTask.getPriority();
        LocalDate oldDeadline = existingTask.getDeadline();
        User oldAssignee = existingTask.getAssignee();

        // Update fields and log activities if they changed
        if (updateData.getTitle() != null && !updateData.getTitle().equals(oldTitle)) {
            existingTask.setTitle(updateData.getTitle());
            projectTaskActivityService.logProjectTaskTitleChanged(existingTask, oldTitle, updateData.getTitle());
        }

        if (updateData.getDescription() != null && !updateData.getDescription().equals(oldDescription)) {
            existingTask.setDescription(updateData.getDescription());
            projectTaskActivityService.logProjectTaskDescriptionChanged(existingTask, oldDescription, updateData.getDescription());
        }

        if (updateData.getStatus() != null && !updateData.getStatus().equals(oldStatus)) {
            existingTask.setStatus(updateData.getStatus());
            projectTaskActivityService.logProjectTaskStatusChanged(existingTask,
                oldStatus != null ? oldStatus.toString() : null,
                updateData.getStatus().toString());

            // ✅ Log special status changes
            if (TaskStatus.DONE.equals(updateData.getStatus())) {
                projectTaskActivityService.logProjectTaskCompleted(existingTask);
            } else if ((TaskStatus.TODO.equals(updateData.getStatus()) || TaskStatus.IN_PROGRESS.equals(updateData.getStatus())) &&
                      TaskStatus.DONE.equals(oldStatus)) {
                projectTaskActivityService.logProjectTaskReopened(existingTask);
            }
        }

        if (updateData.getPriority() != null && !updateData.getPriority().equals(oldPriority)) {
            existingTask.setPriority(updateData.getPriority());
            projectTaskActivityService.logProjectTaskPriorityChanged(existingTask,
                oldPriority != null ? oldPriority.toString() : null,
                updateData.getPriority().toString());
        }

        if (updateData.getStartDate() != null) {
            existingTask.setStartDate(updateData.getStartDate());
        }

        if (updateData.getDeadline() != null && !updateData.getDeadline().equals(oldDeadline)) {
            existingTask.setDeadline(updateData.getDeadline());
            projectTaskActivityService.logProjectTaskDeadlineChanged(existingTask,
                oldDeadline != null ? oldDeadline.toString() : null,
                updateData.getDeadline().toString());
        }

        if (updateData.getEstimatedHours() != null) {
            existingTask.setEstimatedHours(updateData.getEstimatedHours());
        }

        if (updateData.getActualHours() != null) {
            existingTask.setActualHours(updateData.getActualHours());
        }

        if (updateData.getProgressPercentage() != null) {
            existingTask.setProgressPercentage(updateData.getProgressPercentage());
        }

        if (updateData.getAssignee() != null && !updateData.getAssignee().equals(oldAssignee)) {
            existingTask.setAssignee(updateData.getAssignee());
            if (oldAssignee != null) {
                projectTaskActivityService.logProjectTaskAssigneeRemoved(existingTask, oldAssignee.getEmail());
            }
            projectTaskActivityService.logProjectTaskAssigneeAdded(existingTask, updateData.getAssignee().getEmail());
        }

        if (updateData.getAdditionalAssignees() != null) {
            existingTask.setAdditionalAssignees(updateData.getAdditionalAssignees());
        }

        ProjectTask savedTask = projectTaskRepository.save(existingTask);

        // ✅ AUTO-UPDATE: Update progress when task is updated (especially status changes)
        progressUpdateService.updateProgressOnProjectTaskChange(savedTask);

        return savedTask;
    }

    /**
     * Delete project task
     */
    public void deleteProjectTask(Long id) {
        ProjectTask task = getProjectTaskById(id);

        // ✅ FIXED: Update progress BEFORE deleting the task to avoid constraint violations
        // Store project info for progress update since task will be deleted
        Project project = task.getProject();

        // Update progress first (while task still exists)
        progressUpdateService.updateProgressOnProjectTaskChange(task);

        // Delete the task (this will also delete related activities due to cascade)
        projectTaskRepository.delete(task);

        // Update project progress again after deletion
        try {
            progressUpdateService.updateProjectProgress(project.getId());
        } catch (Exception e) {
            // Log error but don't fail the deletion
            // The task has already been deleted successfully
        }
    }

    // ===== Query Methods =====

    /**
     * Get tasks by project ID
     */
    @Transactional(readOnly = true)
    public Page<ProjectTask> getTasksByProjectId(Long projectId, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                   Sort.by(sortBy).descending() :
                   Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return projectTaskRepository.findByProjectId(projectId, pageable);
    }

    /**
     * Get tasks by project ID (all)
     */
    @Transactional(readOnly = true)
    public List<ProjectTask> getTasksByProjectId(Long projectId) {
        return projectTaskRepository.findByProjectId(projectId);
    }

    /**
     * Get user's project tasks (where user is creator, assignee, or additional assignee)
     */
    @Transactional(readOnly = true)
    public Page<ProjectTask> getUserProjectTasks(Long userId, int page, int size, String sortBy, String sortDir) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                   Sort.by(sortBy).descending() :
                   Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return projectTaskRepository.findUserProjectTasks(user, pageable);
    }

    /**
     * Get current user's project tasks
     */
    @Transactional(readOnly = true)
    public Page<ProjectTask> getCurrentUserProjectTasks(int page, int size, String sortBy, String sortDir) {
        User currentUser = getCurrentUser();
        return getUserProjectTasks(currentUser.getId(), page, size, sortBy, sortDir);
    }

    /**
     * Get all current user's project tasks without pagination
     */
    @Transactional(readOnly = true)
    public List<ProjectTask> getCurrentUserProjectTasks() {
        User currentUser = getCurrentUser();
        return projectTaskRepository.findByAssigneeOrAdditionalAssignees(currentUser);
    }

    /**
     * Get tasks with filters
     */
    @Transactional(readOnly = true)
    public Page<ProjectTask> getProjectTasksWithFilters(
            Long projectId, TaskStatus status, TaskPriority priority,
            Long assigneeId, Long creatorId, int page, int size, String sortBy, String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                   Sort.by(sortBy).descending() :
                   Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return projectTaskRepository.findProjectTasksWithFilters(
                projectId, status, priority, assigneeId, creatorId, pageable);
    }

    /**
     * Get overdue tasks by project
     */
    @Transactional(readOnly = true)
    public List<ProjectTask> getOverdueTasksByProject(Long projectId) {
        return projectTaskRepository.findOverdueTasksByProject(projectId);
    }

    /**
     * Get tasks by status
     */
    @Transactional(readOnly = true)
    public List<ProjectTask> getTasksByProjectAndStatus(Long projectId, TaskStatus status) {
        return projectTaskRepository.findByProjectIdAndStatus(projectId, status);
    }

    /**
     * Get subtasks
     */
    @Transactional(readOnly = true)
    public List<ProjectTask> getSubtasks(Long parentTaskId) {
        ProjectTask parentTask = getProjectTaskById(parentTaskId);
        return projectTaskRepository.findByParentTask(parentTask);
    }

    // ===== Statistics Methods =====

    /**
     * Get project task statistics
     */
    @Transactional(readOnly = true)
    public ProjectTaskStats getProjectTaskStats(Long projectId) {
        long totalTasks = projectTaskRepository.countByProjectId(projectId);
        long completedTasks = projectTaskRepository.countByProjectIdAndStatus(projectId, TaskStatus.DONE);
        long inProgressTasks = projectTaskRepository.countByProjectIdAndStatus(projectId, TaskStatus.IN_PROGRESS);
        long todoTasks = projectTaskRepository.countByProjectIdAndStatus(projectId, TaskStatus.TODO);

        List<ProjectTask> overdueTasks = projectTaskRepository.findOverdueTasksByProject(projectId);

        double completionPercentage = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

        return ProjectTaskStats.builder()
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .inProgressTasks(inProgressTasks)
                .todoTasks(todoTasks)
                .overdueTasks(overdueTasks.size())
                .completionPercentage(completionPercentage)
                .build();
    }

    /**
     * Get user project task statistics
     */
    @Transactional(readOnly = true)
    public UserProjectTaskStats getUserProjectTaskStats(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        long totalTasks = projectTaskRepository.countUserProjectTasks(user);

        return UserProjectTaskStats.builder()
                .userId(userId)
                .totalProjectTasks(totalTasks)
                .build();
    }

    // ===== Helper Methods =====

    /**
     * Get current authenticated user
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    /**
     * Assign task to user
     */
    public ProjectTask assignTaskToUser(Long taskId, Long userId) {
        ProjectTask task = getProjectTaskById(taskId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // ✅ NEW: Store old assignee for activity logging
        User oldAssignee = task.getAssignee();

        task.setAssignee(user);
        ProjectTask savedTask = projectTaskRepository.save(task);

        // ✅ NEW: Log assignee change activity
        if (oldAssignee != null) {
            projectTaskActivityService.logProjectTaskAssigneeRemoved(savedTask, oldAssignee.getEmail());
        }
        projectTaskActivityService.logProjectTaskAssigneeAdded(savedTask, user.getEmail());

        return savedTask;
    }

    /**
     * ✅ NEW: Add additional assignee to task
     */
    public ProjectTask addAdditionalAssignee(Long taskId, Long userId) {
        ProjectTask task = getProjectTaskById(taskId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // Initialize additionalAssignees if null
        if (task.getAdditionalAssignees() == null) {
            task.setAdditionalAssignees(new java.util.ArrayList<>());
        }

        // Check if user is already assigned as primary assignee
        if (task.getAssignee() != null && task.getAssignee().getId().equals(userId)) {
            throw new IllegalArgumentException("User is already assigned as primary assignee");
        }

        // Check if user is already in additional assignees
        boolean alreadyAssigned = task.getAdditionalAssignees().stream()
                .anyMatch(assignee -> assignee.getId().equals(userId));

        if (alreadyAssigned) {
            throw new IllegalArgumentException("User is already assigned as additional assignee");
        }

        // Add to additional assignees
        task.getAdditionalAssignees().add(user);
        ProjectTask savedTask = projectTaskRepository.save(task);

        // Log activity
        projectTaskActivityService.logProjectTaskAssigneeAdded(savedTask, user.getEmail());

        return savedTask;
    }

    /**
     * ✅ NEW: Remove additional assignee from task
     */
    public ProjectTask removeAdditionalAssignee(Long taskId, Long userId) {
        ProjectTask task = getProjectTaskById(taskId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        if (task.getAdditionalAssignees() == null) {
            throw new IllegalArgumentException("Task has no additional assignees");
        }

        // Remove from additional assignees
        boolean removed = task.getAdditionalAssignees().removeIf(assignee -> assignee.getId().equals(userId));

        if (!removed) {
            throw new IllegalArgumentException("User is not assigned as additional assignee");
        }

        ProjectTask savedTask = projectTaskRepository.save(task);

        // Log activity
        projectTaskActivityService.logProjectTaskAssigneeRemoved(savedTask, user.getEmail());

        return savedTask;
    }

    /**
     * ✅ NEW: Assign multiple users to task at once
     */
    public ProjectTask assignMultipleUsers(Long taskId, Long primaryAssigneeId, List<Long> additionalAssigneeIds) {
        ProjectTask task = getProjectTaskById(taskId);

        // Set primary assignee
        if (primaryAssigneeId != null) {
            User primaryUser = userRepository.findById(primaryAssigneeId)
                    .orElseThrow(() -> new EntityNotFoundException("Primary assignee not found with id: " + primaryAssigneeId));

            User oldPrimaryAssignee = task.getAssignee();
            task.setAssignee(primaryUser);

            // Log primary assignee change
            if (oldPrimaryAssignee != null) {
                projectTaskActivityService.logProjectTaskAssigneeRemoved(task, oldPrimaryAssignee.getEmail());
            }
            projectTaskActivityService.logProjectTaskAssigneeAdded(task, primaryUser.getEmail());
        }

        // Set additional assignees
        if (additionalAssigneeIds != null && !additionalAssigneeIds.isEmpty()) {
            List<User> additionalUsers = new java.util.ArrayList<>();

            for (Long userId : additionalAssigneeIds) {
                // Skip if same as primary assignee
                if (primaryAssigneeId != null && userId.equals(primaryAssigneeId)) {
                    continue;
                }

                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new EntityNotFoundException("Additional assignee not found with id: " + userId));
                additionalUsers.add(user);

                // Log additional assignee
                projectTaskActivityService.logProjectTaskAssigneeAdded(task, user.getEmail());
            }

            task.setAdditionalAssignees(additionalUsers);
        }

        return projectTaskRepository.save(task);
    }

    /**
     * ✅ NEW: Get all assignees (primary + additional) for a task
     */
    @Transactional(readOnly = true)
    public List<User> getAllTaskAssignees(Long taskId) {
        ProjectTask task = getProjectTaskById(taskId);
        List<User> allAssignees = new java.util.ArrayList<>();

        // Add primary assignee
        if (task.getAssignee() != null) {
            allAssignees.add(task.getAssignee());
        }

        // Add additional assignees
        if (task.getAdditionalAssignees() != null) {
            allAssignees.addAll(task.getAdditionalAssignees());
        }

        return allAssignees;
    }

    /**
     * ✅ NEW: Check if user is assigned to task (primary or additional)
     */
    @Transactional(readOnly = true)
    public boolean isUserAssignedToTask(Long taskId, Long userId) {
        ProjectTask task = getProjectTaskById(taskId);

        // Check primary assignee
        if (task.getAssignee() != null && task.getAssignee().getId().equals(userId)) {
            return true;
        }

        // Check additional assignees
        if (task.getAdditionalAssignees() != null) {
            return task.getAdditionalAssignees().stream()
                    .anyMatch(assignee -> assignee.getId().equals(userId));
        }

        return false;
    }

    // ===== File Handling and Google Calendar Integration =====

    /**
     * Handle file uploads for project tasks
     * @param taskId The ID of the task to upload files for
     * @param files The files to upload
     * @return Comma-separated list of file keys/paths
     */
    public String handleFileUploads(Long taskId, List<org.springframework.web.multipart.MultipartFile> files) {
        // Validate task exists
        ProjectTask task = getProjectTaskById(taskId);

        // Implement the file upload logic here using your S3 or storage service
        // This is a placeholder implementation
        StringBuilder fileKeys = new StringBuilder();

        // Here you would typically:
        // 1. Upload files to storage (S3, local, etc.)
        // 2. Store metadata in database
        // 3. Return the keys/paths to the uploaded files

        // For demonstration purposes, we'll just return some placeholder paths
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) fileKeys.append(",");
            fileKeys.append("uploads/project-tasks/").append(taskId).append("/")
                  .append(System.currentTimeMillis()).append("-").append(files.get(i).getOriginalFilename());
        }

        // Log the activity
        projectTaskActivityService.logProjectTaskFileUploaded(task, files.size());

        return fileKeys.toString();
    }

    /**
     * Handle file deletions for project tasks
     * @param filesToDelete List of file paths/keys to delete
     */
    public void handleFileDeletions(List<String> filesToDelete) {
        // Implement the file deletion logic here using your storage service
        // This is a placeholder implementation

        // Here you would typically:
        // 1. Delete files from storage (S3, local, etc.)
        // 2. Update metadata in database

        // Log the activity - we'd need the task ID here in a real implementation
        // projectTaskActivityService.logProjectTaskFileDeleted(task, filesToDelete.size());
    }

    /**
     * Get file attachments for a project task
     * @param taskId Task ID
     * @return List of task attachments
     */
    public List<com.example.taskmanagement_backend.dtos.TaskAttachmentDto.TaskAttachmentResponseDto> getTaskAttachments(Long taskId) {
        // Validate task exists
        ProjectTask task = getProjectTaskById(taskId);

        // This is a placeholder implementation
        // In a real implementation, you would fetch attachment metadata from your database
        return new java.util.ArrayList<>();
    }

    /**
     * Get attachment statistics for a project task
     * @param taskId Task ID
     * @return Attachment statistics
     */
    public com.example.taskmanagement_backend.services.TaskAttachmentService.TaskAttachmentStatsDto getAttachmentStats(Long taskId) {
        // Validate task exists
        ProjectTask task = getProjectTaskById(taskId);

        // This is a placeholder implementation
        // In a real implementation, you would calculate statistics based on actual attachments
        return new com.example.taskmanagement_backend.services.TaskAttachmentService.TaskAttachmentStatsDto();
    }

    /**
     * Create a Google Calendar event for a project task
     * Google Calendar Service - Updated cho backend mới
     * Backend tự động lấy Google OAuth2 token từ user đã đăng nhập
     * @param dto Calendar event data
     * @return Created calendar event
     */
    public com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CalendarEventResponseDto createCalendarEvent(
            com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CreateCalendarEventRequestDto dto) {

        // Validate task exists
        Long taskId = dto.getTaskId();
        ProjectTask task = getProjectTaskById(taskId);

        // This is a placeholder implementation
        // In a real implementation, you would:
        // 1. Use Google Calendar API to create an event (backend tự động lấy OAuth2 token)
        // 2. Store the event ID and other details with the task
        // 3. Return the created event details

        // Create a placeholder response
        com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CalendarEventResponseDto response =
            new com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CalendarEventResponseDto();

        // Simulate setting event ID and other fields
        String eventId = "event_" + System.currentTimeMillis();

        // Update the task with calendar information
        task.setGoogleCalendarEventId(eventId);
        task.setGoogleMeetLink("https://meet.google.com/" + eventId.substring(0, 8));
        task.setGoogleCalendarEventUrl("https://calendar.google.com/calendar/event?eid=" + eventId);
        task.setIsSyncedToCalendar(true);
        task.setCalendarSyncedAt(java.time.LocalDateTime.now());

        // Save the updated task
        projectTaskRepository.save(task);

        // Log the activity
        projectTaskActivityService.logProjectTaskCalendarEventCreated(task);

        return response;
    }

    /**
     * Update a Google Calendar event for a project task
     * Backend tự động lấy Google OAuth2 token từ user đã đăng nhập
     * @param eventId Calendar event ID
     * @param dto Calendar event data
     * @return Updated calendar event
     */
    public com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CalendarEventResponseDto updateCalendarEvent(
            String eventId,
            com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CreateCalendarEventRequestDto dto) {

        // Validate task exists
        Long taskId = dto.getTaskId();
        ProjectTask task = getProjectTaskById(taskId);

        // This is a placeholder implementation
        // In a real implementation, you would:
        // 1. Use Google Calendar API to update the event (backend tự động lấy OAuth2 token)
        // 2. Update the stored event details with the task
        // 3. Return the updated event details

        // Update the task with calendar information
        task.setCalendarSyncedAt(java.time.LocalDateTime.now());

        // Save the updated task
        projectTaskRepository.save(task);

        // Log the activity
        projectTaskActivityService.logProjectTaskCalendarEventUpdated(task);

        // Create a placeholder response
        return new com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CalendarEventResponseDto();
    }

    /**
     * Delete a Google Calendar event for a project task
     * Backend tự động lấy Google OAuth2 token từ user đã đăng nhập
     * @param taskId Task ID
     * @param eventId Calendar event ID
     * @return Whether the deletion was successful
     */
    public boolean deleteCalendarEvent(Long taskId, String eventId) {
        // Validate task exists
        ProjectTask task = getProjectTaskById(taskId);

        // This is a placeholder implementation
        // In a real implementation, you would:
        // 1. Use Google Calendar API to delete the event (backend tự động lấy OAuth2 token)
        // 2. Remove the event details from the task

        // Clear calendar information from the task
        task.setGoogleCalendarEventId(null);
        task.setGoogleMeetLink(null);
        task.setGoogleCalendarEventUrl(null);
        task.setIsSyncedToCalendar(false);
        task.setCalendarSyncedAt(null);

        // Save the updated task
        projectTaskRepository.save(task);

        // Log the activity
        projectTaskActivityService.logProjectTaskCalendarEventDeleted(task);

        return true;
    }

    /**
     * Get all members of a specific project for task assignment
     * This method returns users who are part of the project team
     * Includes both direct project members AND team members if project is associated with a team
     */
    @Transactional(readOnly = true)
    public List<User> getProjectMembers(Long projectId) {
        // Verify project exists
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found with id: " + projectId));

        // Use a Set to avoid duplicate users
        Set<User> allMembers = new java.util.HashSet<>();

        // 1. Add direct project members
        project.getMembers().stream()
                .map(projectMember -> projectMember.getUser())
                .forEach(allMembers::add);

        // 2. If project is associated with a team, add all team members
        if (project.getTeam() != null) {
            project.getTeam().getMembers().stream()
                    .map(teamMember -> teamMember.getUser())
                    .forEach(allMembers::add);
        }

        // 3. Always include the project owner if not already included
        if (project.getOwner() != null) {
            allMembers.add(project.getOwner());
        }

        // Convert Set to List and return
        return new java.util.ArrayList<>(allMembers);
    }

    /**
     * Update task progress
     */
    public ProjectTask updateTaskProgress(Long taskId, Integer progressPercentage) {
        ProjectTask task = getProjectTaskById(taskId);

        if (progressPercentage < 0 || progressPercentage > 100) {
            throw new IllegalArgumentException("Progress percentage must be between 0 and 100");
        }

        // ✅ NEW: Store old values for activity logging
        TaskStatus oldStatus = task.getStatus();

        task.setProgressPercentage(progressPercentage);

        // Auto-update status based on progress
        TaskStatus newStatus = null;
        if (progressPercentage == 0) {
            newStatus = TaskStatus.TODO;
            task.setStatus(newStatus);
        } else if (progressPercentage == 100) {
            newStatus = TaskStatus.DONE;
            task.setStatus(newStatus);
        } else {
            newStatus = TaskStatus.IN_PROGRESS;
            task.setStatus(newStatus);
        }

        ProjectTask savedTask = projectTaskRepository.save(task);

        // ✅ NEW: Log status change if it occurred
        if (!newStatus.equals(oldStatus)) {
            projectTaskActivityService.logProjectTaskStatusChanged(savedTask,
                oldStatus != null ? oldStatus.toString() : null,
                newStatus.toString());

            // Log special status changes
            if (TaskStatus.DONE.equals(newStatus)) {
                projectTaskActivityService.logProjectTaskCompleted(savedTask);
            } else if ((TaskStatus.TODO.equals(newStatus) || TaskStatus.IN_PROGRESS.equals(newStatus)) &&
                      TaskStatus.DONE.equals(oldStatus)) {
                projectTaskActivityService.logProjectTaskReopened(savedTask);
            }
        }

        // ✅ AUTO-UPDATE: Update progress when task progress is updated
        progressUpdateService.updateProgressOnProjectTaskChange(savedTask);

        return savedTask;
    }

    /**
     * Get project tasks as TaskResponseDto list for dashboard
     * ✅ FIX: Add wrapper method to convert ProjectTask to TaskResponseDto
     */
    @Transactional(readOnly = true)
    public List<com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto> getTasksByProjectIdAsDto(Long projectId) {
        List<ProjectTask> projectTasks = projectTaskRepository.findByProjectId(projectId);

        return projectTasks.stream()
                .map(this::convertToTaskResponseDto)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Convert ProjectTask entity to TaskResponseDto
     */
    private com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto convertToTaskResponseDto(ProjectTask projectTask) {
        // Combine primary assignee and additional assignees for assignedToIds
        List<Long> assignedToIds = new java.util.ArrayList<>();
        List<String> assignedToEmails = new java.util.ArrayList<>();

        // Add primary assignee
        if (projectTask.getAssignee() != null) {
            assignedToIds.add(projectTask.getAssignee().getId());
            assignedToEmails.add(projectTask.getAssignee().getEmail());
        }

        // Add additional assignees
        if (projectTask.getAdditionalAssignees() != null) {
            projectTask.getAdditionalAssignees().forEach(user -> {
                assignedToIds.add(user.getId());
                assignedToEmails.add(user.getEmail());
            });
        }

        return com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto.builder()
                .id(projectTask.getId())
                .title(projectTask.getTitle())
                .description(projectTask.getDescription())
                .status(projectTask.getStatus() != null ? projectTask.getStatus().toString() : null)
                .priority(projectTask.getPriority() != null ? projectTask.getPriority().toString() : null)
                .startDate(projectTask.getStartDate())
                .deadline(projectTask.getDeadline())
                .createdAt(projectTask.getCreatedAt())
                .updatedAt(projectTask.getUpdatedAt())
                .assignedToIds(assignedToIds)
                .assignedToEmails(assignedToEmails)
                .comment(projectTask.getComment())
                .urlFile(projectTask.getUrlFile())
                .isPublic(null) // ProjectTask doesn't have isPublic field, setting to null
                .googleCalendarEventId(projectTask.getGoogleCalendarEventId())
                .build();
    }

    // ProjectTaskStats inner class for project statistics
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProjectTaskStats {
        private long totalTasks;
        private long completedTasks;
        private long inProgressTasks;
        private long todoTasks;
        private long overdueTasks;
        private double completionPercentage;
    }

    // UserProjectTaskStats inner class for user statistics
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserProjectTaskStats {
        private Long userId;
        private long totalProjectTasks;
    }
}
