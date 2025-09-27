package com.example.taskmanagement_backend.config;

import com.example.taskmanagement_backend.services.ChatRedisService;
import com.example.taskmanagement_backend.services.UserService;
import com.example.taskmanagement_backend.services.infrastructure.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatRedisService chatRedisService;
    private final UserService userService;
    private final JwtTokenService jwtTokenService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for destinations prefixed with /topic and /queue
        config.enableSimpleBroker("/topic", "/queue");

        // Set application destination prefix
        config.setApplicationDestinationPrefixes("/app");

        // Set user destination prefix for personal messages
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register WebSocket endpoint with SockJS fallback
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*") // Allow all origins for development
                .addInterceptors(new JwtHandshakeInterceptor())
                .withSockJS()
                .setHeartbeatTime(25000) // Set heartbeat interval
                .setDisconnectDelay(5000); // Set disconnect delay

        // Also register without SockJS for native WebSocket support
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*") // Allow all origins for development
                .addInterceptors(new JwtHandshakeInterceptor());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    handleConnect(accessor);
                } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    handleDisconnect(accessor);
                }

                return message;
            }
        });
    }

    private class JwtHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

            if (request instanceof ServletServerHttpRequest) {
                ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                HttpServletRequest httpRequest = servletRequest.getServletRequest();

                String accessToken = null;
                String tokenSource = null;

                // 🔌 PRIORITY 1: Try to get JWT from WebSocket token cookie (NOT HTTP-only)
                if (httpRequest.getCookies() != null) {
                    for (Cookie cookie : httpRequest.getCookies()) {
                        if ("wsToken".equals(cookie.getName())) {
                            accessToken = cookie.getValue();
                            tokenSource = "wsToken cookie";
                            log.debug("🔌 Found WebSocket token from wsToken cookie");
                            break;
                        }
                    }
                }

                // PRIORITY 2: Fallback to HTTP-only access token cookie
                if (accessToken == null && httpRequest.getCookies() != null) {
                    for (Cookie cookie : httpRequest.getCookies()) {
                        if ("accessToken".equals(cookie.getName())) {
                            accessToken = cookie.getValue();
                            tokenSource = "accessToken cookie (HTTP-only)";
                            log.debug("🍪 Found token from accessToken cookie (HTTP-only)");
                            break;
                        }
                    }
                }

                // PRIORITY 3: Try Authorization header
                if (accessToken == null) {
                    String authHeader = httpRequest.getHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        accessToken = authHeader.substring(7);
                        tokenSource = "Authorization header";
                        log.debug("📝 Found token from Authorization header");
                    }
                }

                // PRIORITY 4: Try query parameter (for WebSocket connections)
                if (accessToken == null) {
                    accessToken = httpRequest.getParameter("token");
                    if (accessToken != null) {
                        tokenSource = "query parameter";
                        log.debug("🔗 Found token from query parameter");
                    }
                }

                if (accessToken != null && !accessToken.trim().isEmpty()) {
                    try {
                        // Validate JWT token
                        if (jwtTokenService.validateToken(accessToken)) {
                            String userEmail = jwtTokenService.getUserEmailFromToken(accessToken);

                            // Get user ID from UserService with safe type conversion
                            Long userId = userService.getUserIdByEmailDirect(userEmail);

                            if (userId != null) {
                                attributes.put("userId", userId);
                                attributes.put("email", userEmail);
                                attributes.put("authenticated", true);
                                attributes.put("accessToken", accessToken);
                                attributes.put("tokenSource", tokenSource);

                                log.info("✅ WebSocket handshake successful for user {} (id: {}) via {}", userEmail, userId, tokenSource);
                                return true;
                            } else {
                                log.debug("❌ Could not find userId for email: {}", userEmail);
                                response.getHeaders().add("WebSocket-Auth-Error", "User not found");
                            }
                        } else {
                            log.debug("❌ Invalid JWT token in WebSocket handshake (source: {})", tokenSource);
                            response.getHeaders().add("WebSocket-Auth-Error", "Invalid or expired JWT token");
                        }
                    } catch (Exception e) {
                        log.debug("❌ Error validating JWT during handshake (source: {}): {}", tokenSource, e.getMessage());
                        response.getHeaders().add("WebSocket-Auth-Error", "Token validation failed");
                    }
                } else {
                    // Reduced log spam - track failed attempts per IP
                    String clientIP = httpRequest.getRemoteAddr();
                    if (shouldLogFailedAttempt(clientIP)) {
                        log.warn("❌ No JWT token found in WebSocket handshake from {} (checked wsToken, accessToken, Authorization header, query param)", clientIP);

                        // Log cookie debug info periodically
                        if (httpRequest.getCookies() != null) {
                            log.debug("🍪 Available cookies: {}",
                                java.util.Arrays.stream(httpRequest.getCookies())
                                    .map(c -> c.getName() + "=" + (c.getValue() != null && c.getValue().length() > 10 ?
                                        c.getValue().substring(0, 10) + "..." : c.getValue()))
                                    .collect(java.util.stream.Collectors.joining(", ")));
                        } else {
                            log.debug("🍪 No cookies found in request");
                        }
                    }

                    response.getHeaders().add("WebSocket-Auth-Error", "No authentication token provided");
                }
            }

            return false; // Reject connection if not authenticated
        }

        // Track failed attempts to reduce log spam
        private final Map<String, FailedAttemptInfo> failedAttempts = new java.util.concurrent.ConcurrentHashMap<>();

        private static class FailedAttemptInfo {
            int count;
            long lastAttempt;

            FailedAttemptInfo() {
                this.count = 1;
                this.lastAttempt = System.currentTimeMillis();
            }

            void increment() {
                this.count++;
                this.lastAttempt = System.currentTimeMillis();
            }
        }

        private boolean shouldLogFailedAttempt(String clientIP) {
            long now = System.currentTimeMillis();
            FailedAttemptInfo info = failedAttempts.compute(clientIP, (ip, existing) -> {
                if (existing == null) {
                    return new FailedAttemptInfo();
                } else {
                    // Reset counter if more than 5 minutes have passed
                    if (now - existing.lastAttempt > 300000) { // 5 minutes
                        return new FailedAttemptInfo();
                    } else {
                        existing.increment();
                        return existing;
                    }
                }
            });

            // Log first attempt, then every 10th attempt to reduce spam
            boolean shouldLog = info.count == 1 || info.count % 10 == 0;

            // Clean up old entries periodically
            if (info.count > 100) {
                failedAttempts.entrySet().removeIf(entry ->
                    now - entry.getValue().lastAttempt > 600000); // 10 minutes
            }

            return shouldLog;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            if (exception != null) {
                log.error("❌ WebSocket handshake error: {}", exception.getMessage());
            }
        }
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        try {
            Object userIdObj = accessor.getSessionAttributes().get("userId");
            Object emailObj = accessor.getSessionAttributes().get("email");

            if (userIdObj != null && emailObj != null) {
                Long userId = null;
                if (userIdObj instanceof Long) {
                    userId = (Long) userIdObj;
                } else if (userIdObj instanceof Integer) {
                    userId = ((Integer) userIdObj).longValue();
                }

                String email = (String) emailObj;

                log.info("🔌 WebSocket CONNECT - User: {} (ID: {})", email, userId);

                // Set user online status in Redis
                if (userId != null) {
                    chatRedisService.setUserOnline(userId, getServerInstance());

                    // Store user info in session for later use
                    accessor.getSessionAttributes().put("connectedAt", LocalDateTime.now());

                    log.debug("✅ User {} marked as online via WebSocket", userId);
                }
            } else {
                log.warn("⚠️ WebSocket CONNECT - Missing user info in session attributes");
            }
        } catch (Exception e) {
            log.error("❌ Error handling WebSocket connect: {}", e.getMessage(), e);
        }
    }

    private void handleDisconnect(StompHeaderAccessor accessor) {
        try {
            Object userIdObj = accessor.getSessionAttributes().get("userId");
            Object emailObj = accessor.getSessionAttributes().get("email");

            if (userIdObj != null && emailObj != null) {
                Long userId = null;
                if (userIdObj instanceof Long) {
                    userId = (Long) userIdObj;
                } else if (userIdObj instanceof Integer) {
                    userId = ((Integer) userIdObj).longValue();
                }

                String email = (String) emailObj;

                log.info("🔌 WebSocket DISCONNECT - User: {} (ID: {})", email, userId);

                // Set user offline status in Redis
                if (userId != null) {
                    chatRedisService.setUserOffline(userId);

                    log.debug("✅ User {} marked as offline via WebSocket", userId);
                }
            } else {
                log.warn("⚠️ WebSocket DISCONNECT - Missing user info in session attributes");
            }
        } catch (Exception e) {
            log.error("❌ Error handling WebSocket disconnect: {}", e.getMessage(), e);
        }
    }

    private String getServerInstance() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-server";
        }
    }

    // ChatUser class for WebSocket authentication
    public static class ChatUser implements Principal {
        private final Long userId;
        private final String email;

        public ChatUser(Long userId, String email) {
            this.userId = userId;
            this.email = email;
        }

        @Override
        public String getName() {
            return email;
        }

        public Long getUserId() {
            return userId;
        }

        public String getEmail() {
            return email;
        }
    }
}
