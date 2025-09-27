package com.example.taskmanagement_backend.enums;

public enum ProjectTimelineEventType {
    PROJECT_CREATED("Project Created"),
    PROJECT_NAME_CHANGED("Project Name Changed"),
    PROJECT_DESCRIPTION_CHANGED("Project Description Changed"),
    PROJECT_STATUS_CHANGED("Project Status Changed"),
    PROJECT_START_DATE_CHANGED("Project Start Date Changed"),
    PROJECT_END_DATE_CHANGED("Project End Date Changed"),
    PROJECT_OWNER_CHANGED("Project Owner Changed"),
    PROJECT_UPDATED("Project Updated"),
    PROJECT_DELETED("Project Deleted");

    private final String description;

    ProjectTimelineEventType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
