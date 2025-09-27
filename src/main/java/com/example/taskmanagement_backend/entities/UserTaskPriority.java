package com.example.taskmanagement_backend.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_task_priorities", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "priority_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTaskPriority {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String priorityKey; // e.g., "LOW", "MEDIUM", "HIGH", etc.

    @Column(nullable = false)
    private String displayName; // User-customizable display name

    @Column(nullable = false)
    private String color; // Hex color code for UI

    @Column(nullable = false)
    private Integer sortOrder; // Order for display in UI

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDefault = false; // Whether this is a default system priority

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true; // Whether this priority is active/visible

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_task_priority_user"))
    private User user;


}