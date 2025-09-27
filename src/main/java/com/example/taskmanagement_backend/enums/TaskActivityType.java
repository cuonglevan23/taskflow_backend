package com.example.taskmanagement_backend.enums;

public enum TaskActivityType {
    // Basic task operations
    TASK_CREATED,
    TASK_UPDATED,
    TASK_DELETED,
    TASK_COMPLETED,
    TASK_REOPENED,

    // Field changes
    STATUS_CHANGED,
    PRIORITY_CHANGED,
    TITLE_CHANGED,
    DESCRIPTION_CHANGED,
    DEADLINE_CHANGED,

    // Assignment operations
    ASSIGNEE_ADDED,
    ASSIGNEE_REMOVED,

    // Comment operations
    COMMENT_ADDED,
    COMMENT_CHANGED,
    COMMENT_DELETED,

    // File operations
    FILE_ATTACHED,
    FILE_DELETED,
    FILE_UPLOADED,

    // Project/Team changes
    PROJECT_CHANGED,
    TEAM_CHANGED,

    // Calendar operations
    CALENDAR_UPDATED,
    CALENDAR_EVENT_CREATED,
    CALENDAR_EVENT_UPDATED,
    CALENDAR_EVENT_DELETED
}
