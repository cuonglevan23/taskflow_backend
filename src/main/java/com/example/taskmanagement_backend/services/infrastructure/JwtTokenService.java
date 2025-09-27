package com.example.taskmanagement_backend.services.infrastructure;

import com.example.taskmanagement_backend.entities.RefreshToken;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.RefreshTokenRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token.expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("roles", List.of(user.getSystemRole().name())); // ✅ UPDATED: Use systemRole instead of roles
        claims.put("type", "access_token"); // ✅ Add required token type for validation
        
        return createToken(claims, user.getEmail(), accessTokenExpiration);
    }

    public RefreshToken generateRefreshToken(User user, String deviceInfo) {
        String tokenValue = UUID.randomUUID().toString();
        
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .deviceInfo(deviceInfo)
                .build();
        
        return refreshTokenRepository.save(refreshToken);
    }

    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUserEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        return claims.getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        return claims.get("userId", Long.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            return claims.getExpiration().before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllByUser(user);
    }

    public void cleanupExpiredTokens() {
        int deletedCount = refreshTokenRepository.deleteExpiredAndRevokedTokens(LocalDateTime.now());
        log.info("Cleaned up {} expired and revoked refresh tokens", deletedCount);
    }

    /**
     * Clean up tokens older than specified date
     */
    public void cleanupOldTokens(LocalDateTime cutoffDate) {
        int deletedCount = refreshTokenRepository.deleteTokensOlderThan(cutoffDate);
        log.info("Cleaned up {} old refresh tokens (older than {})", deletedCount, cutoffDate);
    }

    /**
     * Limit number of tokens per user (keep only the most recent N tokens)
     */
    public void limitTokensPerUser(int maxTokensPerUser) {
        int deletedCount = refreshTokenRepository.deleteExcessTokensPerUser(maxTokensPerUser);
        log.info("Limited tokens per user to {}, deleted {} excess tokens", maxTokensPerUser, deletedCount);
    }

    /**
     * Get token statistics for monitoring
     */
    public TokenStats getTokenStats() {
        long totalTokens = refreshTokenRepository.count();
        long activeTokens = refreshTokenRepository.countActiveTokens();
        long expiredTokens = refreshTokenRepository.countExpiredTokens(LocalDateTime.now());
        long revokedTokens = refreshTokenRepository.countRevokedTokens();
        
        return TokenStats.builder()
                .totalTokens(totalTokens)
                .activeTokens(activeTokens)
                .expiredTokens(expiredTokens)
                .revokedTokens(revokedTokens)
                .build();
    }

    /**
     * Token statistics DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class TokenStats {
        private long totalTokens;
        private long activeTokens;
        private long expiredTokens;
        private long revokedTokens;
    }
}