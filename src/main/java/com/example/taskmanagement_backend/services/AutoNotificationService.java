package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.NotificationDto.CreateNotificationRequestDto;
import com.example.taskmanagement_backend.entities.*;
import com.example.taskmanagement_backend.enums.NotificationType;
import com.example.taskmanagement_backend.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/**
 * Service để xử lý các thông báo tự động
 * Bao gồm: task sắp quá hạn, task được assign, thêm vào team
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoNotificationService {

    private final TaskRepository taskRepository;
    private final TeamJpaRepository teamJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final NotificationKafkaService notificationKafkaService;
    private final NotificationService notificationService;

    /**
     * Kiểm tra và gửi thông báo cho tasks sắp quá hạn
     * Chạy mỗi giờ để kiểm tra
     */
    @Transactional
    @Scheduled(fixedRate = 3600000) // Chạy mỗi giờ
    public void checkTasksDueSoon() {
        log.info("🔍 Checking for tasks due soon...");

        LocalDate now = LocalDate.now();
        LocalDate tomorrow = now.plusDays(1);
        LocalDate in3Days = now.plusDays(3);

        // Tìm tasks sắp quá hạn trong 24h
        List<Task> tasksDueTomorrow = taskRepository.findTasksDueBetween(now, tomorrow);

        // Tìm tasks sắp quá hạn trong 3 ngày
        List<Task> tasksDueIn3Days = taskRepository.findTasksDueBetween(tomorrow, in3Days);

        // Gửi thông báo cho tasks sắp quá hạn trong 24h
        for (Task task : tasksDueTomorrow) {
            if (!task.getAssignees().isEmpty()) {
                sendTaskDueSoonNotification(task, "24 giờ");
            }
        }

        // Gửi thông báo cho tasks sắp quá hạn trong 3 ngày
        for (Task task : tasksDueIn3Days) {
            if (!task.getAssignees().isEmpty()) {
                sendTaskDueSoonNotification(task, "3 ngày");
            }
        }

        log.info("✅ Checked {} tasks due tomorrow, {} tasks due in 3 days",
                tasksDueTomorrow.size(), tasksDueIn3Days.size());
    }

    /**
     * Kiểm tra và gửi thông báo cho tasks quá hạn
     * Chạy mỗi ngày vào 9h sáng
     */
    @Transactional
    @Scheduled(cron = "0 0 9 * * ?") // 9h sáng mỗi ngày
    public void checkOverdueTasks() {
        log.info("🔍 Checking for overdue tasks...");

        LocalDate today = LocalDate.now();
        List<Task> overdueTasks = taskRepository.findOverdueTasks(today);

        for (Task task : overdueTasks) {
            if (!task.getAssignees().isEmpty()) {
                sendTaskOverdueNotification(task);
            }
        }

        log.info("✅ Checked {} overdue tasks", overdueTasks.size());
    }

    /**
     * Gửi thông báo khi task được assign cho user khác
     */
    public void sendTaskAssignedNotification(Task task, User assignedUser, User assignedBy) {
        if (assignedUser == null || assignedUser.equals(assignedBy)) {
            return; // Không gửi thông báo nếu user tự assign cho mình
        }

        CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                .userId(assignedUser.getId())
                .title("📋 Bạn được giao task mới")
                .content(String.format("%s đã giao task \"%s\" cho bạn. Deadline: %s",
                        getUserFullName(assignedBy),
                        task.getTitle(),
                        task.getDeadline() != null ? task.getDeadline().toString() : "Chưa xác định"))
                .type(NotificationType.TASK_ASSIGNED)
                .referenceId(task.getId())
                .referenceType("TASK")
                .build();

        notificationKafkaService.publishNotificationEvent(notification);
        log.info("📤 Sent task assigned notification to user {} for task {}",
                assignedUser.getId(), task.getId());
    }

    /**
     * Gửi thông báo khi user được thêm vào team
     */
    public void sendTeamMemberAddedNotification(Team team, User newMember, User addedBy) {
        if (newMember == null || newMember.equals(addedBy)) {
            return;
        }

        CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                .userId(newMember.getId())
                .title("👥 Bạn được thêm vào team mới")
                .content(String.format("%s đã thêm bạn vào team \"%s\"",
                        getUserFullName(addedBy),
                        team.getName()))
                .type(NotificationType.TEAM_MEMBER_ADDED)
                .referenceId(team.getId())
                .referenceType("TEAM")
                .build();

        notificationKafkaService.publishNotificationEvent(notification);
        log.info("📤 Sent team member added notification to user {} for team {}",
                newMember.getId(), team.getId());
    }

    /**
     * Gửi thông báo khi user được thêm vào project
     */
    public void sendProjectMemberAddedNotification(Project project, User newMember, User addedBy) {
        if (newMember == null || newMember.equals(addedBy)) {
            return;
        }

        CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                .userId(newMember.getId())
                .title("🚀 Bạn được thêm vào dự án mới")
                .content(String.format("%s đã thêm bạn vào dự án \"%s\"",
                        getUserFullName(addedBy),
                        project.getName()))
                .type(NotificationType.PROJECT_MEMBER_ADDED)
                .referenceId(project.getId())
                .referenceType("PROJECT")
                .build();

        notificationKafkaService.publishNotificationEvent(notification);
        log.info("📤 Sent project member added notification to user {} for project {}",
                newMember.getId(), project.getId());
    }

    /**
     * Gửi thông báo khi user được mời vào team
     */
    public void sendTeamInvitationNotification(Team team, User invitedUser, User invitedBy, String inviteLink) {
        if (invitedUser == null || invitedUser.equals(invitedBy)) {
            return;
        }

        CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                .userId(invitedUser.getId())
                .title("🎉 Bạn được mời vào team mới")
                .content(String.format("%s đã mời bạn tham gia team \"%s\". Nhấp vào đây để chấp nhận lời mời.",
                        getUserFullName(invitedBy),
                        team.getName()))
                .type(NotificationType.TEAM_INVITATION)
                .referenceId(team.getId())
                .referenceType("TEAM")
                .actionUrl(inviteLink)
                .build();

        notificationKafkaService.publishNotificationEvent(notification);
        log.info("📤 Sent team invitation notification to user {} for team {}",
                invitedUser.getId(), team.getId());
    }

    /**
     * Gửi thông báo task sắp quá hạn
     */
    private void sendTaskDueSoonNotification(Task task, String timeframe) {
        // Gửi thông báo cho tất cả assignees
        for (TaskAssignee assignee : task.getAssignees()) {
            CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                    .userId(assignee.getUser().getId())
                    .title("⏰ Task sắp quá hạn")
                    .content(String.format("Task \"%s\" sẽ quá hạn trong %s. Deadline: %s",
                            task.getTitle(),
                            timeframe,
                            task.getDeadline() != null ? task.getDeadline().toString() : "Chưa xác định"))
                    .type(NotificationType.TASK_DUE_SOON)
                    .referenceId(task.getId())
                    .referenceType("TASK")
                    .priority(2) // HIGH priority
                    .build();

            notificationKafkaService.publishNotificationEvent(notification);
        }
    }

    /**
     * Gửi thông báo task quá hạn
     */
    private void sendTaskOverdueNotification(Task task) {
        // Gửi thông báo cho tất cả assignees
        for (TaskAssignee assignee : task.getAssignees()) {
            CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                    .userId(assignee.getUser().getId())
                    .title("🚨 Task đã quá hạn")
                    .content(String.format("Task \"%s\" đã quá hạn từ %s. Vui lòng hoàn thành sớm!",
                            task.getTitle(),
                            task.getDeadline() != null ? task.getDeadline().toString() : "N/A"))
                    .type(NotificationType.TASK_OVERDUE)
                    .referenceId(task.getId())
                    .referenceType("TASK")
                    .priority(3) // URGENT priority
                    .build();

            notificationKafkaService.publishNotificationEvent(notification);
        }
    }

    /**
     * Gửi thông báo khi task được comment
     */
    public void sendTaskCommentNotification(Task task, User commenter, String commentContent) {
        for (TaskAssignee assignee : task.getAssignees()) {
            if (assignee.getUser().equals(commenter)) {
                continue; // Không gửi thông báo cho chính người comment
            }

            CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                    .userId(assignee.getUser().getId())
                    .title("💬 Comment mới trên task")
                    .content(String.format("%s đã comment trên task \"%s\": %s",
                            getUserFullName(commenter),
                            task.getTitle(),
                            commentContent.length() > 50 ? commentContent.substring(0, 50) + "..." : commentContent))
                    .type(NotificationType.TASK_COMMENT)
                    .referenceId(task.getId())
                    .referenceType("TASK")
                    .build();

            notificationKafkaService.publishNotificationEvent(notification);
            log.info("📤 Sent task comment notification to user {} for task {}",
                    assignee.getUser().getId(), task.getId());
        }
    }

    /**
     * Gửi thông báo khi task được update
     */
    public void sendTaskUpdatedNotification(Task task, User updatedBy, String updateDetails) {
        for (TaskAssignee assignee : task.getAssignees()) {
            if (assignee.getUser().equals(updatedBy)) {
                continue; // Không gửi thông báo cho chính người update
            }

            CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                    .userId(assignee.getUser().getId())
                    .title("📝 Task được cập nhật")
                    .content(String.format("%s đã cập nhật task \"%s\": %s",
                            getUserFullName(updatedBy),
                            task.getTitle(),
                            updateDetails))
                    .type(NotificationType.TASK_UPDATED)
                    .referenceId(task.getId())
                    .referenceType("TASK")
                    .build();

            notificationKafkaService.publishNotificationEvent(notification);
            log.info("📤 Sent task updated notification to user {} for task {}",
                    assignee.getUser().getId(), task.getId());
        }
    }

    /**
     * Helper method to get user's full name
     */
    private String getUserFullName(User user) {
        String firstName = user.getFirstName();
        String lastName = user.getLastName();

        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return user.getEmail(); // Fallback to email if no name available
        }
    }
}
