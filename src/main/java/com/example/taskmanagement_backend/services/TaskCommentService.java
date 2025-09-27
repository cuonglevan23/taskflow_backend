package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.TaskCommentDto.CreateTaskCommentRequestDto;
import com.example.taskmanagement_backend.dtos.TaskCommentDto.TaskCommentResponseDto;
import com.example.taskmanagement_backend.dtos.TaskCommentDto.UpdateTaskCommentRequestDto;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.TaskAssignee;
import com.example.taskmanagement_backend.entities.TaskComment;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.ProjectTask;
import com.example.taskmanagement_backend.entities.ProjectTaskComment;
import com.example.taskmanagement_backend.repositories.TaskCommentRepository;
import com.example.taskmanagement_backend.repositories.ProjectTaskCommentRepository;
import com.example.taskmanagement_backend.repositories.TaskJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.repositories.TasksAssigneeJpaRepository;
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
public class TaskCommentService {

    @Autowired
    private TaskCommentRepository taskCommentRepository;

    @Autowired
    private TaskJpaRepository taskJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private TasksAssigneeJpaRepository tasksAssigneeJpaRepository;

    @Autowired
    private TaskActivityService taskActivityService;

    @Autowired
    private com.example.taskmanagement_backend.repositories.ProjectTaskJpaRepository projectTaskJpaRepository;

    @Autowired
    private ProjectTaskCommentRepository projectTaskCommentRepository;

    /**
     * ✅ UPDATED: Tạo comment mới cho task (hỗ trợ cả regular task và project task)
     */
    public TaskCommentResponseDto createComment(CreateTaskCommentRequestDto dto) {
        // Lấy current user từ security context
        User currentUser = getCurrentUser();

        // Thử tìm regular task trước
        Task regularTask = taskJpaRepository.findById(dto.getTaskId()).orElse(null);
        if (regularTask != null) {
            // Kiểm tra user có quyền comment không (creator hoặc assignee)
            if (!canUserCommentOnTask(currentUser, regularTask)) {
                throw new SecurityException("You don't have permission to comment on this task");
            }

            // Tạo comment mới cho regular task
            TaskComment comment = TaskComment.builder()
                    .content(dto.getContent())
                    .task(regularTask)
                    .user(currentUser)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            taskCommentRepository.save(comment);

            // ✅ Log activity when comment is added
            taskActivityService.logCommentAdded(regularTask, dto.getContent());

            log.info("✅ User {} created comment for regular task {}", currentUser.getEmail(), regularTask.getId());
            return mapToResponseDto(comment);
        }

        // Nếu không tìm thấy regular task, thử tìm project task
        ProjectTask projectTask = projectTaskJpaRepository.findById(dto.getTaskId()).orElse(null);
        if (projectTask != null) {
            // Kiểm tra user có quyền comment không (creator hoặc assignee)
            if (!canUserCommentOnProjectTask(currentUser, projectTask)) {
                throw new SecurityException("You don't have permission to comment on this project task");
            }

            // Tạo comment mới cho project task
            ProjectTaskComment projectComment = ProjectTaskComment.builder()
                    .content(dto.getContent())
                    .projectTask(projectTask)
                    .user(currentUser)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            projectTaskCommentRepository.save(projectComment);

            // ✅ Log activity when comment is added to project task
            Task taskForActivity = convertProjectTaskToTask(projectTask);
            taskActivityService.logCommentAdded(taskForActivity, dto.getContent());

            log.info("✅ User {} created comment for project task {}", currentUser.getEmail(), projectTask.getId());
            return mapProjectTaskCommentToResponseDto(projectComment);
        }

        // Nếu không tìm thấy trong cả hai bảng
        throw new EntityNotFoundException("Task not found with ID: " + dto.getTaskId());
    }

    /**
     * Lấy tất cả comments của 1 task
     */
    public List<TaskCommentResponseDto> getCommentsByTask(Long taskId) {
        User currentUser = getCurrentUser();

        // Thử tìm regular task trước
        Task regularTask = taskJpaRepository.findById(taskId).orElse(null);
        if (regularTask != null) {
            if (!canUserViewTask(currentUser, regularTask)) {
                throw new SecurityException("You don't have permission to view comments of this task");
            }

            List<TaskComment> comments = taskCommentRepository.findByTaskOrderByCreatedAtAsc(regularTask);
            return comments.stream()
                    .map(this::mapToResponseDto)
                    .collect(Collectors.toList());
        }

        // Nếu không tìm thấy regular task, thử tìm project task
        ProjectTask projectTask = projectTaskJpaRepository.findById(taskId).orElse(null);
        if (projectTask != null) {
            if (!canUserCommentOnProjectTask(currentUser, projectTask)) {
                throw new SecurityException("You don't have permission to view comments of this project task");
            }

            List<ProjectTaskComment> projectComments = projectTaskCommentRepository.findByProjectTaskOrderByCreatedAtAsc(projectTask);
            return projectComments.stream()
                    .map(this::mapProjectTaskCommentToResponseDto)
                    .collect(Collectors.toList());
        }

        throw new EntityNotFoundException("Task not found with ID: " + taskId);
    }

