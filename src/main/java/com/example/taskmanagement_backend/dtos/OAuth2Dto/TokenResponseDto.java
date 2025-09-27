package com.example.taskmanagement_backend.dtos.OAuth2Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponseDto {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn; // seconds
    private String tokenType;
    private UserInfoDto userInfo;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfoDto {
        private Long id;
        private String email;
        private String firstName;
        private String lastName;
        private String avatarUrl;
        private boolean isFirstLogin;
    }
}