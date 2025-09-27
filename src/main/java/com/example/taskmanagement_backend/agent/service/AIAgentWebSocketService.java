package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Agent WebSocket Service - Kế thừa pattern từ ChatWebSocketService
 * Handles realtime messaging cho AI chat với admin monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAgentWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    // Track active WebSocket connections
    private final Set<Long> activeUserConnections = ConcurrentHashMap.newKeySet();
    private final Set<Long> activeAdminConnections = ConcurrentHashMap.newKeySet();
    private final Set<Long> monitoredUsers = ConcurrentHashMap.newKeySet(); // Users being monitored by admin

    // WebSocket destinations cho AI chat
    private static final String AI_CHAT_USER_QUEUE = "/queue/ai-chat/user/";
    private static final String AI_CHAT_ADMIN_QUEUE = "/queue/ai-chat/admin/";
    private static final String AI_CHAT_TOPIC = "/topic/ai-chat/";

    /**
     * Send AI response to user via WebSocket
     */
    public void sendMessageToUser(Long userId, ChatResponse message) {
        try {
            String destination = AI_CHAT_USER_QUEUE + userId + "/messages";
            messagingTemplate.convertAndSend(destination, message);

            log.debug("📤 Sent AI message to user {} via WebSocket", userId);

            // If user is being monitored by admin, also send to admin
            if (monitoredUsers.contains(userId)) {
                sendMessageToMonitoringAdmins(userId, message);
            }

        } catch (Exception e) {
            log.error("❌ Error sending AI message to user {} via WebSocket", userId, e);
        }
    }

    /**
     * Send message to admin monitoring user conversation
     */
    public void sendMessageToAdmin(Long adminId, Long userId, ChatResponse message) {
        try {
            String destination = AI_CHAT_ADMIN_QUEUE + adminId + "/user/" + userId + "/messages";
            messagingTemplate.convertAndSend(destination, message);

            log.debug("📤 Sent message to admin {} monitoring user {}", adminId, userId);

        } catch (Exception e) {
            log.error("❌ Error sending message to admin {} for user {}", adminId, userId, e);
        }
    }

    /**
     * Notify user about admin takeover via WebSocket
     */
    public void notifyUserOfTakeover(Long userId, Long adminId) {
        try {
            ChatResponse notification = ChatResponse.builder()
                .content("Nhân viên hỗ trợ đã tham gia cuộc trò chuyện để giúp bạn giải quyết vấn đề.")
                .senderType("SYSTEM")
                .status("TAKEOVER_NOTIFICATION")
                .agentActive(false)
                .supervisorId(adminId.toString())
                .build();

            sendMessageToUser(userId, notification);

            // Add user to monitored list
            monitoredUsers.add(userId);

            log.info("🚨 Notified user {} of admin {} takeover via WebSocket", userId, adminId);

        } catch (Exception e) {
            log.error("❌ Error notifying user {} of takeover", userId, e);
        }
    }

    /**
     * Notify user when conversation returned to AI
     */
    public void notifyUserOfReturn(Long userId) {
        try {
            ChatResponse notification = ChatResponse.builder()
                .content("AI Assistant đã được khôi phục và sẽ tiếp tục hỗ trợ bạn.")
                .senderType("SYSTEM")
                .status("RETURN_NOTIFICATION")
                .agentActive(true)
                .build();

            sendMessageToUser(userId, notification);

            // Remove user from monitored list
            monitoredUsers.remove(userId);

            log.info("✅ Notified user {} of return to AI via WebSocket", userId);

        } catch (Exception e) {
            log.error("❌ Error notifying user {} of return", userId, e);
        }
    }

    /**
     * Notify admin dashboard of user needing intervention
     */
    public void notifyAdminOfInterventionNeeded(Long userId, String reason) {
        try {
            ChatResponse alert = ChatResponse.builder()
                .content("User " + userId + " needs intervention: " + reason)
                .senderType("SYSTEM")
                .status("INTERVENTION_ALERT")
                .build();

            // Send to all active admin connections
            for (Long adminId : activeAdminConnections) {
                String destination = AI_CHAT_ADMIN_QUEUE + adminId + "/alerts";
                messagingTemplate.convertAndSend(destination, alert);
            }

            log.info("🚨 Alerted admins of intervention needed for user {}: {}", userId, reason);

        } catch (Exception e) {
            log.error("❌ Error alerting admins of intervention for user {}", userId, e);
        }
    }

    /**
     * Broadcast system message to all connected users
     */
    public void broadcastSystemMessage(String message, String status) {
        try {
            ChatResponse systemMessage = ChatResponse.builder()
                .content(message)
                .senderType("SYSTEM")
                .status(status)
                .build();

            // Send to all active user connections
            for (Long userId : activeUserConnections) {
                sendMessageToUser(userId, systemMessage);
            }

            log.info("📢 Broadcasted system message to {} users", activeUserConnections.size());

        } catch (Exception e) {
            log.error("❌ Error broadcasting system message", e);
        }
    }

    /**
     * Send admin message to specific user (during takeover)
     */
    public void sendAdminMessageToUser(Long userId, Long adminId, ChatResponse message) {
        try {
            // Send to user
            sendMessageToUser(userId, message);

            // Also send copy to admin for confirmation
            sendMessageToAdmin(adminId, userId, message);

            log.debug("📤 Admin {} message sent to user {} via WebSocket", adminId, userId);

        } catch (Exception e) {
            log.error("❌ Error sending admin message to user via WebSocket", e);
        }
    }

    /**
     * Track user connection status
     */
    public void addUserConnection(Long userId) {
        activeUserConnections.add(userId);
        log.debug("➕ Added user {} to active WebSocket connections", userId);
    }

    public void removeUserConnection(Long userId) {
        activeUserConnections.remove(userId);
        monitoredUsers.remove(userId);
        log.debug("➖ Removed user {} from active WebSocket connections", userId);
    }

    /**
     * Track admin connection status
     */
    public void addAdminConnection(Long adminId) {
        activeAdminConnections.add(adminId);
        log.debug("➕ Added admin {} to active WebSocket connections", adminId);
    }

    public void removeAdminConnection(Long adminId) {
        activeAdminConnections.remove(adminId);
        log.debug("➖ Removed admin {} from active WebSocket connections", adminId);
    }

    /**
     * Check if admin is monitoring user
     */
    public boolean isUserMonitored(Long userId) {
        return monitoredUsers.contains(userId);
    }

    /**
     * Start monitoring user by admin
     */
    public void startMonitoringUser(Long userId, Long adminId) {
        monitoredUsers.add(userId);
        log.info("👀 Admin {} started monitoring user {}", adminId, userId);
    }

    /**
     * Stop monitoring user by admin
     */
    public void stopMonitoringUser(Long userId, Long adminId) {
        monitoredUsers.remove(userId);
        log.info("👁️ Admin {} stopped monitoring user {}", adminId, userId);
    }

    /**
     * Get connection statistics
     */
    public ConnectionStats getConnectionStats() {
        return ConnectionStats.builder()
            .activeUsers(activeUserConnections.size())
            .activeAdmins(activeAdminConnections.size())
            .monitoredUsers(monitoredUsers.size())
            .build();
    }

    // Helper methods
    private void sendMessageToMonitoringAdmins(Long userId, ChatResponse message) {
        try {
            for (Long adminId : activeAdminConnections) {
                sendMessageToAdmin(adminId, userId, message);
            }
        } catch (Exception e) {
            log.error("Error sending message to monitoring admins", e);
        }
    }

    private void notifyAdminsOfMessage(Long userId, ChatResponse message) {
        try {
            // Send notification to admin dashboard
            String destination = AI_CHAT_TOPIC + "admin/notifications";
            messagingTemplate.convertAndSend(destination, Map.of(
                "userId", userId,
                "message", message,
                "type", "ADMIN_MESSAGE_SENT"
            ));
        } catch (Exception e) {
            log.error("Error notifying admins of message", e);
        }
    }

    public void notifyAdminIfMonitoring(Long userId, ChatResponse response) {
        if (monitoredUsers.contains(userId)) {
            sendMessageToMonitoringAdmins(userId, response);
        }
    }

    // Inner class for connection statistics
    public static class ConnectionStats {
        private int activeUsers;
        private int activeAdmins;
        private int monitoredUsers;

        public static ConnectionStatsBuilder builder() {
            return new ConnectionStatsBuilder();
        }

        public static class ConnectionStatsBuilder {
            private int activeUsers;
            private int activeAdmins;
            private int monitoredUsers;

            public ConnectionStatsBuilder activeUsers(int activeUsers) {
                this.activeUsers = activeUsers;
                return this;
            }

            public ConnectionStatsBuilder activeAdmins(int activeAdmins) {
                this.activeAdmins = activeAdmins;
                return this;
            }

            public ConnectionStatsBuilder monitoredUsers(int monitoredUsers) {
                this.monitoredUsers = monitoredUsers;
                return this;
            }

            public ConnectionStats build() {
                ConnectionStats stats = new ConnectionStats();
                stats.activeUsers = this.activeUsers;
                stats.activeAdmins = this.activeAdmins;
                stats.monitoredUsers = this.monitoredUsers;
                return stats;
            }
        }

        // Getters
        public int getActiveUsers() { return activeUsers; }
        public int getActiveAdmins() { return activeAdmins; }
        public int getMonitoredUsers() { return monitoredUsers; }
    }
}
