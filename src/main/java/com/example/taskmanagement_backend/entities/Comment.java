package com.example.taskmanagement_backend.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String content;

    @ManyToOne
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_comment_user"))
    private User user;

    @ManyToOne
    @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "fk_comment_task"))
    private Task task;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
