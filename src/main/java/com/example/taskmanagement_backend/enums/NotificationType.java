package com.example.taskmanagement_backend.enums;

/**
 * Notification types for the real-time notification system
 * Supports chat messages, system notifications, friend requests, and more
 */
public enum NotificationType {
    // Chat related
    CHAT_MESSAGE("Chat Message"),
    CHAT_MESSAGE_REACTION("Message Reaction"),
    CHAT_MENTION("Mentioned in Chat"),

    // Task related
    TASK_ASSIGNED("Task Assigned"),
    TASK_COMPLETED("Task Completed"),
    TASK_UPDATED("Task Updated"),
    TASK_COMMENT("Task Comment"),
    TASK_DUE_SOON("Task Due Soon"),
    TASK_OVERDUE("Task Overdue"),

    // Project related
    PROJECT_INVITATION("Project Invitation"),
    PROJECT_UPDATED("Project Updated"),
    PROJECT_MEMBER_ADDED("Project Member Added"),
    PROJECT_DEADLINE_APPROACHING("Project Deadline Approaching"),

    // Team related
    TEAM_INVITATION("Team Invitation"),
    TEAM_MEMBER_ADDED("Team Member Added"),
    TEAM_UPDATED("Team Updated"),

    // Friend related
    FRIEND_REQUEST("Friend Request"),
    FRIEND_REQUEST_ACCEPTED("Friend Request Accepted"),

    // System notifications
    SYSTEM_MAINTENANCE("System Maintenance"),
    SYSTEM_UPDATE("System Update"),
    SYSTEM_ANNOUNCEMENT("System Announcement"),

    // Post related (social features)
    POST_LIKE("Post Liked"),
    POST_COMMENT("Post Comment"),
    POST_MENTION("Mentioned in Post"),

    // Meeting related
    MEETING_INVITATION("Meeting Invitation"),
    MEETING_REMINDER("Meeting Reminder"),
    MEETING_UPDATED("Meeting Updated"),
    MEETING_CANCELLED("Meeting Cancelled"),

    // Calendar related
    CALENDAR_EVENT("Calendar Event"),
    CALENDAR_REMINDER("Calendar Reminder");

    private final String displayName;

    NotificationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this notification type should be sent in real-time
     */
    public boolean isRealTime() {
        return switch (this) {
            case CHAT_MESSAGE, CHAT_MESSAGE_REACTION, CHAT_MENTION,
                 TASK_ASSIGNED, FRIEND_REQUEST, MEETING_INVITATION -> true;
            default -> false;
        };
    }

    /**
     * Check if this notification type should trigger push notifications for offline users
     */
    public boolean isPushEnabled() {
        return switch (this) {
            case CHAT_MESSAGE, TASK_ASSIGNED, TASK_DUE_SOON, TASK_OVERDUE,
                 PROJECT_INVITATION, TEAM_INVITATION, FRIEND_REQUEST,
                 MEETING_INVITATION, MEETING_REMINDER -> true;
            default -> false;
        };
    }

    /**
     * Get priority level for notifications (0=normal, 1=high, 2=urgent)
     */
    public int getPriority() {
        return switch (this) {
            case TASK_OVERDUE, MEETING_REMINDER, SYSTEM_MAINTENANCE -> 2; // Urgent
            case TASK_DUE_SOON, TASK_ASSIGNED, PROJECT_DEADLINE_APPROACHING,
                 MEETING_INVITATION -> 1; // High
            default -> 0; // Normal
        };
    }
}
