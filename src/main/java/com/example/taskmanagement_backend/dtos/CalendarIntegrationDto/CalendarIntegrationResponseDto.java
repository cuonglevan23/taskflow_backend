package com.example.taskmanagement_backend.dtos.CalendarIntegrationDto;


import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarIntegrationResponseDto {

    private Long id;
    private Long userId;
    private String provider;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}