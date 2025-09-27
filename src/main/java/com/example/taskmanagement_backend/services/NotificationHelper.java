package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.NotificationDto.CreateNotificationRequestDto;
import com.example.taskmanagement_backend.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility service for creating notifications from other services
 * Provides convenient methods for common notification scenarios
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationHelper {

    private final NotificationService notificationService;
    private final NotificationKafkaService kafkaService;

    /**
     * Create task assignment notification
     */
    public void notifyTaskAssigned(Long userId, Long taskId, String taskTitle, String assignerName) {
        CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                .userId(userId)
                .title("New Task Assigned")
                .content("You have been assigned to task: " + taskTitle)
                .type(NotificationType.TASK_ASSIGNED)
                .referenceId(taskId)
                .referenceType("TASK")
                .senderName(assignerName)
                .actionUrl("/tasks/" + taskId)
                .metadata(Map.of("taskTitle", taskTitle, "assignerName", assignerName))
                .build();

        notificationService.createAndSendNotification(notification);
    }

    /**
     * Create friend request notification
     */
    public void notifyFriendRequest(Long userId, Long requesterId, String requesterName, String requesterAvatar) {
        CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                .userId(userId)
                .title("New Friend Request")
                .content(requesterName + " sent you a friend request")
                .type(NotificationType.FRIEND_REQUEST)
                .referenceId(requesterId)
                .referenceType("USER")
                .senderName(requesterName)
                .avatarUrl(requesterAvatar)
                .actionUrl("/friends/requests")
                .metadata(Map.of("requesterId", requesterId.toString(), "requesterName", requesterName))
                .build();

        notificationService.createAndSendNotification(notification);
    }

    /**
     * Create chat message notification
     */
    public void notifyChatMessage(Long userId, Long senderId, String senderName, String message, String chatId) {
        CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                .userId(userId)
                .title("New Message from " + senderName)
                .content(message.length() > 50 ? message.substring(0, 50) + "..." : message)
                .type(NotificationType.CHAT_MESSAGE)
                .referenceId(Long.valueOf(chatId))
                .referenceType("CHAT")
                .senderName(senderName)
                .actionUrl("/chat/" + chatId)
                .metadata(Map.of("senderId", senderId.toString(), "senderName", senderName, "chatId", chatId))
                .build();

        // Use Kafka for chat messages to ensure scalability
        kafkaService.publishNotificationEvent(notification);
    }

    /**
     * Create project invitation notification
     */
    public void notifyProjectInvitation(Long userId, Long projectId, String projectName, String inviterName) {
        CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                .userId(userId)
                .title("Project Invitation")
                .content("You've been invited to join project: " + projectName)
                .type(NotificationType.PROJECT_INVITATION)
                .referenceId(projectId)
                .referenceType("PROJECT")
                .senderName(inviterName)
                .actionUrl("/projects/" + projectId)
                .metadata(Map.of("projectName", projectName, "inviterName", inviterName))
                .build();

        notificationService.createAndSendNotification(notification);
    }

    /**
     * Create system maintenance notification for all users
     */
    public void notifySystemMaintenance(List<Long> userIds, String message, String startTime) {
        userIds.forEach(userId -> {
            CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                    .userId(userId)
                    .title("System Maintenance")
                    .content(message)
                    .type(NotificationType.SYSTEM_MAINTENANCE)
                    .senderName("System")
                    .expiresInHours(24)
                    .metadata(Map.of("maintenanceTime", startTime))
                    .build();

            kafkaService.publishSystemNotificationEvent(notification);
        });
    }

    /**
     * Create meeting reminder notification
     */
    public void notifyMeetingReminder(Long userId, Long meetingId, String meetingTitle, String startTime) {
        CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                .userId(userId)
                .title("Meeting Reminder")
                .content("Meeting '" + meetingTitle + "' starts at " + startTime)
                .type(NotificationType.MEETING_REMINDER)
                .referenceId(meetingId)
                .referenceType("MEETING")
                .senderName("Calendar")
                .actionUrl("/meetings/" + meetingId)
                .metadata(Map.of("meetingTitle", meetingTitle, "startTime", startTime))
                .build();

        notificationService.createAndSendNotification(notification);
    }

    /**
     * Create task due soon notification
     */
    public void notifyTaskDueSoon(Long userId, Long taskId, String taskTitle, String dueDate) {
        CreateNotificationRequestDto notification = CreateNotificationRequestDto.builder()
                .userId(userId)
                .title("Task Due Soon")
                .content("Task '" + taskTitle + "' is due on " + dueDate)
                .type(NotificationType.TASK_DUE_SOON)
                .referenceId(taskId)
                .referenceType("TASK")
                .senderName("System")
                .actionUrl("/tasks/" + taskId)
                .priority(1) // High priority
                .metadata(Map.of("taskTitle", taskTitle, "dueDate", dueDate))
                .build();

        notificationService.createAndSendNotification(notification);
    }
}
