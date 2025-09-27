package com.example.taskmanagement_backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_default_workspace")
    @Builder.Default
    private boolean isDefaultWorkspace = false;

    // Direct one-to-many relationship with projects
    @Builder.Default
    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Project> projects = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TeamMember> members = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "fk_team_creator"))
    private User createdBy;

    @ManyToOne
    @JoinColumn(name = "organization_id", foreignKey = @ForeignKey(name = "fk_team_organization"))
    private Organization organization;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== SEARCH INDEXING HELPER METHODS ====================

    /**
     * Get leader ID for search indexing
     */
    public Long getLeaderId() {
        return members.stream()
                .filter(member -> member.getRole() != null && member.getRole().equals("LEADER"))
                .findFirst()
                .map(member -> member.getUser().getId())
                .orElse(createdBy != null ? createdBy.getId() : null);
    }

    /**
     * Get leader name for search indexing
     */
    public String getLeaderName() {
        return members.stream()
                .filter(member -> member.getRole() != null && member.getRole().equals("LEADER"))
                .findFirst()
                .map(member -> member.getUser().getFirstName() + " " + member.getUser().getLastName())
                .orElse(createdBy != null ? createdBy.getFirstName() + " " + createdBy.getLastName() : null);
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
     * Get member count for search indexing
     */
    public Integer getMemberCount() {
        return members.size();
    }

    /**
     * Get active projects count for search indexing
     */
    public Integer getActiveProjectsCount() {
        return (int) projects.stream()
                .filter(project -> project.getIsActive())
                .count();
    }

    /**
     * Get total tasks count for search indexing
     */
    public Integer getTotalTasksCount() {
        // This would need to be calculated from project tasks
        return 0;
    }

    /**
     * Get completed tasks count for search indexing
     */
    public Integer getCompletedTasksCount() {
        // This would need to be calculated from project tasks
        return 0;
    }

    /**
     * Get team performance score for search indexing
     */
    public Float getTeamPerformanceScore() {
        // This would need to be calculated based on team metrics
        return 0.0f;
    }

    /**
     * Check if team is active for search indexing
     */
    public Boolean getIsActive() {
        return true; // Teams are active by default
    }

    /**
     * Get team type for search indexing
     */
    public String getTeamType() {
        return isDefaultWorkspace ? "WORKSPACE" : "TEAM";
    }

    /**
     * Get privacy setting for search indexing
     */
    public String getPrivacy() {
        return "PUBLIC"; // Default privacy setting
    }

    /**
     * Check if team is searchable for search indexing
     */
    public Boolean getSearchable() {
        return true; // Teams are searchable by default
    }

    /**
     * Get tags for search indexing
     */
    public java.util.List<String> getTags() {
        // For now, return empty list - implement tag system later if needed
        return java.util.Collections.emptyList();
    }

    /**
     * Get department for search indexing
     */
    public String getDepartment() {
        // This would be derived from organization or team settings
        return null;
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
        Team team = (Team) o;
        return id != null && id.equals(team.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
