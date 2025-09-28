package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.OAuth2Dto.GoogleAuthUrlResponseDto;
import com.example.taskmanagement_backend.dtos.OAuth2Dto.TokenResponseDto;
import com.example.taskmanagement_backend.services.infrastructure.GoogleOAuth2Service;
import com.example.taskmanagement_backend.services.OnlineStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Google OAuth2 Authentication", description = "Google OAuth2 authentication endpoints with frontend redirect")
public class GoogleOAuth2Controller {

    private final GoogleOAuth2Service googleOAuth2Service;
    private final OnlineStatusService onlineStatusService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.admin.url}")
    private String adminUrl;

    // ✅ Inject JWT expiration values from application.properties
    @Value("${jwt.access-token.expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    @GetMapping("/url")
    @Operation(summary = "Get Google OAuth2 authorization URL", 
               description = "Returns the Google OAuth2 authorization URL with state parameter for CSRF protection. Includes all permissions (profile, email, calendar, gmail) so user only needs to authorize once.")
    public ResponseEntity<GoogleAuthUrlResponseDto> getGoogleAuthUrl(
            @RequestParam(value = "client_type", required = false, defaultValue = "user") String clientType) {
        log.info("Generating Google OAuth2 authorization URL for client type: {} (includes Gmail permissions)", clientType);
        GoogleAuthUrlResponseDto response = googleOAuth2Service.generateAuthUrlWithClientType(clientType);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/callback")
    @Operation(summary = "Handle Google OAuth2 callback", 
               description = "Processes the authorization code from Google, generates JWT tokens, and sets HTTP-only cookies")
    public void handleGoogleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        try {
            // ✅ Extract client type from state parameter
            String clientType = "user"; // default
            String actualState = state;

            if (state != null && state.contains("|")) {
                String[] stateParts = state.split("\\|", 2);
                actualState = stateParts[0];
                clientType = stateParts.length > 1 ? stateParts[1] : "user";
            }

            // ✅ Determine target frontend URL based on client type
            String targetFrontendUrl = "admin".equalsIgnoreCase(clientType) ? adminUrl : frontendUrl;
            String clientName = "admin".equalsIgnoreCase(clientType) ? "Admin" : "User";

            log.info("🎯 Processing OAuth2 callback for {} frontend ({}) - extracted from state", clientName, targetFrontendUrl);
            log.info("📍 Original state: {}, Actual state: {}, Client type: {}", state, actualState, clientType);

            // ✅ Handle Google OAuth2 errors first
            if (error != null) {
                log.warn("🚫 Google OAuth2 error received: {} - {}", error, errorDescription);

                String userFriendlyMessage;
                String technicalDetails = errorDescription != null ? errorDescription : error;

                switch (error) {
                    case "access_denied":
                        userFriendlyMessage = "Quyền truy cập bị từ chối. " +
                            "Ứng dụng TaskFlow đang trong giai đoạn thử nghiệm và chỉ những người dùng " +
                            "được phê duyệt mới có thể đăng nhập bằng Google. " +
                            "Vui lòng liên hệ với nhà phát triển để được thêm vào danh sách test users " +
                            "hoặc sử dụng tính năng đăng ký/đăng nhập thông thường.";
                        break;
                    case "invalid_request":
                        userFriendlyMessage = "Yêu cầu không hợp lệ. Vui lòng thử lại.";
                        break;
                    case "unauthorized_client":
                        userFriendlyMessage = "Ứng dụng chưa được ủy quyền. Vui lòng liên hệ hỗ trợ.";
                        break;
                    case "unsupported_response_type":
                        userFriendlyMessage = "Lỗi cấu hình ứng dụng. Vui lòng liên hệ hỗ trợ.";
                        break;
                    case "invalid_scope":
                        userFriendlyMessage = "Quyền truy cập không hợp lệ. Vui lòng thử lại.";
                        break;
                    case "server_error":
                        userFriendlyMessage = "Lỗi từ Google. Vui lòng thử lại sau.";
                        break;
                    case "temporarily_unavailable":
                        userFriendlyMessage = "Dịch vụ Google tạm thời không khả dụng. Vui lòng thử lại sau.";
                        break;
                    default:
                        userFriendlyMessage = "Đăng nhập Google thất bại: " + technicalDetails;
                }

                // Log detailed error for debugging
                log.error("🚫 Google OAuth2 Error Details: error={}, description={}, state={}, clientType={}",
                         error, errorDescription, state, clientType);

                // Redirect to appropriate frontend with user-friendly error
                String errorUrl = targetFrontendUrl + "/auth/error?type=oauth&error=" +
                                 URLEncoder.encode(error, StandardCharsets.UTF_8) +
                                 "&message=" + URLEncoder.encode(userFriendlyMessage, StandardCharsets.UTF_8);
                response.sendRedirect(errorUrl);
                return;
            }

            // ✅ Validate required parameters
            if (code == null || code.trim().isEmpty()) {
                log.error("🚫 Missing authorization code in OAuth2 callback");
                String errorUrl = targetFrontendUrl + "/auth/error?type=missing_code&message=" +
                                 URLEncoder.encode("Mã xác thực từ Google bị thiếu. Vui lòng thử lại.", StandardCharsets.UTF_8);
                response.sendRedirect(errorUrl);
                return;
            }

            if (actualState == null || actualState.trim().isEmpty()) {
                log.error("🚫 Missing state parameter in OAuth2 callback");
                String errorUrl = targetFrontendUrl + "/auth/error?type=missing_state&message=" +
                                 URLEncoder.encode("Tham số bảo mật bị thiếu. Vui lòng thử lại.", StandardCharsets.UTF_8);
                response.sendRedirect(errorUrl);
                return;
            }

            log.info("✅ Processing Google OAuth2 callback for state: {}", actualState);

            // Extract device info for security tracking
            String deviceInfo = extractDeviceInfo(request);
            
            // Process OAuth2 callback and get JWT tokens (use actualState, not the encoded one)
            TokenResponseDto tokenResponse = googleOAuth2Service.handleCallback(code, actualState, deviceInfo);

            log.info("✅ Successfully authenticated user via Google OAuth2: {}", tokenResponse.getUserInfo().getEmail());

            // Set user online status
            onlineStatusService.setUserOnline(tokenResponse.getUserInfo().getId());

            // Set HTTP-only cookies
            setAuthCookies(response, tokenResponse);

            // Redirect to appropriate frontend success page
            String successUrl = targetFrontendUrl + "/auth/success";
            log.info("🔄 Redirecting to {} frontend success page: {}", clientName, successUrl);
            response.sendRedirect(successUrl);

        } catch (Exception e) {
            log.error("❌ OAuth2 callback processing failed: {}", e.getMessage(), e);

            // ✅ Extract client type from state for error handling too
            String clientType = "user"; // default
            if (state != null && state.contains("|")) {
                String[] stateParts = state.split("\\|", 2);
                clientType = stateParts.length > 1 ? stateParts[1] : "user";
            }

            // Determine target frontend URL based on client type
            String targetFrontendUrl = "admin".equalsIgnoreCase(clientType) ? adminUrl : frontendUrl;

            // Determine user-friendly error message
            String userFriendlyMessage;
            if (e.getMessage().contains("access_denied") || e.getMessage().contains("403")) {
                userFriendlyMessage = "Quyền truy cập bị từ chối. Ứng dụng đang trong giai đoạn thử nghiệm. " +
                                    "Vui lòng liên hệ nhà phát triển để được thêm vào danh sách test users.";
            } else if (e.getMessage().contains("invalid_grant")) {
                userFriendlyMessage = "Mã xác thực đã hết hạn hoặc không hợp lệ. Vui lòng thử đăng nhập lại.";
            } else if (e.getMessage().contains("network") || e.getMessage().contains("timeout")) {
                userFriendlyMessage = "Lỗi kết nối mạng. Vui lòng kiểm tra internet và thử lại.";
            } else {
                userFriendlyMessage = "Đăng nhập Google thất bại. Vui lòng thử lại hoặc sử dụng đăng nhập thông thường.";
            }

            // Redirect to appropriate frontend auth error page with detailed error
            String errorUrl = targetFrontendUrl + "/auth/error?type=processing_error&message=" +
                             URLEncoder.encode(userFriendlyMessage, StandardCharsets.UTF_8) +
                             "&technical=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            response.sendRedirect(errorUrl);
        }
    }

    @PostMapping("/callback")
    @Operation(summary = "Handle Google OAuth2 callback (API)", 
               description = "Processes the authorization code from Google and returns JWT tokens as JSON (for API clients)")
    public ResponseEntity<TokenResponseDto> handleGoogleCallbackApi(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpServletRequest request) {
        
        log.info("Processing Google OAuth2 callback API for state: {}", state);
        
        // Extract device info for security tracking
        String deviceInfo = extractDeviceInfo(request);
        
        TokenResponseDto response = googleOAuth2Service.handleCallback(code, state, deviceInfo);
        
        log.info("Successfully authenticated user via Google OAuth2 API: {}", response.getUserInfo().getEmail());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate")
    @Operation(summary = "Validate JWT token", 
               description = "Validates a JWT token and returns user info if valid")
    public ResponseEntity<?> validateToken(@RequestParam("token") String token) {
        
        log.info("Validating token for frontend");
        
        try {
            // This endpoint helps frontend validate if the token is working
            // You can add JWT validation logic here
            return ResponseEntity.ok().body(Map.of(
                "valid", true,
                "message", "Token validation endpoint - implement JWT validation logic here"
            ));
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", "Invalid token"
            ));
        }
    }

    private String buildFrontendCallbackUrl(TokenResponseDto tokenResponse) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(frontendUrl).append("/auth/callback");
        
        // Add query parameters
        urlBuilder.append("?access_token=").append(URLEncoder.encode(tokenResponse.getAccessToken(), StandardCharsets.UTF_8));
        urlBuilder.append("&refresh_token=").append(URLEncoder.encode(tokenResponse.getRefreshToken(), StandardCharsets.UTF_8));
        urlBuilder.append("&expires_in=").append(tokenResponse.getExpiresIn());
        urlBuilder.append("&token_type=").append(URLEncoder.encode(tokenResponse.getTokenType(), StandardCharsets.UTF_8));
        
        // Add user info
        TokenResponseDto.UserInfoDto userInfo = tokenResponse.getUserInfo();
        urlBuilder.append("&user_id=").append(userInfo.getId());
        urlBuilder.append("&email=").append(URLEncoder.encode(userInfo.getEmail(), StandardCharsets.UTF_8));
        urlBuilder.append("&first_name=").append(URLEncoder.encode(userInfo.getFirstName(), StandardCharsets.UTF_8));
        urlBuilder.append("&last_name=").append(URLEncoder.encode(userInfo.getLastName(), StandardCharsets.UTF_8));
        urlBuilder.append("&is_first_login=").append(userInfo.isFirstLogin());
        
        if (userInfo.getAvatarUrl() != null && !userInfo.getAvatarUrl().isEmpty()) {
            urlBuilder.append("&avatar_url=").append(URLEncoder.encode(userInfo.getAvatarUrl(), StandardCharsets.UTF_8));
        }
        
        return urlBuilder.toString();
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
     * Set secure HTTP-only cookies for authentication tokens
     */
    private void setAuthCookies(HttpServletResponse response, TokenResponseDto tokenResponse) {
        // ✅ Convert milliseconds to seconds for cookie MaxAge
        int accessTokenSeconds = (int) (accessTokenExpiration / 1000);
        int refreshTokenSeconds = (int) (refreshTokenExpiration / 1000);



        // Access Token Cookie (HTTP-only, Secure, Long expiration for work management app)
        Cookie accessTokenCookie = new Cookie("accessToken", tokenResponse.getAccessToken());
        accessTokenCookie.setHttpOnly(true);  // Prevent XSS
        accessTokenCookie.setSecure(false);   // Set to true in production with HTTPS
        accessTokenCookie.setPath("/");
        accessTokenCookie.setDomain("main.d2az19adxqfdf3.amplifyapp.com"); // Production domain for Amplify
        accessTokenCookie.setMaxAge(accessTokenSeconds); // ✅ Use configured expiration (20 hours)
        response.addCookie(accessTokenCookie);

        // Refresh Token Cookie (HTTP-only, Secure, Very long expiration)
        Cookie refreshTokenCookie = new Cookie("refreshToken", tokenResponse.getRefreshToken());
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(false);  // Set to true in production with HTTPS
        refreshTokenCookie.setPath("/api/auth/refresh"); // Only sent to refresh endpoint
        refreshTokenCookie.setDomain("localhost"); // Explicit domain for local development
        refreshTokenCookie.setMaxAge(refreshTokenSeconds); // ✅ Use configured expiration (30 days)
        response.addCookie(refreshTokenCookie);

        // 🔌 WebSocket Token Cookie (NOT HTTP-only, so JavaScript can read it for WebSocket authentication)
        Cookie wsTokenCookie = new Cookie("wsToken", tokenResponse.getAccessToken());
        wsTokenCookie.setHttpOnly(false);  // JavaScript CAN read this for WebSocket connection
        wsTokenCookie.setSecure(false);    // Set to true in production with HTTPS
        wsTokenCookie.setPath("/");
        wsTokenCookie.setDomain("localhost"); // Explicit domain for local development - CRITICAL for WebSocket
        wsTokenCookie.setMaxAge(accessTokenSeconds);  // ✅ Same expiration as access token (20 hours)
        response.addCookie(wsTokenCookie);

        // Add SameSite attribute via header for better WebSocket compatibility
        response.addHeader("Set-Cookie", String.format("wsToken=%s; Path=/; Domain=localhost; Max-Age=%d; SameSite=Lax",
            tokenResponse.getAccessToken(), accessTokenSeconds));

        log.info("✅ Set HTTP-only cookies for user: {} with long expiration for work management", tokenResponse.getUserInfo().getEmail());
        log.info("🔌 Set WebSocket token cookie ({}h expiration) for real-time chat authentication", accessTokenSeconds / 3600);
    }
}
