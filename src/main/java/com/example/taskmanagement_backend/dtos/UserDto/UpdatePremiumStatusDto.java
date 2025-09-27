package com.example.taskmanagement_backend.dtos.UserDto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePremiumStatusDto {

    private Boolean isPremium;

    private String planType; // "monthly", "yearly", "lifetime"

    private LocalDateTime premiumExpiry;
}
