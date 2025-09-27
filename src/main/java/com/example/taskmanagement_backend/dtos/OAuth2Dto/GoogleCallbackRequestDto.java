package com.example.taskmanagement_backend.dtos.OAuth2Dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleCallbackRequestDto {
    
    @NotBlank(message = "Authorization code is required")
    private String code;
    
    @NotBlank(message = "State parameter is required")
    private String state;
}