package com.example.taskmanagement_backend.entities;

import com.example.taskmanagement_backend.enums.TaskPriority;
import com.example.taskmanagement_backend.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "project_tasks", indexes = {
    @Index(name = "idx_project_task_project", columnList = "project_id"),
    @Index(name = "idx_project_task_creator", columnList = "creator_id"),
    @Index(name = "idx_project_task_assignee", columnList = "assignee_id"),
    @Index(name = "idx_project_task_status", columnList = "status"),
    @Index(name = "idx_project_task_priority", columnList = "priority"),
    @Index(name = "idx_project_task_deadline", columnList = "deadline"),
    @Index(name = "idx_project_task_updated", columnList = "updated_at"),
    @Index(name = "idx_project_task_parent", columnList = "parent_task_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "estimated_hours")
    private Integer estimatedHours;

    @Column(name = "actual_hours")
    private Integer actualHours;

    @Column(name = "progress_percentage")
    @Builder.Default
    private Integer progressPercentage = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // File handling fields
    @Column(name = "url_file", length = 1000)
    private String urlFile;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    // Google Calendar integration fields
    @Column(name = "google_calendar_event_id")
    private String googleCalendarEventId;

    @Column(name = "google_calendar_event_url")
    private String googleCalendarEventUrl;

    @Column(name = "google_meet_link")
    private String googleMeetLink;

    @Column(name = "is_synced_to_calendar", columnDefinition = "bit default false")
    @Builder.Default
    private Boolean isSyncedToCalendar = false;

    @Column(name = "calendar_synced_at")
    private LocalDateTime calendarSyncedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee; // Primary assignee

    // Additional assignees (many-to-many)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "project_task_assignees",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> additionalAssignees;

    // Parent-child relationship for subtasks
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private ProjectTask parentTask;

    @OneToMany(mappedBy = "parentTask", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProjectTask> subtasks;

    // Project Task Activities relationship - CASCADE DELETE
    @OneToMany(mappedBy = "projectTask", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProjectTaskActivity> activities;

    // TODO: Task dependencies/connections will be implemented later
    // when timeline feature is fully developed

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}