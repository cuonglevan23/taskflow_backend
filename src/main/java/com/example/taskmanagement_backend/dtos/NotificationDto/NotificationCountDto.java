package com.example.taskmanagement_backend.dtos.NotificationDto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCountDto {

    private Long userId;

    private Long unreadCount;

    private Long totalCount;

    // Breakdown by notification type for better UX
    private Long unreadChatMessages;

    private Long unreadTaskNotifications;

    private Long unreadSystemNotifications;

    private Long unreadFriendRequests;

    private Long unreadMeetingInvitations;

    // ✅ NEW: Bookmark and Archive counts for inbox functionality
    private Long bookmarkedCount;

    private Long archivedCount;
}
