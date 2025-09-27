package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.TaskCommentDto.CreateTaskCommentRequestDto;
import com.example.taskmanagement_backend.dtos.TaskCommentDto.TaskCommentResponseDto;
import com.example.taskmanagement_backend.dtos.TaskCommentDto.UpdateTaskCommentRequestDto;
import com.example.taskmanagement_backend.entities.ProjectTask;
import com.example.taskmanagement_backend.entities.ProjectTaskComment;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.repositories.ProjectTaskCommentRepository;
import com.example.taskmanagement_backend.repositories.ProjectTaskJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ProjectTaskCommentService {

    @Autowired
    private ProjectTaskCommentRepository projectTaskCommentRepository;

    @Autowired
    private ProjectTaskJpaRepository projectTaskJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private TaskActivityService taskActivityService;

    @Autowired
    private ProjectTaskActivityService projectTaskActivityService;

    /**
     * Tạo comment mới cho project task
     */
    public TaskCommentResponseDto createComment(CreateTaskCommentRequestDto dto) {
        User currentUser = getCurrentUser();

        // Tìm project task
        ProjectTask projectTask = projectTaskJpaRepository.findById(dto.getTaskId())
                .orElseThrow(() -> new EntityNotFoundException("Project task not found with ID: " + dto.getTaskId()));

        // Kiểm tra quyền comment
        if (!canUserCommentOnProjectTask(currentUser, projectTask)) {
            throw new SecurityException("You don't have permission to comment on this project task");
        }

        // Tạo comment mới
        ProjectTaskComment comment = ProjectTaskComment.builder()
                .content(dto.getContent())
                .projectTask(projectTask)
                .user(currentUser)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        projectTaskCommentRepository.save(comment);
        log.info("✅ Comment saved successfully with ID: {}", comment.getId());

        // Log activity - Ghi log hoạt động khi thêm comment
        try {
            log.info("🔔 [ProjectTaskCommentService] About to log comment activity for project task ID: {}", projectTask.getId());
            projectTaskActivityService.logProjectTaskCommentAdded(projectTask, dto.getContent());
            log.info("✅ [ProjectTaskCommentService] Successfully logged comment activity");
        } catch (Exception e) {
            log.error("❌ [ProjectTaskCommentService] Failed to log comment activity: {}", e.getMessage(), e);
            // Don't fail the comment creation if activity logging fails
        }

        log.info("✅ User {} created comment for project task {}", currentUser.getEmail(), projectTask.getId());
        return mapToResponseDto(comment);
    }

    /**
     * Lấy tất cả comments của project task
     */
    public List<TaskCommentResponseDto> getCommentsByProjectTask(Long projectTaskId) {
        ProjectTask projectTask = projectTaskJpaRepository.findById(projectTaskId)
                .orElseThrow(() -> new EntityNotFoundException("Project task not found with ID: " + projectTaskId));

        User currentUser = getCurrentUser();
        if (!canUserCommentOnProjectTask(currentUser, projectTask)) {
            throw new SecurityException("You don't have permission to view comments of this project task");
        }

        List<ProjectTaskComment> comments = projectTaskCommentRepository.findByProjectTaskOrderByCreatedAtAsc(projectTask);
        return comments.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Lấy comments với phân trang
     */
    public Page<TaskCommentResponseDto> getCommentsByProjectTaskPaginated(Long projectTaskId, int page, int size) {
        ProjectTask projectTask = projectTaskJpaRepository.findById(projectTaskId)
                .orElseThrow(() -> new EntityNotFoundException("Project task not found with ID: " + projectTaskId));

        User currentUser = getCurrentUser();
        if (!canUserCommentOnProjectTask(currentUser, projectTask)) {
            throw new SecurityException("You don't have permission to view comments of this project task");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<ProjectTaskComment> comments = projectTaskCommentRepository.findByProjectTaskOrderByCreatedAtDesc(projectTask, pageable);

        return comments.map(this::mapToResponseDto);
    }

    /**
     * Đếm số comments của project task
     */
    public long getCommentCountByProjectTask(Long projectTaskId) {
        ProjectTask projectTask = projectTaskJpaRepository.findById(projectTaskId)
                .orElseThrow(() -> new EntityNotFoundException("Project task not found with ID: " + projectTaskId));

        return projectTaskCommentRepository.countByProjectTask(projectTask);
    }

    /**
     * Lấy comment theo ID
     */
    public TaskCommentResponseDto getCommentById(Long commentId) {
        ProjectTaskComment comment = projectTaskCommentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found with ID: " + commentId));

        User currentUser = getCurrentUser();
        if (!canUserCommentOnProjectTask(currentUser, comment.getProjectTask())) {
            throw new SecurityException("You don't have permission to view this comment");
        }

        return mapToResponseDto(comment);
    }

    /**
     * Cập nhật comment
     */
    public TaskCommentResponseDto updateComment(Long commentId, UpdateTaskCommentRequestDto dto) {
        ProjectTaskComment comment = projectTaskCommentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found with ID: " + commentId));

        User currentUser = getCurrentUser();

        // Chỉ người tạo comment mới được sửa
        if (!comment.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only edit your own comments");
        }

        // Store old content for activity logging
        String oldContent = comment.getContent();

        // Cập nhật content
        if (dto.getContent() != null) {
            comment.setContent(dto.getContent());
            comment.setUpdatedAt(LocalDateTime.now());
        }

        projectTaskCommentRepository.save(comment);

        // Log activity - Ghi log hoạt động khi cập nhật comment
        try {
            log.info("🔔 [ProjectTaskCommentService] About to log comment update activity for project task ID: {}", comment.getProjectTask().getId());
            projectTaskActivityService.logActivity(comment.getProjectTask(),
                com.example.taskmanagement_backend.enums.TaskActivityType.COMMENT_CHANGED,
                "updated a comment", oldContent, dto.getContent(), "comment");
            log.info("✅ [ProjectTaskCommentService] Successfully logged comment update activity");
        } catch (Exception e) {
            log.error("❌ [ProjectTaskCommentService] Failed to log comment update activity: {}", e.getMessage(), e);
            // Don't fail the comment update if activity logging fails
        }

        log.info("✅ User {} updated project task comment {}", currentUser.getEmail(), commentId);
        return mapToResponseDto(comment);
    }

    /**
     * Xóa comment
     */
    public void deleteComment(Long commentId) {
        ProjectTaskComment comment = projectTaskCommentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found with ID: " + commentId));

        User currentUser = getCurrentUser();

        // Kiểm tra quyền: người tạo comment hoặc creator của project task
        boolean isCommentOwner = comment.getUser().getId().equals(currentUser.getId());
        boolean isProjectTaskCreator = comment.getProjectTask().getCreator().getId().equals(currentUser.getId());

        if (!isCommentOwner && !isProjectTaskCreator) {
            throw new SecurityException("You don't have permission to delete this comment");
        }

        projectTaskCommentRepository.delete(comment);
        log.info("✅ User {} deleted project task comment {}", currentUser.getEmail(), commentId);
    }

    /**
     * Lấy comments gần đây (5 comments mới nhất)
     */
    public List<TaskCommentResponseDto> getRecentCommentsByProjectTask(Long projectTaskId) {
        ProjectTask projectTask = projectTaskJpaRepository.findById(projectTaskId)
                .orElseThrow(() -> new EntityNotFoundException("Project task not found with ID: " + projectTaskId));

        User currentUser = getCurrentUser();
        if (!canUserCommentOnProjectTask(currentUser, projectTask)) {
            throw new SecurityException("You don't have permission to view comments of this project task");
        }

        Pageable pageable = PageRequest.of(0, 5);
        Page<ProjectTaskComment> recentComments = projectTaskCommentRepository.findByProjectTaskOrderByCreatedAtDesc(projectTask, pageable);

        return recentComments.getContent().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Lấy comments của user hiện tại trong project task
     */
    public List<TaskCommentResponseDto> getCurrentUserCommentsByProjectTask(Long projectTaskId) {
        ProjectTask projectTask = projectTaskJpaRepository.findById(projectTaskId)
                .orElseThrow(() -> new EntityNotFoundException("Project task not found with ID: " + projectTaskId));

        User currentUser = getCurrentUser();
        if (!canUserCommentOnProjectTask(currentUser, projectTask)) {
            throw new SecurityException("You don't have permission to view comments of this project task");
        }

        List<ProjectTaskComment> userComments = projectTaskCommentRepository.findByProjectTaskAndUserOrderByCreatedAtDesc(projectTask, currentUser);

        return userComments.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Tìm kiếm comments theo nội dung
     */
    public List<TaskCommentResponseDto> searchCommentsInProjectTask(Long projectTaskId, String keyword) {
        ProjectTask projectTask = projectTaskJpaRepository.findById(projectTaskId)
                .orElseThrow(() -> new EntityNotFoundException("Project task not found with ID: " + projectTaskId));

        User currentUser = getCurrentUser();
        if (!canUserCommentOnProjectTask(currentUser, projectTask)) {
            throw new SecurityException("You don't have permission to view comments of this project task");
        }

        List<ProjectTaskComment> searchResults = projectTaskCommentRepository.findByProjectTaskAndContentContainingIgnoreCaseOrderByCreatedAtDesc(projectTask, keyword);

        return searchResults.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Helper methods
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        return userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));
    }

    private boolean canUserCommentOnProjectTask(User user, ProjectTask projectTask) {
        try {
            // User có thể comment nếu là creator, assignee hoặc additional assignee của project task
            boolean isCreator = projectTask.getCreator().getId().equals(user.getId());
            if (isCreator) return true;

            // Kiểm tra assignee chính
            if (projectTask.getAssignee() != null && projectTask.getAssignee().getId().equals(user.getId())) {
                return true;
            }

            // Kiểm tra additional assignees
            if (projectTask.getAdditionalAssignees() != null) {
                return projectTask.getAdditionalAssignees().stream()
                        .anyMatch(assignee -> assignee.getId().equals(user.getId()));
            }

            return false;
        } catch (Exception e) {
            log.error("❌ Error checking user comment permission for project task {} and user {}: {}",
                     projectTask.getId(), user.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Convert ProjectTask to Task for activity logging
     */
    private Task convertProjectTaskToTask(ProjectTask projectTask) {
        return Task.builder()
                .id(projectTask.getId())
                .title(projectTask.getTitle())
                .description(projectTask.getDescription())
                .statusKey(projectTask.getStatus() != null ? projectTask.getStatus().toString() : null)
                .priorityKey(projectTask.getPriority() != null ? projectTask.getPriority().toString() : null)
                .startDate(projectTask.getStartDate())
                .deadline(projectTask.getDeadline())
                .createdAt(projectTask.getCreatedAt())
                .updatedAt(projectTask.getUpdatedAt())
                .creator(projectTask.getCreator())
                .project(projectTask.getProject())
                .build();
    }

    private TaskCommentResponseDto mapToResponseDto(ProjectTaskComment comment) {
        User user = comment.getUser();

        // Lấy thông tin từ UserProfile nếu có
        String userName = "Unknown User";
        String userAvatar = null;

        if (user.getUserProfile() != null) {
            String firstName = user.getUserProfile().getFirstName();
            String lastName = user.getUserProfile().getLastName();

            // Tạo full name từ firstName và lastName
            if (firstName != null && lastName != null) {
                userName = firstName + " " + lastName;
            } else if (firstName != null) {
                userName = firstName;
            } else if (lastName != null) {
                userName = lastName;
            }

            userAvatar = user.getAvatarUrl();
        }

        return TaskCommentResponseDto.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .taskId(comment.getProjectTask().getId()) // Using project task ID
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userName(userName)
                .userAvatar(userAvatar)
                .build();
    }
}
