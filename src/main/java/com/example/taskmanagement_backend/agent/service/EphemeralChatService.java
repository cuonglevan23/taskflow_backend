package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.dto.ChatRequest;
import com.example.taskmanagement_backend.agent.dto.ChatResponse;
import com.example.taskmanagement_backend.agent.dto.ConversationDto;
import com.example.taskmanagement_backend.agent.dto.ModerationResult;
import com.example.taskmanagement_backend.agent.exception.AgentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ephemeral Chat Service - Tích hợp 2 tầng dữ liệu
 * Session Memory: Cho user experience (ephemeral)
 * Audit Log: Cho admin monitoring (persistent)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EphemeralChatService {

    private final SessionMemoryService sessionMemoryService;
    @Qualifier("agentAuditLogService")
    private final AuditLogService auditLogService;
    private final AIAgentWebSocketService aiAgentWebSocketService; // Add WebSocket service
    private final ModerationService moderationService;
    private final RetrieverService retrieverService;
    private final GeminiService geminiService;

    // NEW: Add repositories for database access
    private final com.example.taskmanagement_backend.agent.memory.ConversationRepository conversationRepository;
    private final com.example.taskmanagement_backend.agent.memory.ChatMessageRepository chatMessageRepository;
    private final UserContextService userContextService; // NEW: Add UserContextService to get user details

    /**
     * Start ephemeral conversation for user
     */
    public ConversationDto startEphemeralConversation(Long userId, String title, HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(true);
            String sessionId = session.getId();

            // Create session-based conversation (ephemeral)
            ConversationDto sessionConversation = sessionMemoryService.createSessionConversation(userId, sessionId);

            // Log to audit for admin monitoring (persistent)
            auditLogService.logConversationCreated(userId, sessionConversation.getConversationId(), sessionId);

            // Send welcome message
            sendWelcomeMessage(sessionConversation.getConversationId(), userId, sessionId, request);

            log.info("Started ephemeral conversation: {} for user: {}", sessionConversation.getConversationId(), userId);

            return sessionConversation;

        } catch (Exception e) {
            log.error("Failed to start ephemeral conversation for user: {}", userId, e);
            throw new AgentException("Failed to start ephemeral conversation: " + e.getMessage());
        }
    }

    /**
     * Process user message in ephemeral chat với WebSocket realtime
     */
    public ChatResponse processEphemeralMessage(String conversationId, Long userId,
                                              ChatRequest request, HttpServletRequest httpRequest) {
        try {
            HttpSession session = httpRequest.getSession(false);
            if (session == null) {
                throw new AgentException("Invalid session", "SESSION_EXPIRED", "AUTHENTICATION");
            }

            String sessionId = session.getId();

            // Validate session conversation
            ConversationDto conversation = sessionMemoryService.getSessionConversation(conversationId, userId);
            if (!conversation.isAgentActive()) {
                // Notify user via WebSocket that admin has taken over
                ChatResponse takeoverNotification = ChatResponse.builder()
                    .content("Cuộc trò chuyện đang được nhân viên hỗ trợ xử lý.")
                    .senderType("SYSTEM")
                    .timestamp(LocalDateTime.now())
                    .status("AGENT_INACTIVE")
                    .agentActive(false)
                    .build();

                aiAgentWebSocketService.sendMessageToUser(userId, takeoverNotification);

                throw new AgentException("AI Agent is not active - conversation taken over by supervisor",
                    "AGENT_INACTIVE", "CONVERSATION_STATE");
            }

            // Extend session timeout on activity
            sessionMemoryService.extendSessionTimeout(conversationId, userId);

            // Create and send user message via WebSocket immediately
            ChatResponse userMessage = ChatResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .content(request.getContent())
                .senderType("USER")
                .timestamp(LocalDateTime.now())
                .conversationId(conversationId)
                .success(true)
                .build();

            // Send user message via WebSocket first for immediate feedback
            aiAgentWebSocketService.sendMessageToUser(userId, userMessage);

            // Moderation check
            ModerationResult moderationResult = moderationService.moderateContent(request.getContent(), userId.toString());
            if (!moderationResult.isSafe()) {
                ChatResponse moderationResponse = createModerationResponse(moderationResult);

                // Send moderation response via WebSocket
                aiAgentWebSocketService.sendMessageToUser(userId, moderationResponse);

                // Still log to audit for monitoring
                auditLogService.logMessage(userId, conversationId, request, moderationResponse,
                    sessionId, getUserAgent(httpRequest), getClientIp(httpRequest));

                return moderationResponse;
            }

            // Show typing indicator via WebSocket
            ChatResponse typingIndicator = ChatResponse.builder()
                .content("AI đang xử lý...")
                .senderType("AGENT")
                .timestamp(LocalDateTime.now())
                .status("TYPING")
                .conversationId(conversationId)
                .build();

            aiAgentWebSocketService.sendMessageToUser(userId, typingIndicator);

            // Get conversation context from session
            List<ChatResponse> sessionMessages = sessionMemoryService.getSessionMessages(conversationId, userId);
            String conversationContext = buildContextFromMessages(sessionMessages);

            // RAG retrieval
            String ragContext = retrieverService.retrieveContext(request.getContent(), conversationContext);

            // Generate AI response
            String aiResponse = geminiService.generateResponse(request.getContent(), conversationContext, ragContext);
            String intent = geminiService.detectIntent(request.getContent());
            double confidence = geminiService.calculateConfidence(request.getContent(), aiResponse, ragContext);

            // Create final AI response
            ChatResponse response = ChatResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .content(aiResponse)
                .senderType("AGENT")
                .timestamp(LocalDateTime.now())
                .aiModel("gemini-pro-mock")
                .confidence(confidence)
                .intent(intent)
                .context(ragContext)
                .success(true)
                .status("PROCESSED")
                .conversationId(conversationId)
                .agentActive(true)
                .build();

            // Store in session memory (ephemeral)
            sessionMemoryService.storeSessionMessage(conversationId, userMessage);
            sessionMemoryService.storeSessionMessage(conversationId, response);

            // Send AI response via WebSocket
            aiAgentWebSocketService.sendMessageToUser(userId, response);

            // Check if intervention is needed and alert admin
            if (analyzeInterventionNeeded(sessionMessages)) {
                String reason = detectInterventionReason(sessionMessages);
                aiAgentWebSocketService.notifyAdminOfInterventionNeeded(userId, reason);
            }

            // Log to audit (persistent - admin sees this)
            auditLogService.logMessage(userId, conversationId, request, response,
                sessionId, getUserAgent(httpRequest), getClientIp(httpRequest));

            return response;

        } catch (AgentException e) {
            log.error("Agent error processing ephemeral message", e);
            ChatResponse errorResponse = createErrorResponse(e.getMessage(), e.getErrorCode());

            // Send error via WebSocket
            aiAgentWebSocketService.sendMessageToUser(userId, errorResponse);

            return errorResponse;
        } catch (Exception e) {
            log.error("Unexpected error processing ephemeral message", e);
            ChatResponse errorResponse = createErrorResponse("Internal server error", "INTERNAL_ERROR");

            // Send error via WebSocket
            aiAgentWebSocketService.sendMessageToUser(userId, errorResponse);

            return errorResponse;
        }
    }

    /**
     * Get user's session messages (ephemeral - only current session)
     */
    public List<ChatResponse> getUserSessionMessages(String conversationId, Long userId) {
        return sessionMemoryService.getSessionMessages(conversationId, userId);
    }

    /**
     * Get user's active session conversations (ephemeral)
     */
    public List<ConversationDto> getUserSessionConversations(Long userId) {
        return sessionMemoryService.getUserSessionConversations(userId);
    }

    /**
     * Handle user logout - clear session data
     */
    public void handleUserLogout(Long userId, HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                String sessionId = session.getId();

                // Log session end to audit
                auditLogService.logSessionEnd(userId, sessionId, "user_logout");

                // Clear ephemeral session data
                sessionMemoryService.clearUserSession(userId, sessionId);

                // Invalidate HTTP session
                session.invalidate();

                log.info("Cleared ephemeral session data for user: {} on logout", userId);
            }
        } catch (Exception e) {
            log.error("Error handling user logout", e);
        }
    }

    /**
     * Handle session timeout - clear expired data
     */
    public void handleSessionTimeout(Long userId, String sessionId) {
        try {
            // Log session end to audit
            auditLogService.logSessionEnd(userId, sessionId, "session_timeout");

            // Clear ephemeral session data
            sessionMemoryService.clearUserSession(userId, sessionId);

            log.info("Cleared ephemeral session data for user: {} due to timeout", userId);
        } catch (Exception e) {
            log.error("Error handling session timeout", e);
        }
    }

    /**
     * Admin takeover ephemeral conversation với WebSocket notifications
     */
    public ChatResponse takeoverEphemeralConversation(String conversationId, Long supervisorId, HttpServletRequest request) {
        try {
            // Log takeover to audit
            auditLogService.logTakeoverEvent(conversationId, supervisorId, "takeover");

            // Get userId from conversation
            Long userId = getUserIdFromConversation(conversationId);

            // Create takeover response for session
            ChatResponse takeoverResponse = ChatResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .content("Nhân viên hỗ trợ đã tham gia cuộc trò chuyện. Tôi sẽ giúp bạn giải quyết vấn đề này.")
                .senderType("SUPERVISOR")
                .timestamp(LocalDateTime.now())
                .success(true)
                .status("TAKEOVER_SUCCESS")
                .conversationId(conversationId)
                .agentActive(false)
                .supervisorId(supervisorId.toString())
                .build();

            // Store takeover message in session
            sessionMemoryService.storeSessionMessage(conversationId, takeoverResponse);

            // Send realtime notification to user via WebSocket
            if (userId != null) {
                aiAgentWebSocketService.notifyUserOfTakeover(userId, supervisorId);
                aiAgentWebSocketService.sendMessageToUser(userId, takeoverResponse);
            }

            log.info("Supervisor {} took over ephemeral conversation: {}", supervisorId, conversationId);

            return takeoverResponse;

        } catch (Exception e) {
            log.error("Error during ephemeral conversation takeover", e);
            throw new AgentException("Takeover failed: " + e.getMessage());
        }
    }

    /**
     * Admin send message during takeover với WebSocket realtime
     */
    public ChatResponse sendAdminMessage(String conversationId, Long supervisorId,
                                       ChatRequest request, HttpServletRequest httpRequest) {
        try {
            log.info("Admin {} sending message in conversation {}", supervisorId, conversationId);

            // Get userId from conversation
            Long userId = getUserIdFromConversation(conversationId);

            // Create admin message
            ChatResponse adminMessage = ChatResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .content(request.getContent())
                .senderType("SUPERVISOR")
                .timestamp(LocalDateTime.now())
                .success(true)
                .status("ADMIN_MESSAGE")
                .conversationId(conversationId)
                .agentActive(false)
                .supervisorId(supervisorId.toString())
                .build();

            // Store in session
            sessionMemoryService.storeSessionMessage(conversationId, adminMessage);

            // Send realtime to user via WebSocket
            if (userId != null) {
                aiAgentWebSocketService.sendAdminMessageToUser(userId, supervisorId, adminMessage);
            }

            // Log to audit (handle null httpRequest for WebSocket calls)
            if (httpRequest != null) {
                auditLogService.logMessage(null, conversationId, request, adminMessage,
                    httpRequest.getSession().getId(),
                    httpRequest.getHeader("User-Agent"),
                    getClientIp(httpRequest));
            } else {
                // WebSocket call - create mock audit entry
                auditLogService.logMessage(null, conversationId, request, adminMessage,
                    "WEBSOCKET_SESSION", "WebSocket", "127.0.0.1");
            }

            return adminMessage;

        } catch (Exception e) {
            log.error("Error sending admin message", e);
            throw new AgentException("Admin message failed: " + e.getMessage());
        }
    }

    /**
     * Return conversation to AI Assistant với WebSocket notifications
     */
    public ChatResponse returnConversationToAI(String conversationId, Long supervisorId, HttpServletRequest request) {
        try {
            // Log return to audit
            auditLogService.logTakeoverEvent(conversationId, supervisorId, "return_to_agent");

            // Get userId from conversation
            Long userId = getUserIdFromConversation(conversationId);

            // Create return message
            ChatResponse returnMessage = ChatResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .content("AI Assistant đã được khôi phục. Tôi sẽ tiếp tục hỗ trợ bạn.")
                .senderType("AGENT")
                .timestamp(LocalDateTime.now())
                .success(true)
                .status("RETURN_TO_AGENT")
                .conversationId(conversationId)
                .agentActive(true)
                .build();

            // Store return message in session
            sessionMemoryService.storeSessionMessage(conversationId, returnMessage);

            // Send realtime notification to user via WebSocket
            if (userId != null) {
                aiAgentWebSocketService.notifyUserOfReturn(userId);
                aiAgentWebSocketService.sendMessageToUser(userId, returnMessage);
            }

            log.info("Conversation {} returned to AI by supervisor {}", conversationId, supervisorId);

            return returnMessage;

        } catch (Exception e) {
            log.error("Error returning conversation to AI", e);
            throw new AgentException("Return to AI failed: " + e.getMessage());
        }
    }

    /**
     * Get all active user sessions for admin monitoring
     */
    public List<Map<String, Object>> getAllActiveUserSessions() {
        try {
            // This would query Redis for all active sessions
            // For now, return mock data structure
            return List.of(
                Map.of(
                    "userId", 123L,
                    "userEmail", "user@example.com",
                    "conversationId", "session_123_xxx",
                    "messageCount", 5,
                    "lastActivity", LocalDateTime.now().minusMinutes(2),
                    "agentActive", true,
                    "needsIntervention", false
                ),
                Map.of(
                    "userId", 456L,
                    "userEmail", "user2@example.com",
                    "conversationId", "session_456_xxx",
                    "messageCount", 12,
                    "lastActivity", LocalDateTime.now().minusMinutes(10),
                    "agentActive", false,
                    "needsIntervention", true,
                    "supervisorId", 999L
                )
            );
        } catch (Exception e) {
            log.error("Error getting all active user sessions", e);
            return List.of();
        }
    }

    /**
     * Get user conversation status for admin
     */
    public Map<String, Object> getUserConversationStatus(Long userId) {
        try {
            List<ConversationDto> conversations = getUserSessionConversations(userId);

            if (conversations.isEmpty()) {
                return Map.of(
                    "userId", userId,
                    "hasActiveConversation", false,
                    "status", "NO_CONVERSATION"
                );
            }

            ConversationDto conversation = conversations.get(0);
            List<ChatResponse> messages = sessionMemoryService.getSessionMessages(conversation.getConversationId(), userId);

            // Analyze if intervention is needed
            boolean needsIntervention = analyzeInterventionNeeded(messages);

            return Map.of(
                "userId", userId,
                "conversationId", conversation.getConversationId(),
                "hasActiveConversation", true,
                "agentActive", conversation.isAgentActive(),
                "messageCount", messages.size(),
                "lastActivity", conversation.getLastActivity(),
                "needsIntervention", needsIntervention,
                "interventionReason", needsIntervention ? detectInterventionReason(messages) : null,
                "status", conversation.getStatus(),
                "supervisorId", conversation.getSupervisorId()
            );

        } catch (Exception e) {
            log.error("Error getting user conversation status", e);
            return Map.of(
                "userId", userId,
                "status", "ERROR",
                "error", e.getMessage()
            );
        }
    }

    /**
     * Get session statistics for user
     */
    public Map<String, Object> getUserSessionStats(Long userId) {
        return sessionMemoryService.getSessionStats(userId);
    }

    /**
     * Get all conversations from database (admin only) - FIXED to use persistent data with user info
     */
    public List<ConversationDto> getAllConversations() {
        log.info("Admin getting all conversations from database with user details");

        try {
            // Get all conversations from database instead of session memory
            List<com.example.taskmanagement_backend.agent.entity.Conversation> conversations =
                conversationRepository.findAll();

            // Convert to DTOs with user information
            List<ConversationDto> conversationDtos = new ArrayList<>();
            for (com.example.taskmanagement_backend.agent.entity.Conversation conv : conversations) {
                if (!conv.getIsDeleted()) {
                    // Get user information from UserContextService
                    UserContextService.UserChatContext userContext = null;
                    try {
                        userContext = userContextService.getUserChatContext(conv.getUserId());
                    } catch (Exception e) {
                        log.warn("Could not get user context for user: {}", conv.getUserId(), e);
                    }

                    ConversationDto dto = ConversationDto.builder()
                        .conversationId(conv.getConversationId())
                        .userId(conv.getUserId())
                        .title(conv.getTitle())
                        .status(conv.getStatus().toString())
                        .createdAt(conv.getCreatedAt())
                        .updatedAt(conv.getUpdatedAt())
                        .agentActive(conv.getAgentActive())
                        .lastActivity(conv.getLastActivityAt())
                        // NEW: Add user information from UserContextService
                        .userEmail(userContext != null ? userContext.getEmail() : "unknown@example.com")
                        .build();

                    // Get message count from chat messages
                    Long messageCount = chatMessageRepository.countByConversationId(conv.getConversationId());
                    dto.setMessageCount(messageCount != null ? messageCount.intValue() : 0);

                    conversationDtos.add(dto);
                }
            }

            log.info("Retrieved {} conversations from database with user details", conversationDtos.size());
            return conversationDtos;

        } catch (Exception e) {
            log.error("Error getting all conversations from database", e);
            // Fallback to session memory if database fails
            log.warn("Falling back to session memory");
            return sessionMemoryService.getAllConversations();
        }
    }

    // Helper methods
    private void sendWelcomeMessage(String conversationId, Long userId, String sessionId, HttpServletRequest request) {
        ChatResponse welcomeMessage = ChatResponse.builder()
            .messageId(UUID.randomUUID().toString())
            .content("Xin chào! Tôi là AI Assistant của Taskflow. Cuộc trò chuyện này chỉ tồn tại trong phiên đăng nhập hiện tại. Tôi có thể giúp gì cho bạn?")
            .senderType("AGENT")
            .timestamp(LocalDateTime.now())
            .conversationId(conversationId)
            .success(true)
            .build();

        // Store in session
        sessionMemoryService.storeSessionMessage(conversationId, welcomeMessage);

        // Log to audit
        ChatRequest welcomeRequest = ChatRequest.builder()
            .content("SYSTEM: Welcome message")
            .build();
        auditLogService.logMessage(userId, conversationId, welcomeRequest, welcomeMessage,
            sessionId, getUserAgent(request), getClientIp(request));
    }

    /**
     * Analyze if admin intervention is needed
     */
    private boolean analyzeInterventionNeeded(List<ChatResponse> messages) {
        if (messages.size() < 3) {
            return false; // Too few messages to determine
        }

        // Check last few AI responses for failure indicators
        List<ChatResponse> recentAIMessages = messages.stream()
            .filter(msg -> "AGENT".equals(msg.getSenderType()))
            .skip(Math.max(0, messages.size() - 3))
            .toList();

        for (ChatResponse aiMessage : recentAIMessages) {
            String content = aiMessage.getContent().toLowerCase();

            // Check for AI confusion indicators
            if (content.contains("xin lỗi, tôi không hiểu") ||
                content.contains("tôi chưa hiểu") ||
                content.contains("vui lòng giải thích rõ hơn") ||
                content.contains("tôi không thể") ||
                aiMessage.getStatus().contains("ERROR") ||
                aiMessage.getStatus().contains("FALLBACK")) {
                return true;
            }
        }

        // Check for repeated user frustration
        List<ChatResponse> recentUserMessages = messages.stream()
            .filter(msg -> "USER".equals(msg.getSenderType()))
            .skip(Math.max(0, messages.size() - 3))
            .toList();

        for (ChatResponse userMessage : recentUserMessages) {
            String content = userMessage.getContent().toLowerCase();
            if (content.contains("không hiểu") ||
                content.contains("sai rồi") ||
                content.contains("không đúng") ||
                content.contains("không giúp được gì") ||
                content.contains("muốn nói chuyện với người thật")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Detect reason for intervention
     */
    private String detectInterventionReason(List<ChatResponse> messages) {
        List<ChatResponse> recentMessages = messages.stream()
            .skip(Math.max(0, messages.size() - 5))
            .toList();

        for (ChatResponse message : recentMessages) {
            String content = message.getContent().toLowerCase();

            if (content.contains("error") || content.contains("lỗi")) {
                return "AI_ERROR_DETECTED";
            }
            if (content.contains("không hiểu") || content.contains("chưa hiểu")) {
                return "AI_COMPREHENSION_ISSUE";
            }
            if (content.contains("sai") || content.contains("không đúng")) {
                return "AI_INCORRECT_RESPONSE";
            }
            if (content.contains("người thật") || content.contains("nhân viên")) {
                return "USER_REQUESTS_HUMAN";
            }
        }

        return "GENERAL_ASSISTANCE_NEEDED";
    }

    private String buildContextFromMessages(List<ChatResponse> messages) {
        if (messages == null || messages.isEmpty()) {
            return "New conversation - no previous context";
        }

        StringBuilder context = new StringBuilder("Recent conversation:\n");
        int count = 0;
        for (int i = Math.max(0, messages.size() - 5); i < messages.size() && count < 5; i++) {
            ChatResponse msg = messages.get(i);
            context.append(msg.getSenderType()).append(": ").append(msg.getContent()).append("\n");
            count++;
        }

        return context.toString();
    }

    private String getClientIp(HttpServletRequest request) {
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

    private String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown";
    }

    private ChatResponse createModerationResponse(ModerationResult moderationResult) {
        return ChatResponse.builder()
            .content("Xin lỗi, tin nhắn của bạn vi phạm quy định cộng đồng và không thể được xử lý.")
            .senderType("SYSTEM")
            .timestamp(LocalDateTime.now())
            .success(false)
            .status("MODERATED")
            .errorMessage("Content moderation failed: " + moderationResult.getReason())
            .build();
    }

    private ChatResponse createErrorResponse(String message, String errorCode) {
        return ChatResponse.builder()
            .content("Xin lỗi, tôi đang gặp sự cố kỹ thuật. Vui lòng thử lại sau.")
            .senderType("SYSTEM")
            .timestamp(LocalDateTime.now())
            .success(false)
            .status("ERROR")
            .errorMessage(message)
            .build();
    }

    // Helper method để lấy userId từ conversationId
    private Long getUserIdFromConversation(String conversationId) {
        try {
            // Extract userId from session conversation ID format: "session_{userId}_{timestamp}_{sessionId}"
            if (conversationId.startsWith("session_")) {
                String[] parts = conversationId.split("_");
                if (parts.length >= 2) {
                    return Long.valueOf(parts[1]);
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting userId from conversationId: {}", conversationId, e);
            return null;
        }
    }
}
