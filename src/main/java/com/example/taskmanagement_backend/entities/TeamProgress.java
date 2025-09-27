package com.example.taskmanagement_backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "team_progress")
public class TeamProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "team_id", foreignKey = @ForeignKey(name = "fk_team_progress_team"))
    private Team team;

    @Column(name = "total_tasks")
    private Integer totalTasks;

    @Column(name = "completed_tasks")
    private Integer completedTasks;

    @Column(name = "completion_percentage")
    private Double completionPercentage;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastUpdated = LocalDateTime.now();
    }

    // Helper method to calculate completion percentage
    public void calculateCompletionPercentage() {
        if (totalTasks != null && totalTasks > 0) {
            this.completionPercentage = (completedTasks != null ? completedTasks : 0) * 100.0 / totalTasks;
        } else {
            this.completionPercentage = 0.0;
        }
    }
}