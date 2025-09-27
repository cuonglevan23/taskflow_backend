package com.example.taskmanagement_backend.entities;

import com.example.taskmanagement_backend.enums.TaskPriority;
import com.example.taskmanagement_backend.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tasks", indexes = {
    @Index(name = "idx_task_creator", columnList = "creator_id"),
    @Index(name = "idx_task_project", columnList = "project_id"),
    @Index(name = "idx_task_team", columnList = "team_id"),
    @Index(name = "idx_task_status", columnList = "status_key, status"),
    @Index(name = "idx_task_priority", columnList = "priority_key, priority"),
    @Index(name = "idx_task_deadline", columnList = "deadline"),
    @Index(name = "idx_task_updated_at", columnList = "updated_at")
})
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    @Column(name = "status_key")
    private String statusKey; // References user's custom status configuration

    @Column(name = "priority_key")
    private String priorityKey; // References user's custom priority configuration

    // Enum fields for backward compatibility
    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    private TaskPriority priority;

    @Column(name = "start_date")
    private LocalDate startDate;

    private LocalDate deadline;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Comment field for additional task notes
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    // URL file field for file attachments
    @Column(name = "url_file", length = 1000)
    private String urlFile;

    // Public visibility flag for profile page functionality
    @Column(name = "is_public", columnDefinition = "bit default false")
    @Builder.Default
    private Boolean isPublic = false;

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
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TaskAssignee> assignees = new HashSet<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TaskChecklist> checklists = new HashSet<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TaskComment> comments = new HashSet<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TaskAttachment> attachments = new HashSet<>();

    // Helper methods
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Computed fields for timestamps
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== SEARCH INDEXING HELPER METHODS ====================

    /**
     * Get the primary assignee ID for search indexing
     */
    public Long getAssigneeId() {
        return assignees.stream()
                .findFirst()
                .map(assignee -> assignee.getUser().getId())
                .orElse(null);
    }

    /**
     * Get the primary assignee name for search indexing
     */
    public String getAssigneeName() {
        return assignees.stream()
                .findFirst()
                .map(assignee -> assignee.getUser().getFirstName() + " " + assignee.getUser().getLastName())
                .orElse(null);
    }

    /**
     * Get project ID for search indexing
     */
    public Long getProjectId() {
        return project != null ? project.getId() : null;
    }

    /**
     * Get project name for search indexing
     */
    public String getProjectName() {
        return project != null ? project.getName() : null;
    }

    /**
     * Get tags as list of strings for search indexing
     */
    public java.util.List<String> getTags() {
        // For now, return empty list - implement tag system later if needed
        return java.util.Collections.emptyList();
    }

    /**
     * Get due date for search indexing (deadline converted to LocalDateTime)
     */
    public LocalDateTime getDueDate() {
        return deadline != null ? deadline.atStartOfDay() : null;
    }

    /**
     * Check if task is completed for search indexing
     */
    public Boolean getIsCompleted() {
        return status == TaskStatus.COMPLETED;
    }

    // Override equals and hashCode for proper collection handling
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return id != null && id.equals(task.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
