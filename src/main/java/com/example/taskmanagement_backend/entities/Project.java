package com.example.taskmanagement_backend.entities;


import com.example.taskmanagement_backend.enums.ProjectStatus;
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
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    private ProjectStatus status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @ManyToOne
    @JoinColumn(name = "owner_id", foreignKey = @ForeignKey(name = "fk_project_owner"))
    private User owner;

    @Builder.Default
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProjectMember> members = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "organization_id", foreignKey = @ForeignKey(name = "fk_project_organization"))
    private Organization organization;

    // Direct relationship with team (optional for personal projects)
    @ManyToOne
    @JoinColumn(name = "team_id", foreignKey = @ForeignKey(name = "fk_project_team"))
    private Team team;

    @Column(name = "is_personal")
    @Builder.Default
    private Boolean isPersonal = false;

    @ManyToOne
    @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "fk_project_creator"))
    private User createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== SEARCH INDEXING HELPER METHODS ====================

    /**
     * Get privacy setting for search indexing
     */
    public String getPrivacy() {
        // For now, return a default privacy setting - implement privacy enum later if needed
        return "PUBLIC";
    }

    /**
     * Get owner ID for search indexing
     */
    public Long getOwnerId() {
        return owner != null ? owner.getId() : null;
    }

    /**
     * Get owner name for search indexing
     */
    public String getOwnerName() {
        return owner != null ? owner.getFirstName() + " " + owner.getLastName() : null;
    }

    /**
     * Get member IDs for search indexing
     */
    public java.util.List<Long> getMemberIds() {
        return members.stream()
                .map(member -> member.getUser().getId())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get member names for search indexing
     */
    public java.util.List<String> getMemberNames() {
        return members.stream()
                .map(member -> member.getUser().getFirstName() + " " + member.getUser().getLastName())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get tags as list of strings for search indexing
     */
    public java.util.List<String> getTags() {
        // For now, return empty list - implement tag system later if needed
        return java.util.Collections.emptyList();
    }

    /**
     * Check if project is active for search indexing
     */
    public Boolean getIsActive() {
        return status != null && status != com.example.taskmanagement_backend.enums.ProjectStatus.COMPLETED;
    }

    /**
     * Get total tasks count for search indexing
     */
    public Integer getTotalTasks() {
        // This would need to be calculated from task relationships
        return 0;
    }

    /**
     * Get completed tasks count for search indexing
     */
    public Integer getCompletedTasks() {
        // This would need to be calculated from task relationships
        return 0;
    }

    /**
     * Get completion percentage for search indexing
     */
    public Float getCompletionPercentage() {
        // This would need to be calculated from task relationships
        return 0.0f;
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

    // ✅ FIX: Override hashCode and equals to prevent ConcurrentModificationException
    // Only use ID field to avoid issues with lazy-loaded collections
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return id != null && id.equals(project.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
