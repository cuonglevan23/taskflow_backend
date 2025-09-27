package com.example.taskmanagement_backend.entities;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_status_history")
public class TaskStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "old_status")
    private String oldStatus;

    @Column(name = "new_status")
    private String newStatus;

    @ManyToOne
    @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "fk_task_status_history_task"))
    private Task task;

    @ManyToOne
    @JoinColumn(name = "changed_by", foreignKey = @ForeignKey(name = "fk_task_status_history_user"))
    private User changedBy;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;
}