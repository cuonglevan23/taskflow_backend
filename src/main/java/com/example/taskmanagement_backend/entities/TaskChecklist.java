package com.example.taskmanagement_backend.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_checklists")
public class TaskChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false, foreignKey = @ForeignKey(name = "fk_checklist_task"))
    private Task task;

    @Column(nullable = false)
    private String item;

    @Builder.Default
    @Column(name = "is_completed")
    private Boolean isCompleted = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
