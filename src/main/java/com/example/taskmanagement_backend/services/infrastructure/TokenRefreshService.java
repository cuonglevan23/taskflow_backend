package com.example.taskmanagement_backend.services.infrastructure;

import com.example.taskmanagement_backend.dtos.OAuth2Dto.TokenResponseDto;
import com.example.taskmanagement_backend.entities.RefreshToken;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.exceptions.HttpException;
import com.example.taskmanagement_backend.repositories.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRefreshService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public TokenResponseDto refreshAccessToken(String refreshTokenValue, String deviceInfo) {
        // 1. Find and validate refresh token
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenAndIsRevokedFalse(refreshTokenValue)
                .orElseThrow(() -> new HttpException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        // 2. Check if token is expired
        if (refreshToken.isExpired()) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new HttpException("Refresh token has expired", HttpStatus.UNAUTHORIZED);
        }

        User user = refreshToken.getUser();

        // 3. Generate new access token
        String newAccessToken = jwtTokenService.generateAccessToken(user);

        // 4. Optionally rotate refresh token (recommended for security)
        RefreshToken newRefreshToken = jwtTokenService.generateRefreshToken(user, deviceInfo);
        
        // Revoke old refresh token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        log.info("Successfully refreshed tokens for user: {}", user.getEmail());

        return TokenResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .expiresIn(900L) // 15 minutes
                .tokenType("Bearer")
                .userInfo(TokenResponseDto.UserInfoDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getUserProfile() != null ? user.getUserProfile().getFirstName() : "")
                        .lastName(user.getUserProfile() != null ? user.getUserProfile().getLastName() : "")
                        .avatarUrl(user.getAvatarUrl() != null ? user.getAvatarUrl() : "")
                        .isFirstLogin(user.isFirstLogin())
                        .build())
                .build();
    }

    @Transactional
    public void revokeRefreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenAndIsRevokedFalse(refreshTokenValue)
                .orElseThrow(() -> new HttpException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        
        log.info("Successfully revoked refresh token for user: {}", refreshToken.getUser().getEmail());
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        // This method would need the user repository to fetch the user
        // For now, we'll implement it in the service that has access to UserRepository
        log.info("Revoking all tokens for user: {}", userId);
    }
}