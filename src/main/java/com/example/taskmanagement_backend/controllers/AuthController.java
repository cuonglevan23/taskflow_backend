package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.OAuth2Dto.TokenResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.LoginRequestDto;
import com.example.taskmanagement_backend.dtos.UserDto.LoginResponseDto;
import com.example.taskmanagement_backend.services.infrastructure.TokenRefreshService;
import com.example.taskmanagement_backend.services.infrastructure.GoogleOAuth2Service;
import com.example.taskmanagement_backend.services.UserService;
import com.example.taskmanagement_backend.services.OnlineStatusService; // NEW
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
@Slf4j
@Tag(name = "Authentication", description = "Authentication endpoints")
@CrossOrigin(origins = {"https://main.d2az19adxqfdf3.amplifyapp.com", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class AuthController {

    private final UserService userService;
    private final TokenRefreshService tokenRefreshService;
    private final GoogleOAuth2Service googleOAuth2Service;
    private final OnlineStatusService onlineStatusService; // NEW

    @PostMapping("/login")
    @Operation(summary = "Login with email and password", description = "Traditional login with email and password")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request, HttpServletResponse response) throws Exception {
        LoginResponseDto result = this.userService.login(request);

        // Set HTTP-only cookie for WebSocket authentication
        setAccessTokenCookie(response, result.getAccessToken());

        // Set user online status - NEW
        onlineStatusService.setUserOnline(request.getEmail());

        log.info("✅ Login successful for user: {}, HTTP-only cookie set for WebSocket, status set to ONLINE", request.getEmail());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Get new access token using refresh token from cookie")
    public ResponseEntity<Void> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        try {
            // Đọc refresh token từ cookie
            String refreshToken = null;
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("refreshToken".equals(cookie.getName())) {
                        refreshToken = cookie.getValue();
                        break;
                    }
                }
            }

            if (refreshToken == null) {
                log.warn("No refresh token found in cookies");
                return ResponseEntity.status(401).build();
            }

            String deviceInfo = extractDeviceInfo(request);
            TokenResponseDto tokenResponse = tokenRefreshService.refreshAccessToken(refreshToken, deviceInfo);

            // Set new access token cookie
            setAuthCookies(response, tokenResponse);

            // THÊM: Cập nhật trạng thái online khi refresh token thành công
            // Điều này đảm bảo user vẫn được maintain online status khi token được refresh tự động
            if (tokenResponse.getUserInfo() != null && tokenResponse.getUserInfo().getEmail() != null) {
                onlineStatusService.setUserOnline(tokenResponse.getUserInfo().getEmail());
                log.debug("🔄 Updated online status for user during token refresh: {}", tokenResponse.getUserInfo().getEmail());
            }

            log.info("✅ Token refreshed successfully");
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            clearAuthCookies(response);
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoke refresh token and clear authentication cookies")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        try {
            // Set user offline status - NEW
            if (authentication != null && authentication.isAuthenticated()) {
                onlineStatusService.setUserOffline(authentication.getName());
            }

            // Đọc refresh token từ cookie để revoke
            String refreshToken = null;
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("refreshToken".equals(cookie.getName())) {
                        refreshToken = cookie.getValue();
                        break;
                    }
                }
            }

            if (refreshToken != null) {
                tokenRefreshService.revokeRefreshToken(refreshToken);
                log.info("✅ Refresh token revoked successfully");
            }

            // Clear cookies
            clearAuthCookies(response);
            log.info("✅ Authentication cookies cleared, user set to OFFLINE");

            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            clearAuthCookies(response);
            return ResponseEntity.ok().build();
        }
    }

    @GetMapping("/check")
    @Operation(summary = "Check authentication status", description = "Check if user is authenticated and return user info")
    public ResponseEntity<Map<String, Object>> checkAuth(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("user", authentication.getName());
            response.put("authorities", authentication.getAuthorities());
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(401).build();
    }

    @GetMapping("/google/status")
    @Operation(summary = "Check Google Calendar permissions status",
               description = "Check if current user has Google Calendar permissions")
    public ResponseEntity<Map<String, Object>> getGoogleCalendarStatus(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "User not authenticated"
            ));
        }

        try {
            String userEmail = authentication.getName();

            // TODO: Check user's Google Calendar permissions from database
            // For now, return mock data
            Map<String, Object> status = new HashMap<>();
            status.put("userEmail", userEmail);
            status.put("hasGoogleCalendarPermissions", false); // TODO: Check from database
            status.put("googleCalendarTokenExpiry", null); // TODO: Get from database
            status.put("needsReauthorization", true);

            log.info("📅 Checked Google Calendar status for user: {}", userEmail);

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("❌ Error checking Google Calendar status: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to check Google Calendar status",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Extract access token from Authorization header for blacklisting
     */
    private String extractAccessTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // Remove "Bearer " prefix
        }
        
        return null;
    }

    private String extractDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String clientIp = getClientIpAddress(request);
        return String.format("IP: %s, UserAgent: %s", clientIp, userAgent);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Set access token cookie (HTTP-only for security)
     */
    private void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        Cookie accessTokenCookie = new Cookie("accessToken", accessToken);
        accessTokenCookie.setHttpOnly(true);  // Prevent XSS attacks
        accessTokenCookie.setSecure(false);   // Set to true in production with HTTPS
        accessTokenCookie.setPath("/");
        accessTokenCookie.setDomain("localhost"); // Explicit domain for local development
        accessTokenCookie.setMaxAge(15 * 60); // 15 minutes
        response.addCookie(accessTokenCookie);
        log.info("✅ Set access token cookie (domain: localhost)");
    }

    /**
     * Set refresh token cookie (HTTP-only for security, longer expiration)
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie refreshTokenCookie = new Cookie("refreshToken", refreshToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(false);  // Set to true in production with HTTPS
        refreshTokenCookie.setPath("/api/auth/refresh"); // Only sent to refresh endpoint
        refreshTokenCookie.setDomain("localhost"); // Explicit domain for local development
        refreshTokenCookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        response.addCookie(refreshTokenCookie);
        log.info("✅ Set refresh token cookie (domain: localhost)");
    }

    /**
     * 🔌 Set WebSocket token cookie (NOT HTTP-only for JavaScript access)
     */
    private void setWebSocketTokenCookie(HttpServletResponse response, String accessToken) {
        Cookie wsTokenCookie = new Cookie("wsToken", accessToken);
        wsTokenCookie.setHttpOnly(false);  // JavaScript CAN read this for WebSocket connection
        wsTokenCookie.setSecure(false);    // Set to true in production with HTTPS
        wsTokenCookie.setPath("/");
        wsTokenCookie.setDomain("localhost"); // Explicit domain for local development - CRITICAL for WebSocket
        wsTokenCookie.setMaxAge(15 * 60);  // 15 minutes (same as access token)
        // Note: SameSite is set via response header instead of cookie method
        response.addCookie(wsTokenCookie);
        // Add SameSite attribute via header for better WebSocket compatibility
        response.addHeader("Set-Cookie", String.format("wsToken=%s; Path=/; Domain=localhost; Max-Age=%d; SameSite=Lax",
            accessToken, 15 * 60));
        log.info("🔌 Set WebSocket token cookie with domain localhost for real-time chat authentication");
    }

    /**
     * Set authentication cookies from token response
     */
    private void setAuthCookies(HttpServletResponse response, TokenResponseDto tokenResponse) {
        if (tokenResponse.getAccessToken() != null) {
            setAccessTokenCookie(response, tokenResponse.getAccessToken());
            // 🔌 Also set WebSocket token cookie for real-time chat
            setWebSocketTokenCookie(response, tokenResponse.getAccessToken());
        }

        if (tokenResponse.getRefreshToken() != null) {
            setRefreshTokenCookie(response, tokenResponse.getRefreshToken());
        }

        log.debug("Set authentication cookies from token response");
    }

    /**
     * Clear authentication cookies
     */
    private void clearAuthCookies(HttpServletResponse response) {
        // Clear access token cookie
        Cookie accessTokenCookie = new Cookie("accessToken", "");
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setSecure(false);            // Set to true in production
        accessTokenCookie.setPath("/");
        accessTokenCookie.setDomain("localhost");      // Match the domain used when setting
        accessTokenCookie.setMaxAge(0);                // Expire immediately
        response.addCookie(accessTokenCookie);

        // Clear refresh token cookie
        Cookie refreshTokenCookie = new Cookie("refreshToken", "");
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(false);           // Set to true in production
        refreshTokenCookie.setPath("/api/auth/refresh"); // Match the path used when setting
        refreshTokenCookie.setDomain("localhost");      // Match the domain used when setting
        refreshTokenCookie.setMaxAge(0);               // Expire immediately
        response.addCookie(refreshTokenCookie);

        // 🔌 Clear WebSocket token cookie
        Cookie wsTokenCookie = new Cookie("wsToken", "");
        wsTokenCookie.setHttpOnly(false);             // Match the httpOnly setting used when setting
        wsTokenCookie.setSecure(false);               // Set to true in production
        wsTokenCookie.setPath("/");
        wsTokenCookie.setDomain("localhost");         // Match the domain used when setting
        wsTokenCookie.setMaxAge(0);                   // Expire immediately
        response.addCookie(wsTokenCookie);

        log.info("🧹 Cleared all authentication cookies (accessToken, refreshToken, wsToken) with domain localhost");
    }
}
