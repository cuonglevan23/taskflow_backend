package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.TaskActivity;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.TaskActivityType;
import com.example.taskmanagement_backend.repositories.TaskActivityRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class TaskActivityService {

    private final TaskActivityRepository taskActivityRepository;
    private final UserJpaRepository userJpaRepository;
    private final UserProfileMapper userProfileMapper;

    /**
     * Tạo activity log cho task
     */
    @Transactional
    public TaskActivity logActivity(Task task, TaskActivityType activityType, String description,
                                  String oldValue, String newValue, String fieldName) {
        try {
            User currentUser = getCurrentUser();

            TaskActivity activity = TaskActivity.builder()
                    .task(task)
                    .user(currentUser)
                    .activityType(activityType)
                    .description(description)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .fieldName(fieldName)
                    .createdAt(LocalDateTime.now())
                    .build();

            TaskActivity savedActivity = taskActivityRepository.save(activity);
            System.out.println("✅ [TaskActivity] Logged: " + description + " for task " + task.getId());
            return savedActivity;
        } catch (Exception e) {
            System.err.println("❌ [TaskActivity] Error logging activity: " + e.getMessage());
            return null;
        }
    }

    /**
     * Lấy tất cả activity của một task
     */
    @Transactional(readOnly = true)
    public List<TaskActivityResponseDto> getTaskActivities(Long taskId) {
        List<TaskActivity> activities = taskActivityRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
        return activities.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Lấy activity của task với phân trang
     */
    @Transactional(readOnly = true)
    public Page<TaskActivityResponseDto> getTaskActivities(Long taskId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TaskActivity> activities = taskActivityRepository.findByTaskIdOrderByCreatedAtDesc(taskId, pageable);
        return activities.map(this::convertToDto);
    }

    /**
     * Lấy activity gần đây của task (5 hoạt động gần nhất)
     */
    @Transactional(readOnly = true)
    public List<TaskActivityResponseDto> getRecentTaskActivities(Long taskId) {
        Pageable pageable = PageRequest.of(0, 5);
        List<TaskActivity> activities = taskActivityRepository.findRecentActivityByTaskId(taskId, pageable);
        return activities.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Đếm số activity của task
     */
    @Transactional(readOnly = true)
    public Long countTaskActivities(Long taskId) {
        return taskActivityRepository.countByTaskId(taskId);
    }

    /**
     * Alias method for controller compatibility
     */
    @Transactional(readOnly = true)
    public Long getTaskActivitiesCount(Long taskId) {
        return countTaskActivities(taskId);
    }

    /**
     * Log các loại activity phổ biến
     */
    public void logTaskCreated(Task task) {
        logActivity(task, TaskActivityType.TASK_CREATED,
                   "created this task", null, null, null);
    }

    public void logStatusChanged(Task task, String oldStatus, String newStatus) {
        String description = String.format("changed status from %s to %s", oldStatus, newStatus);
        logActivity(task, TaskActivityType.STATUS_CHANGED, description, oldStatus, newStatus, "status");
    }

    public void logPriorityChanged(Task task, String oldPriority, String newPriority) {
        String description = String.format("changed priority from %s to %s", oldPriority, newPriority);
        logActivity(task, TaskActivityType.PRIORITY_CHANGED, description, oldPriority, newPriority, "priority");
    }

    public void logDeadlineChanged(Task task, String oldDeadline, String newDeadline) {
        String description = String.format("changed due date from %s to %s",
                                         oldDeadline != null ? oldDeadline : "None",
                                         newDeadline != null ? newDeadline : "None");
        logActivity(task, TaskActivityType.DEADLINE_CHANGED, description, oldDeadline, newDeadline, "deadline");
    }

    public void logTitleChanged(Task task, String oldTitle, String newTitle) {
        String description = String.format("changed title from \"%s\" to \"%s\"", oldTitle, newTitle);
        logActivity(task, TaskActivityType.TITLE_CHANGED, description, oldTitle, newTitle, "title");
    }

    public void logDescriptionChanged(Task task, String oldDesc, String newDesc) {
        logActivity(task, TaskActivityType.DESCRIPTION_CHANGED, "updated task description",
                   oldDesc, newDesc, "description");
    }

    public void logCommentChanged(Task task, String oldComment, String newComment) {
        logActivity(task, TaskActivityType.COMMENT_CHANGED, "updated task comment",
                   oldComment, newComment, "comment");
    }

    public void logAssigneeAdded(Task task, String assigneeEmail) {
        String description = String.format("added %s to this task", assigneeEmail);
        logActivity(task, TaskActivityType.ASSIGNEE_ADDED, description, null, assigneeEmail, "assignee");
    }

    public void logAssigneeRemoved(Task task, String assigneeEmail) {
        String description = String.format("removed %s from this task", assigneeEmail);
        logActivity(task, TaskActivityType.ASSIGNEE_REMOVED, description, assigneeEmail, null, "assignee");
    }

    public void logTaskCompleted(Task task) {
        logActivity(task, TaskActivityType.TASK_COMPLETED, "completed this task", null, null, null);
    }

    public void logTaskReopened(Task task) {
        logActivity(task, TaskActivityType.TASK_REOPENED, "reopened this task", null, null, null);
    }

    public void logCommentAdded(Task task, String commentContent) {
        String description = "added a comment";
        logActivity(task, TaskActivityType.COMMENT_ADDED, description, null, commentContent, "comment");
    }

    public void logProjectChanged(Task task, String oldProject, String newProject) {
        String description = String.format("moved task from \"%s\" to \"%s\"",
                                         oldProject != null ? oldProject : "None",
                                         newProject != null ? newProject : "None");
        logActivity(task, TaskActivityType.PROJECT_CHANGED, description, oldProject, newProject, "project");
    }

    public void logTeamChanged(Task task, String oldTeam, String newTeam) {
        String description = String.format("changed team from \"%s\" to \"%s\"",
                                         oldTeam != null ? oldTeam : "None",
                                         newTeam != null ? newTeam : "None");
        logActivity(task, TaskActivityType.TEAM_CHANGED, description, oldTeam, newTeam, "team");
    }

    public void logFileUploaded(Task task, String fileName) {
        String description = String.format("uploaded file \"%s\"", fileName);
        logActivity(task, TaskActivityType.FILE_ATTACHED, description, null, fileName, "file");
    }

    public void logFileDeleted(Task task, String fileName) {
        String description = String.format("deleted file \"%s\"", fileName);
        logActivity(task, TaskActivityType.FILE_DELETED, description, fileName, null, "file");
    }

    public void logMultipleFilesUploaded(Task task, int fileCount) {
        String description = String.format("uploaded %d file%s", fileCount, fileCount > 1 ? "s" : "");
        logActivity(task, TaskActivityType.FILE_ATTACHED, description, null, String.valueOf(fileCount), "files");
    }

    /**
     * Convert TaskActivity entity to DTO
     */
    private TaskActivityResponseDto convertToDto(TaskActivity activity) {
        UserProfileDto userProfile = userProfileMapper.toUserProfileDto(activity.getUser());

        return TaskActivityResponseDto.builder()
                .id(activity.getId())
                .taskId(activity.getTask().getId())
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
    private String formatActivityMessage(TaskActivity activity, UserProfileDto user) {
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
