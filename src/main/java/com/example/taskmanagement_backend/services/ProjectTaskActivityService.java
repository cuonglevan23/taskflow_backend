package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import com.example.taskmanagement_backend.entities.ProjectTask;
import com.example.taskmanagement_backend.entities.ProjectTaskActivity;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.TaskActivityType;
import com.example.taskmanagement_backend.repositories.ProjectTaskActivityRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectTaskActivityService {

    private final ProjectTaskActivityRepository projectTaskActivityRepository;
    private final UserJpaRepository userJpaRepository;
    private final UserProfileMapper userProfileMapper;

    /**
     * Tạo activity log cho project task
     */
    @Transactional
    public ProjectTaskActivity logActivity(ProjectTask projectTask, TaskActivityType activityType, String description,
                                         String oldValue, String newValue, String fieldName) {
        try {
            log.info("🔔 [ProjectTaskActivityService] Starting logActivity for projectTask ID: {}, activityType: {}",
                    projectTask.getId(), activityType);

            User currentUser = getCurrentUser();
            log.info("🔔 [ProjectTaskActivityService] Current user: {} (ID: {})", currentUser.getEmail(), currentUser.getId());

            ProjectTaskActivity activity = ProjectTaskActivity.builder()
                    .projectTask(projectTask)
                    .user(currentUser)
                    .activityType(activityType)
                    .description(description)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .fieldName(fieldName)
                    .createdAt(LocalDateTime.now())
                    .build();

            log.info("🔔 [ProjectTaskActivityService] Created activity entity, about to save...");
            ProjectTaskActivity savedActivity = projectTaskActivityRepository.save(activity);
            log.info("✅ [ProjectTaskActivityService] Successfully saved activity with ID: {}", savedActivity.getId());

            return savedActivity;
        } catch (Exception e) {
            log.error("❌ [ProjectTaskActivityService] Error logging project task activity for task {}: {}",
                     projectTask.getId(), e.getMessage(), e);
            e.printStackTrace(); // Print full stack trace for debugging
            return null;
        }
    }

    // ===== Generic activity logging methods =====

    @Transactional
    public void logProjectTaskCreated(ProjectTask projectTask) {
        logActivity(projectTask, TaskActivityType.TASK_CREATED,
                   "created this project task", null, null, null);
    }

    @Transactional
    public void logProjectTaskTitleChanged(ProjectTask projectTask, String oldTitle, String newTitle) {
        String description = String.format("changed title from \"%s\" to \"%s\"", oldTitle, newTitle);
        logActivity(projectTask, TaskActivityType.TITLE_CHANGED, description, oldTitle, newTitle, "title");
    }

    @Transactional
    public void logProjectTaskDescriptionChanged(ProjectTask projectTask, String oldDesc, String newDesc) {
        logActivity(projectTask, TaskActivityType.DESCRIPTION_CHANGED, "updated project task description",
                   oldDesc, newDesc, "description");
    }

    @Transactional
    public void logProjectTaskStatusChanged(ProjectTask projectTask, String oldStatus, String newStatus) {
        String description = String.format("changed status from %s to %s", oldStatus, newStatus);
        logActivity(projectTask, TaskActivityType.STATUS_CHANGED, description, oldStatus, newStatus, "status");
    }

    @Transactional
    public void logProjectTaskPriorityChanged(ProjectTask projectTask, String oldPriority, String newPriority) {
        String description = String.format("changed priority from %s to %s", oldPriority, newPriority);
        logActivity(projectTask, TaskActivityType.PRIORITY_CHANGED, description, oldPriority, newPriority, "priority");
    }

    @Transactional
    public void logProjectTaskDeadlineChanged(ProjectTask projectTask, String oldDeadline, String newDeadline) {
        String description = String.format("changed due date from %s to %s",
                                         oldDeadline != null ? oldDeadline : "None",
                                         newDeadline != null ? newDeadline : "None");
        logActivity(projectTask, TaskActivityType.DEADLINE_CHANGED, description, oldDeadline, newDeadline, "deadline");
    }

    @Transactional
    public void logProjectTaskCompleted(ProjectTask projectTask) {
        logActivity(projectTask, TaskActivityType.TASK_COMPLETED, "completed this project task", null, null, null);
    }

    @Transactional
    public void logProjectTaskReopened(ProjectTask projectTask) {
        logActivity(projectTask, TaskActivityType.TASK_REOPENED, "reopened this project task", null, null, null);
    }

    @Transactional
    public void logProjectTaskAssigneeAdded(ProjectTask projectTask, String assigneeEmail) {
        String description = String.format("added %s to this project task", assigneeEmail);
        logActivity(projectTask, TaskActivityType.ASSIGNEE_ADDED, description, null, assigneeEmail, "assignee");
    }

    @Transactional
    public void logProjectTaskAssigneeRemoved(ProjectTask projectTask, String assigneeEmail) {
        String description = String.format("removed %s from this project task", assigneeEmail);
        logActivity(projectTask, TaskActivityType.ASSIGNEE_REMOVED, description, assigneeEmail, null, "assignee");
    }

    @Transactional
    public void logProjectTaskFileUploaded(ProjectTask projectTask, int fileCount) {
        String fileInfo = fileCount + " file" + (fileCount > 1 ? "s" : "");
        logActivity(projectTask, TaskActivityType.FILE_ATTACHED,
                    fileInfo + " uploaded", null, fileInfo, "files");
    }

    @Transactional
    public void logProjectTaskFileDeleted(ProjectTask projectTask, int fileCount) {
        String fileInfo = fileCount + " file" + (fileCount > 1 ? "s" : "");
        logActivity(projectTask, TaskActivityType.FILE_DELETED,
                    fileInfo + " deleted", fileInfo, null, "files");
    }

    @Transactional
    public void logProjectTaskCalendarEventCreated(ProjectTask projectTask) {
        logActivity(projectTask, TaskActivityType.TASK_UPDATED,
                    "Calendar event created", null, "Event created", "calendar");
    }

    @Transactional
    public void logProjectTaskCalendarEventUpdated(ProjectTask projectTask) {
        logActivity(projectTask, TaskActivityType.TASK_UPDATED,
                    "Calendar event updated", "Previous event", "Updated event", "calendar");
    }

    @Transactional
    public void logProjectTaskCalendarEventDeleted(ProjectTask projectTask) {
        logActivity(projectTask, TaskActivityType.TASK_UPDATED,
                    "Calendar event deleted", "Event existed", "Event deleted", "calendar");
    }

    @Transactional
    public void logProjectTaskCommentAdded(ProjectTask projectTask, String commentContent) {
        try {
            log.info("🔔 [ProjectTaskActivityService] Logging comment added activity for project task ID: {}", projectTask.getId());

            String description = "added a comment";

            // Truncate comment content if too long for activity log
            String truncatedContent = commentContent != null && commentContent.length() > 100
                    ? commentContent.substring(0, 100) + "..."
                    : commentContent;

            ProjectTaskActivity activity = logActivity(projectTask, TaskActivityType.COMMENT_ADDED,
                                                     description, null, truncatedContent, "comment");

            if (activity != null) {
                log.info("✅ [ProjectTaskActivityService] Successfully logged comment activity with ID: {}", activity.getId());
            } else {
                log.error("❌ [ProjectTaskActivityService] Failed to log comment activity - returned null");
            }
        } catch (Exception e) {
            log.error("❌ [ProjectTaskActivityService] Error logging comment activity for project task {}: {}",
                     projectTask.getId(), e.getMessage(), e);
        }
    }

    @Transactional
    public void logProjectTaskProgressChanged(ProjectTask projectTask, Integer oldProgress, Integer newProgress) {
        String description = String.format("changed progress from %d%% to %d%%",
                                         oldProgress != null ? oldProgress : 0,
                                         newProgress != null ? newProgress : 0);
        logActivity(projectTask, TaskActivityType.STATUS_CHANGED, description,
                   oldProgress != null ? oldProgress.toString() : "0",
                   newProgress != null ? newProgress.toString() : "0", "progress");
    }

    /**
     * Lấy tất cả activity của một project task
     */
    @Transactional(readOnly = true)
    public List<TaskActivityResponseDto> getProjectTaskActivities(Long projectTaskId) {
        List<ProjectTaskActivity> activities = projectTaskActivityRepository.findByProjectTaskIdOrderByCreatedAtDesc(projectTaskId);
        return activities.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Lấy activity của project task với phân trang
     */
    @Transactional(readOnly = true)
    public Page<TaskActivityResponseDto> getProjectTaskActivities(Long projectTaskId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProjectTaskActivity> activities = projectTaskActivityRepository.findByProjectTaskIdOrderByCreatedAtDesc(projectTaskId, pageable);
        return activities.map(this::convertToDto);
    }

    /**
     * Lấy activity gần đây của project task (5 hoạt động gần nhất)
     */
    @Transactional(readOnly = true)
    public List<TaskActivityResponseDto> getRecentProjectTaskActivities(Long projectTaskId) {
        Pageable pageable = PageRequest.of(0, 5);
        List<ProjectTaskActivity> activities = projectTaskActivityRepository.findRecentActivityByProjectTaskId(projectTaskId, pageable);
        return activities.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Đếm số activity của project task
     */
    @Transactional(readOnly = true)
    public Long countProjectTaskActivities(Long projectTaskId) {
        return projectTaskActivityRepository.countByProjectTaskId(projectTaskId);
    }

    /**
     * Methods for compatibility with Task objects (used by ProjectTaskService)
     * These methods convert Task to ProjectTask and delegate to the appropriate methods
     */
    public void logAssigneeRemoved(com.example.taskmanagement_backend.entities.Task task, String assigneeEmail) {
        // Convert Task to ProjectTask for logging
        ProjectTask projectTask = convertTaskToProjectTask(task);
        logProjectTaskAssigneeRemoved(projectTask, assigneeEmail);
    }

    public void logAssigneeAdded(com.example.taskmanagement_backend.entities.Task task, String assigneeEmail) {
        // Convert Task to ProjectTask for logging
        ProjectTask projectTask = convertTaskToProjectTask(task);
        logProjectTaskAssigneeAdded(projectTask, assigneeEmail);
    }

    public void logStatusChanged(com.example.taskmanagement_backend.entities.Task task, String oldStatus, String newStatus) {
        // Convert Task to ProjectTask for logging
        ProjectTask projectTask = convertTaskToProjectTask(task);
        logProjectTaskStatusChanged(projectTask, oldStatus, newStatus);
    }

    public void logTaskCompleted(com.example.taskmanagement_backend.entities.Task task) {
        // Convert Task to ProjectTask for logging
        ProjectTask projectTask = convertTaskToProjectTask(task);
        logProjectTaskCompleted(projectTask);
    }

    public void logTaskReopened(com.example.taskmanagement_backend.entities.Task task) {
        // Convert Task to ProjectTask for logging
        ProjectTask projectTask = convertTaskToProjectTask(task);
        logProjectTaskReopened(projectTask);
    }

    /**
     * Convert Task to ProjectTask for activity logging
     */
    private ProjectTask convertTaskToProjectTask(com.example.taskmanagement_backend.entities.Task task) {
        // Create a minimal ProjectTask with just the ID for activity logging
        ProjectTask projectTask = new ProjectTask();
        projectTask.setId(task.getId());
        return projectTask;
    }

    /**
     * Convert ProjectTaskActivity entity to DTO
     */
    private TaskActivityResponseDto convertToDto(ProjectTaskActivity activity) {
        UserProfileDto userProfile = userProfileMapper.toUserProfileDto(activity.getUser());

        return TaskActivityResponseDto.builder()
                .id(activity.getId())
                .taskId(activity.getProjectTask().getId())
                .activityType(activity.getActivityType())
                .description(activity.getDescription())
                .oldValue(activity.getOldValue())
                .newValue(activity.getNewValue())
                .fieldName(activity.getFieldName())
                .createdAt(activity.getCreatedAt())
                .user(userProfile)
                .formattedMessage(formatActivityMessage(activity, userProfile))
                .timeAgo(formatTimeAgo(activity.getCreatedAt()))
                .build();
    }

    /**
     * Format activity message để hiển thị đẹp
     */
    private String formatActivityMessage(ProjectTaskActivity activity, UserProfileDto user) {
        String userName = getUserDisplayName(user);
        return String.format("%s %s", userName, activity.getDescription());
    }

    /**
     * Lấy tên hiển thị của user
     */
    private String getUserDisplayName(UserProfileDto user) {
        if (user.getFirstName() != null && user.getLastName() != null) {
            return user.getFirstName() + " " + user.getLastName();
        } else if (user.getUsername() != null) {
            return user.getUsername();
        } else {
            return user.getEmail();
        }
    }

    /**
     * Format thời gian "time ago"
     */
    private String formatTimeAgo(LocalDateTime createdAt) {
        LocalDateTime now = LocalDateTime.now();

        long minutes = ChronoUnit.MINUTES.between(createdAt, now);
        if (minutes < 1) return "Just now";
        if (minutes == 1) return "1 minute ago";
        if (minutes < 60) return minutes + " minutes ago";

        long hours = ChronoUnit.HOURS.between(createdAt, now);
        if (hours == 1) return "1 hour ago";
        if (hours < 24) return hours + " hours ago";

        long days = ChronoUnit.DAYS.between(createdAt, now);
        if (days == 1) return "1 day ago";
        if (days < 7) return days + " days ago";

        long weeks = ChronoUnit.WEEKS.between(createdAt, now);
        if (weeks == 1) return "1 week ago";
        if (weeks < 4) return weeks + " weeks ago";

        long months = ChronoUnit.MONTHS.between(createdAt, now);
        if (months == 1) return "1 month ago";
        if (months < 12) return months + " months ago";

        long years = ChronoUnit.YEARS.between(createdAt, now);
        if (years == 1) return "1 year ago";
        return years + " years ago";
    }

    /**
     * Lấy current user
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userJpaRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }
}
