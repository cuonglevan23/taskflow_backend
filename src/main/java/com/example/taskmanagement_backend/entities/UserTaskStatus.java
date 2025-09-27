package com.example.taskmanagement_backend.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_task_statuses", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "status_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTaskStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String statusKey; // e.g., "TODO", "IN_PROGRESS", "DONE", etc.

    @Column(nullable = false)
    private String displayName; // User-customizable display name

    @Column(nullable = false)
    private String color; // Hex color code for UI

    @Column(nullable = false)
    private Integer sortOrder; // Order for display in UI

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDefault = false; // Whether this is a default system status

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true; // Whether this status is active/visible

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_task_status_user"))
    private User user;


}