    /**
     * ✅ UPDATED: Đếm số comments của 1 task (hỗ trợ cả regular task và project task)
     */
    public long getCommentCountByTask(Long taskId) {
        // Thử tìm regular task trước
        try {
            Task task = taskJpaRepository.findById(taskId).orElse(null);
            if (task != null) {
                return taskCommentRepository.countByTask(task);
            }
        } catch (Exception e) {
            log.debug("Task with ID {} not found in regular tasks table", taskId);
        }

        // Nếu không tìm thấy regular task, kiểm tra xem có phải project task không
        try {
            ProjectTask projectTask = projectTaskJpaRepository.findById(taskId).orElse(null);
            if (projectTask != null) {
                // ✅ Bây giờ project tasks có hỗ trợ comments
                return projectTaskCommentRepository.countByProjectTask(projectTask);
            }
        } catch (Exception e) {
            log.debug("Task with ID {} not found in project tasks table either", taskId);
        }

        // Nếu không tìm thấy trong cả hai bảng
        throw new EntityNotFoundException("Task not found with ID: " + taskId);
    }

    /**
     * ✅ NEW: Lấy comments của task (hỗ trợ cả regular task và project task) với phân trang
     */
    public Page<TaskCommentResponseDto> getCommentsByTaskPaginated(Long taskId, int page, int size) {
        User currentUser = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size);

        // Thử tìm regular task trước
        Task regularTask = taskJpaRepository.findById(taskId).orElse(null);
        if (regularTask != null) {
            if (!canUserViewTask(currentUser, regularTask)) {
                throw new SecurityException("You don't have permission to view comments of this task");
            }

            Page<TaskComment> comments = taskCommentRepository.findByTaskOrderByCreatedAtDesc(regularTask, pageable);
            return comments.map(this::mapToResponseDto);
        }

        // Nếu không tìm thấy regular task, thử tìm project task
        ProjectTask projectTask = projectTaskJpaRepository.findById(taskId).orElse(null);
        if (projectTask != null) {
            if (!canUserCommentOnProjectTask(currentUser, projectTask)) {
                throw new SecurityException("You don't have permission to view comments of this project task");
            }

            Page<ProjectTaskComment> projectComments = projectTaskCommentRepository.findByProjectTaskOrderByCreatedAtDesc(projectTask, pageable);
            return projectComments.map(this::mapProjectTaskCommentToResponseDto);
        }

        throw new EntityNotFoundException("Task not found with ID: " + taskId);
    }

    /**
     * Cập nhật comment (chỉ người tạo comment mới được sửa)
     */
    public TaskCommentResponseDto updateComment(Long commentId, UpdateTaskCommentRequestDto dto) {
        TaskComment comment = taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        User currentUser = getCurrentUser();

        // Chỉ người tạo comment mới được sửa
        if (!comment.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only edit your own comments");
        }

        // Cập nhật content
        if (dto.getContent() != null) {
            comment.setContent(dto.getContent());
            comment.setUpdatedAt(LocalDateTime.now());
        }

        taskCommentRepository.save(comment);

        log.info("✅ User {} updated comment {}", currentUser.getEmail(), commentId);
        return mapToResponseDto(comment);
    }

    /**
     * Xóa comment (chỉ người tạo comment hoặc creator của task)
     */
    public void deleteComment(Long commentId) {
        TaskComment comment = taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        User currentUser = getCurrentUser();

        // Kiểm tra quyền: người tạo comment hoặc creator của task
        boolean isCommentOwner = comment.getUser().getId().equals(currentUser.getId());
        boolean isTaskCreator = comment.getTask().getCreator().getId().equals(currentUser.getId());

        if (!isCommentOwner && !isTaskCreator) {
            throw new SecurityException("You don't have permission to delete this comment");
        }

        taskCommentRepository.delete(comment);
        log.info("✅ User {} deleted comment {}", currentUser.getEmail(), commentId);
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

    private boolean canUserCommentOnTask(User user, Task task) {
        try {
            // User có thể comment nếu là creator hoặc assignee của task
            boolean isCreator = task.getCreator().getId().equals(user.getId());
            if (isCreator) return true;

            // ✅ FIX: Sử dụng repository query thay vì lazy loading để tránh lỗi
            List<TaskAssignee> taskAssignees = tasksAssigneeJpaRepository.findByTask(task);
            return taskAssignees.stream()
                    .anyMatch(assignee -> assignee.getUser().getId().equals(user.getId()));
        } catch (Exception e) {
            log.error("❌ Error checking user comment permission for task {} and user {}: {}",
                     task.getId(), user.getId(), e.getMessage());
            return false;
        }
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

    private boolean canUserViewTask(User user, Task task) {
        return canUserCommentOnTask(user, task);
    }

    /**
     * ✅ Helper: Convert ProjectTask to Task for activity logging
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

    private TaskCommentResponseDto mapToResponseDto(TaskComment comment) {
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
                .taskId(comment.getTask().getId())
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userName(userName)
                .userAvatar(userAvatar)
                .build();
    }

    private TaskCommentResponseDto mapProjectTaskCommentToResponseDto(ProjectTaskComment projectComment) {
        User user = projectComment.getUser();

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
                .id(projectComment.getId())
                .content(projectComment.getContent())
                .createdAt(projectComment.getCreatedAt())
                .updatedAt(projectComment.getUpdatedAt())
                .taskId(projectComment.getProjectTask().getId())
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userName(userName)
                .userAvatar(userAvatar)
                .build();
    }
}
