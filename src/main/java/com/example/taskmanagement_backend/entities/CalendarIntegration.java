package com.example.taskmanagement_backend.entities;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "calendar_integrations")
public class CalendarIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_calendar_user"))
    private User user;

    private String provider;

    @Lob
    private String accessToken;

    @Lob
    private String refreshToken;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
}