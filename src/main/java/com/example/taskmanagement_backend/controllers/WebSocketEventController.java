package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.services.NotificationRedisService;
import com.example.taskmanagement_backend.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

/**
 * WebSocket event handler for chat connections only
 * Handles user connect/disconnect events for chat and notifications
 * NOTE: Online status is managed separately using DB-based approach in profile endpoints
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketEventController {

    private final NotificationRedisService redisService;
    private final NotificationService notificationService;

    /**
     * Handle user connection to WebSocket for chat and notifications only
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        try {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();

            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

            if (sessionAttributes != null) {
                Long userId = (Long) sessionAttributes.get("userId");
                String email = (String) sessionAttributes.get("email");

                if (userId != null && email != null) {
                    // Add user session to Redis for chat functionality only
                    redisService.addUserSession(userId, sessionId);

                    // Sync notifications for the user
                    notificationService.syncNotificationsOnLogin(userId);

                    log.info("User {} connected to WebSocket, session: {}", userId, sessionId);

                } else {
                    log.warn("❌ WebSocket connection without valid user info, session: {}", sessionId);
                }
            } else {
                log.warn("❌ WebSocket connection without session attributes, session: {}", sessionId);
            }

        } catch (Exception e) {
            log.error("Error handling WebSocket connect event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle user disconnection from WebSocket
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        try {
            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();

            // Get user ID from session
            Long userId = redisService.getUserFromSession(sessionId);

            if (userId != null) {
                // Remove user session from Redis
                redisService.removeUserSession(userId, sessionId);

                // Check if user still has other active chat sessions
                boolean isStillInChat = redisService.isUserOnline(userId);

                log.info("User {} disconnected from chat, session: {}, still has chat sessions: {}", userId, sessionId, isStillInChat);
            } else {
                log.warn("WebSocket disconnection for unknown session: {}", sessionId);
            }

        } catch (Exception e) {
            log.error("Error handling WebSocket disconnect event: {}", e.getMessage(), e);
        }
    }
}
