package com.example.taskmanagement_backend.dtos.OAuth2Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthUrlResponseDto {
    private String authUrl;
    private String state;
    private String scopes; // ✅ NEW: Add scopes field for Calendar permissions info
}