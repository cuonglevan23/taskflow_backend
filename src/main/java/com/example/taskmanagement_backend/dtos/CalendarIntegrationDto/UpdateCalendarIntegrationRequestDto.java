package com.example.taskmanagement_backend.dtos.CalendarIntegrationDto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCalendarIntegrationRequestDto {

    @NotBlank
    private String accessToken;

    @NotBlank
    private String refreshToken;

    @NotNull
    private LocalDateTime expiresAt;
}