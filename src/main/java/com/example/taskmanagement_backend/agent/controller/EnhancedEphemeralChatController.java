package com.example.taskmanagement_backend.agent.controller;

import com.example.taskmanagement_backend.agent.dto.ChatRequest;
import com.example.taskmanagement_backend.agent.dto.ChatResponse;
import com.example.taskmanagement_backend.agent.dto.ConversationDto;
import com.example.taskmanagement_backend.agent.dto.ChatAnalysisRequest;
import com.example.taskmanagement_backend.agent.dto.ChatAnalysisResponse;
import com.example.taskmanagement_backend.agent.entity.ChatMessage;
import com.example.taskmanagement_backend.agent.service.AdminDashboardService;
import com.example.taskmanagement_backend.agent.service.CoreAgentService;
import com.example.taskmanagement_backend.agent.service.EphemeralChatService;
import com.example.taskmanagement_backend.agent.service.UserContextService;
import com.example.taskmanagement_backend.agent.service.ChatAnalysisService;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.SystemRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

/**
 * Enhanced Ephemeral Chat Controller - Single Conversation per User
 * User chỉ có duy nhất 1 đoạn chat, tự động tạo khi cần
 * ENHANCED: Tích hợp full role-based security
 */
@Slf4j
@RestController
@RequestMapping("/api/ai-agent")
@RequiredArgsConstructor
public class EnhancedEphemeralChatController {

    private final CoreAgentService coreAgentService;
    private final EphemeralChatService ephemeralChatService;
    private final AdminDashboardService adminDashboardService;
    private final UserContextService userContextService; // NEW: For role validation
    private final UserJpaRepository userRepository; // NEW: For database role check
    private final ChatAnalysisService chatAnalysisService; // NEW: For chat analysis features

    // NEW: Add repositories for direct database access in admin endpoints
    private final com.example.taskmanagement_backend.agent.memory.ChatMessageRepository chatMessageRepository;
    private final com.example.taskmanagement_backend.agent.memory.ConversationRepository conversationRepository;

    // ======================== USER APIs (Single Conversation) ========================

    /**
     * Send message - tự động tạo conversation nếu chưa có
     * POST /api/ai-agent/messages?projectId=123
     */
    @PostMapping("/messages")
    public ResponseEntity<ChatResponse> sendMessage(
            @Valid @RequestBody ChatRequest request,
            @RequestParam(required = false) Long projectId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long userId = getUserIdFromAuth(authentication);
        log.info("Processing message: user={}, project={}", userId, projectId);

        // Tự động lấy hoặc tạo conversation duy nhất cho user
        String conversationId = getOrCreateUserConversation(userId, httpRequest);

        // Use Core Agent Service với full pipeline
        ChatResponse response = coreAgentService.processUserMessage(
            conversationId, userId, request, httpRequest, projectId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get user's single conversation (for frontend compatibility)
     * GET /api/ai-agent/conversations
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDto>> getUserConversations(
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        log.info("Getting conversations for user: {}", userId);

        // Since this is a single conversation per user system, return the single conversation or empty list
        List<ConversationDto> conversations = ephemeralChatService.getUserSessionConversations(userId);

        // If no conversation exists, optionally create one (depending on your business logic)
        // For now, just return empty list to match the existing pattern
        return ResponseEntity.ok(conversations);
    }

    /**
     * Create or start a new conversation (for frontend compatibility)
     * POST /api/ai-agent/conversations
     */
    @PostMapping("/conversations")
    public ResponseEntity<ConversationDto> createConversation(
            @RequestBody(required = false) Map<String, String> request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long userId = getUserIdFromAuth(authentication);
        log.info("Creating conversation for user: {}", userId);

        // Check if user already has a conversation (single conversation per user system)
        List<ConversationDto> existingConversations = ephemeralChatService.getUserSessionConversations(userId);

        if (!existingConversations.isEmpty()) {
            // Return existing conversation
            log.info("User {} already has a conversation, returning existing one", userId);
            return ResponseEntity.ok(existingConversations.get(0));
        }

        // Create new conversation
        String title = request != null ? request.getOrDefault("title", "AI Chat Session") : "AI Chat Session";
        ConversationDto newConversation = ephemeralChatService.startEphemeralConversation(userId, title, httpRequest);

        log.info("Created new conversation {} for user {}", newConversation.getConversationId(), userId);
        return ResponseEntity.ok(newConversation);
    }

    /**
     * Get session messages (ephemeral)
     * GET /api/ai-agent/messages
     */
    @GetMapping("/messages")
    public ResponseEntity<List<ChatResponse>> getSessionMessages(
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);

        // Lấy conversation duy nhất của user
        String conversationId = getUserConversation(userId);
        if (conversationId == null) {
            // Trả về empty list nếu chưa có conversation
            return ResponseEntity.ok(List.of());
        }

        List<ChatResponse> messages = ephemeralChatService.getUserSessionMessages(conversationId, userId);
        return ResponseEntity.ok(messages);
    }

    /**
     * Clear session (logout hoặc reset chat)
     * DELETE /api/ai-agent/messages
     */
    @DeleteMapping("/messages")
    public ResponseEntity<Map<String, String>> clearSession(
            Authentication authentication,
            HttpServletRequest request) {

        Long userId = getUserIdFromAuth(authentication);
        log.info("User {} clearing session", userId);

        ephemeralChatService.handleUserLogout(userId, request);

        return ResponseEntity.ok(Map.of(
            "message", "Chat session cleared successfully",
            "note", "Your conversation history has been removed"
        ));
    }

    /**
     * Get system health and agent status
     * GET /api/ai-agent/system/health
     */
    @GetMapping("/system/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = coreAgentService.getSystemHealth();
        return ResponseEntity.ok(health);
    }

    /**
     * Get session statistics
     * GET /api/ai-agent/session/stats
     */
    @GetMapping("/session/stats")
    public ResponseEntity<Map<String, Object>> getSessionStats(
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Map<String, Object> stats = ephemeralChatService.getUserSessionStats(userId);

        // Thêm thông tin về single conversation model
        stats.put("conversationModel", "SINGLE_SESSION");
        stats.put("maxConversations", 1);

        return ResponseEntity.ok(stats);
    }

    // ======================== ADMIN INTERVENTION APIs ========================

    /**
     * Admin takeover user's single conversation
     * POST /api/ai-agent/admin/takeover/{userId}
     */
    @PostMapping("/admin/takeover/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChatResponse> takeoverUserConversation(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest request) {

        Long supervisorId = getUserIdFromAuth(authentication);
        validateAdminRole(authentication);

        log.info("Admin {} taking over user {}'s conversation", supervisorId, userId);

        // Lấy conversation duy nhất của user
        String conversationId = getUserConversation(userId);
        if (conversationId == null) {
            return ResponseEntity.badRequest()
                .body(null); // User chưa có conversation để takeover
        }

        ChatResponse result = ephemeralChatService.takeoverEphemeralConversation(conversationId, supervisorId, request);
        return ResponseEntity.ok(result);
    }

    /**
     * Admin send message to user during takeover
     * POST /api/ai-agent/admin/users/{userId}/messages
     */
    @PostMapping("/admin/users/{userId}/messages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChatResponse> sendAdminMessageToUser(
            @PathVariable Long userId,
            @Valid @RequestBody ChatRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long supervisorId = getUserIdFromAuth(authentication);
        validateAdminRole(authentication);

        log.info("Admin {} sending message to user {}", supervisorId, userId);

        // Lấy conversation của user
        String conversationId = getUserConversation(userId);
        if (conversationId == null) {
            return ResponseEntity.badRequest()
                .body(null); // User chưa có conversation
        }

        // Gửi tin nhắn với tư cách admin
        ChatResponse response = ephemeralChatService.sendAdminMessage(conversationId, supervisorId, request, httpRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Admin return conversation back to AI
     * POST /api/ai-agent/admin/return/{userId}
     */
    @PostMapping("/admin/return/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChatResponse> returnConversationToAI(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest request) {

        Long supervisorId = getUserIdFromAuth(authentication);
        validateAdminRole(authentication);

        log.info("Admin {} returning user {}'s conversation to AI", supervisorId, userId);

        // Lấy conversation của user
        String conversationId = getUserConversation(userId);
        if (conversationId == null) {
            return ResponseEntity.badRequest()
                .body(null); // User chưa có conversation
        }

        ChatResponse result = ephemeralChatService.returnConversationToAI(conversationId, supervisorId, request);
        return ResponseEntity.ok(result);
    }

    /**
     * Admin view user's conversation in real-time
     * GET /api/ai-agent/admin/users/{userId}/messages
     */
    @GetMapping("/admin/users/{userId}/messages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ChatResponse>> viewUserConversation(
            @PathVariable Long userId,
            Authentication authentication) {

        validateAdminRole(authentication);

        log.info("Admin viewing user {}'s conversation", userId);

        // Lấy conversation của user
        String conversationId = getUserConversation(userId);
        if (conversationId == null) {
            return ResponseEntity.ok(List.of()); // User chưa có conversation
        }

        List<ChatResponse> messages = ephemeralChatService.getUserSessionMessages(conversationId, userId);
        return ResponseEntity.ok(messages);
    }

    /**
     * Get all active user sessions for admin monitoring
     * GET /api/ai-agent/admin/active-sessions
     */
    @GetMapping("/admin/active-sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getActiveUserSessions(
            Authentication authentication) {

        validateAdminRole(authentication);

        List<Map<String, Object>> activeSessions = ephemeralChatService.getAllActiveUserSessions();
        return ResponseEntity.ok(activeSessions);
    }

    /**
     * Check if user conversation needs intervention
     * GET /api/ai-agent/admin/users/{userId}/status
     */
    @GetMapping("/admin/users/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserConversationStatus(
            @PathVariable Long userId,
            Authentication authentication) {

        validateAdminRole(authentication);

        Map<String, Object> status = ephemeralChatService.getUserConversationStatus(userId);
        return ResponseEntity.ok(status);
    }

    /**
     * ADMIN: Get all conversations of all users
     * GET /api/ai-agent/admin/conversations
     */
    @GetMapping("/admin/conversations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ConversationDto>> getAllConversations(Authentication authentication) {
        validateAdminRole(authentication);
        List<ConversationDto> allConversations = ephemeralChatService.getAllConversations();
        return ResponseEntity.ok(allConversations);
    }

    // ======================== ADMIN APIs (Full Audit Trail) ========================

    /**
     * Get comprehensive audit logs
     */
    @GetMapping("/admin/audit/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ChatMessage>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication) {

        validateAdminRole(authentication);
        Page<ChatMessage> auditLogs = adminDashboardService.getAuditLogs(page, size, userId, sessionId, startDate, endDate);
        return ResponseEntity.ok(auditLogs);
    }

    /**
     * Get enhanced audit statistics với AI insights
     */
    @GetMapping("/admin/audit/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getEnhancedStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication) {

        validateAdminRole(authentication);
        Map<String, Object> statistics = adminDashboardService.getAuditStatistics(startDate, endDate);

        // Add AI system metrics
        Map<String, Object> systemHealth = coreAgentService.getSystemHealth();
        statistics.put("aiSystemHealth", systemHealth);
        statistics.put("conversationModel", "SINGLE_SESSION_PER_USER");

        return ResponseEntity.ok(statistics);
    }

    // ======================== KNOWLEDGE MANAGEMENT APIs ========================




    /**
     * Lấy hoặc tạo conversation duy nhất cho user
     */
    private String getOrCreateUserConversation(Long userId, HttpServletRequest request) {
        // Kiểm tra xem user đã có conversation chưa
        List<ConversationDto> existingConversations = ephemeralChatService.getUserSessionConversations(userId);

        if (!existingConversations.isEmpty()) {
            // Trả về conversation đầu tiên (duy nhất)
            return existingConversations.get(0).getConversationId();
        }

        // Tạo conversation mới nếu chưa có
        ConversationDto newConversation = ephemeralChatService.startEphemeralConversation(
            userId, "AI Chat Session", request);
        return newConversation.getConversationId();
    }

    /**
     * Lấy conversation ID của user (null nếu chưa có)
     */
    private String getUserConversation(Long userId) {
        List<ConversationDto> conversations = ephemeralChatService.getUserSessionConversations(userId);
        return conversations.isEmpty() ? null : conversations.get(0).getConversationId();
    }

    /**
     * ENHANCED: Validate admin role với comprehensive security
     */
    private void validateAdminRole(Authentication authentication) {
        try {
            Long userId = getUserIdFromAuth(authentication);

            // Get user context để check role
            UserContextService.UserChatContext userContext = userContextService.getUserChatContext(userId);

            // Check system role từ database - chỉ có ADMIN, không có SUPER_ADMIN
            SystemRole userRole = SystemRole.valueOf(userContext.getSystemRole());

            if (userRole != SystemRole.ADMIN) {
                log.warn("Unauthorized admin access attempt by user: {} with role: {}", userId, userRole);
                throw new SecurityException("Access denied: Admin privileges required. Current role: " + userRole);
            }

            log.debug("Admin access granted for user: {} with role: {}", userId, userRole);

        } catch (Exception e) {
            log.error("Error validating admin role", e);
            throw new SecurityException("Authentication failed: " + e.getMessage());
        }
    }

    /**
     * NEW: Validate specific permissions for premium features
     */
    private void validatePremiumAccess(Authentication authentication, String feature) {
        Long userId = getUserIdFromAuth(authentication);

        if (!userContextService.hasFeatureAccess(userId, feature)) {
            UserContextService.UserChatContext userContext = userContextService.getUserChatContext(userId);
            log.warn("Premium feature access denied for user: {} (Premium: {})", userId, userContext.getIsPremium());
            throw new SecurityException("Premium subscription required for feature: " + feature);
        }
    }

    /**
     * NEW: Check if user can access another user's data
     */
    private void validateUserAccess(Authentication authentication, Long targetUserId) {
        Long requestingUserId = getUserIdFromAuth(authentication);

        // User can always access their own data
        if (requestingUserId.equals(targetUserId)) {
            return;
        }

        // Admin can access any user's data
        try {
            validateAdminRole(authentication);
            log.debug("Admin {} accessing user {}'s data", requestingUserId, targetUserId);
        } catch (SecurityException e) {
            log.warn("Unauthorized access attempt: user {} trying to access user {}'s data", requestingUserId, targetUserId);
            throw new SecurityException("Access denied: Cannot access other user's data without admin privileges");
        }
    }

    /**
     * NEW: Enhanced getUserIdFromAuth với validation
     */
    private Long getUserIdFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new SecurityException("Authentication required");
        }

        try {
            // Extract user ID from authentication using the correct approach
            if (authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                // Get email from authentication
                String email = userDetails.getUsername();

                // Find user from UserJpaRepository
                Optional<User> userOpt = userRepository.findByEmail(email);
                if (userOpt.isEmpty()) {
                    throw new SecurityException("User not found: " + email);
                }

                User user = userOpt.get();
                if (user.isDeleted()) {
                    throw new SecurityException("User account is deleted: " + email);
                }

                // Update last seen
                userContextService.updateUserLastSeen(user.getId());

                return user.getId();
            } else {
                throw new SecurityException("Invalid authentication principal type");
            }

        } catch (Exception e) {
            log.error("Error extracting user ID from authentication: {}", e.getMessage());
            throw new SecurityException("Authentication failed: " + e.getMessage());
        }
    }

    // ======================== CHAT ANALYSIS APIs (ADMIN ONLY) ========================


    /**
     * Admin view detailed messages of any conversation
     * GET /api/ai-agent/admin/chat/messages/{conversationId}
     */
    @GetMapping("/admin/chat/messages/{conversationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ChatResponse>> getConversationMessages(
            @PathVariable String conversationId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "false") Boolean includeSystemMessages,
            Authentication authentication) {

        validateAdminRole(authentication);

        log.info("Admin viewing detailed messages for conversation: {}", conversationId);

        try {
            // Get all messages from the conversation via repository
            List<com.example.taskmanagement_backend.agent.entity.ChatMessage> chatMessages =
                chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

            if (chatMessages.isEmpty()) {
                log.warn("No messages found for conversation: {}", conversationId);
                return ResponseEntity.ok(List.of());
            }

            // Filter messages based on parameters
            List<ChatResponse> filteredMessages = chatMessages.stream()
                .filter(msg -> !msg.getIsDeleted())
                .filter(msg -> includeSystemMessages || !isSystemMessage(msg))
                .filter(msg -> isWithinDateRange(msg, startDate, endDate))
                .skip((long) page * size)
                .limit(size)
                .map(this::convertToResponse)
                .collect(java.util.stream.Collectors.toList());

            log.info("Retrieved {} messages for conversation: {}", filteredMessages.size(), conversationId);
            return ResponseEntity.ok(filteredMessages);

        } catch (Exception e) {
            log.error("Error retrieving messages for conversation: {}", conversationId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Admin get conversation summary with message statistics
     * GET /api/ai-agent/admin/chat/summary/{conversationId}
     */
    @GetMapping("/admin/chat/summary/{conversationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getConversationSummary(
            @PathVariable String conversationId,
            Authentication authentication) {

        validateAdminRole(authentication);

        log.info("Admin getting conversation summary: {}", conversationId);

        try {
            // Get conversation details
            Optional<com.example.taskmanagement_backend.agent.entity.Conversation> convOpt =
                conversationRepository.findByConversationIdAndIsDeletedFalse(conversationId);

            if (convOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            com.example.taskmanagement_backend.agent.entity.Conversation conv = convOpt.get();

            // Get message statistics
            List<com.example.taskmanagement_backend.agent.entity.ChatMessage> messages =
                chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

            // Count messages by sender type
            Map<String, Long> messageCounts = messages.stream()
                .filter(msg -> !msg.getIsDeleted())
                .collect(java.util.stream.Collectors.groupingBy(
                    msg -> msg.getSenderType().toString(),
                    java.util.stream.Collectors.counting()
                ));

            // Get user information
            UserContextService.UserChatContext userContext = null;
            try {
                userContext = userContextService.getUserChatContext(conv.getUserId());
            } catch (Exception e) {
                log.warn("Could not get user context for user: {}", conv.getUserId());
            }

            // Calculate conversation duration
            LocalDateTime startTime = messages.isEmpty() ? conv.getCreatedAt() : messages.get(0).getCreatedAt();
            LocalDateTime endTime = messages.isEmpty() ? conv.getUpdatedAt() :
                messages.get(messages.size() - 1).getCreatedAt();

            Map<String, Object> summary = new java.util.HashMap<>();
            summary.put("conversationId", conv.getConversationId());
            summary.put("userId", conv.getUserId());
            summary.put("userEmail", userContext != null ? userContext.getEmail() : "unknown@example.com");
            summary.put("title", conv.getTitle());
            summary.put("status", conv.getStatus().toString());
            summary.put("agentActive", conv.getAgentActive());
            summary.put("createdAt", conv.getCreatedAt());
            summary.put("updatedAt", conv.getUpdatedAt());
            summary.put("lastActivity", conv.getLastActivityAt());
            summary.put("totalMessages", messages.size());
            summary.put("messageCounts", messageCounts);
            summary.put("conversationDuration", java.time.Duration.between(startTime, endTime).toMinutes());
            summary.put("averageResponseTime", calculateAverageResponseTime(messages));

            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            log.error("Error getting conversation summary: {}", conversationId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET endpoint - CHỈ lấy phân tích đã tồn tại, không tạo mới
     * GET /api/ai-agent/admin/chat/analysis/{conversationId}
     */
    @GetMapping("/admin/chat/analysis/{conversationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChatAnalysisResponse> getExistingAnalysis(
            @PathVariable String conversationId,
            Authentication authentication) {

        validateAdminRole(authentication);

        log.info("Admin retrieving existing analysis for conversation: {}", conversationId);

        try {
            // Chỉ lấy phân tích đã tồn tại, không tạo mới
            ChatAnalysisResponse existingAnalysis = chatAnalysisService.getExistingAnalysis(conversationId);

            if (existingAnalysis != null) {
                return ResponseEntity.ok(existingAnalysis);
            } else {
                log.info("No existing analysis found for conversation: {}", conversationId);

                // Automatically trigger analysis if no existing analysis found
                log.info("Automatically triggering new analysis for conversation: {}", conversationId);

                // Create a new analysis request with default parameters
                ChatAnalysisRequest request = ChatAnalysisRequest.builder()
                    .conversationId(conversationId)
                    .includeSystemMessages(false)
                    .maxMessages(100)
                    .build();

                // Generate new analysis with default parameters
                try {
                    ChatAnalysisResponse newAnalysis = chatAnalysisService.analyzeSingleConversation(request, false);
                    log.info("Auto-generated analysis for conversation: {} - Category: {}, Confidence: {}",
                            conversationId, newAnalysis.getPrimaryCategory(), newAnalysis.getConfidence());
                    return ResponseEntity.ok(newAnalysis);
                } catch (Exception analysisError) {
                    log.error("Failed to auto-generate analysis for conversation: {}", conversationId, analysisError);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(null);
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving analysis for conversation: {}", conversationId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * PUT endpoint - Tạo mới hoặc cập nhật phân tích hội thoại
     * PUT /api/ai-agent/admin/chat/analysis/{conversationId}
     */
    @PutMapping("/admin/chat/analysis/{conversationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChatAnalysisResponse> createOrUpdateAnalysis(
            @PathVariable String conversationId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "false") Boolean includeSystemMessages,
            @RequestParam(required = false, defaultValue = "100") Integer maxMessages,
            Authentication authentication) {

        validateAdminRole(authentication);

        log.info("Admin creating/updating analysis for conversation: {}", conversationId);

        try {
            // Luôn tạo mới phân tích (bỏ qua phân tích cũ) bằng cách đặt forceRefresh=true
            ChatAnalysisRequest request = ChatAnalysisRequest.builder()
                .conversationId(conversationId)
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .includeSystemMessages(includeSystemMessages)
                .maxMessages(maxMessages)
                .build();

            // Sử dụng tham số forceRefresh=true để đảm bảo luôn tạo phân tích mới
            ChatAnalysisResponse analysis = chatAnalysisService.analyzeSingleConversation(request, true);

            log.info("Analysis created for conversation: {} - Category: {}, Confidence: {}",
                    conversationId, analysis.getPrimaryCategory(), analysis.getConfidence());

            return ResponseEntity.ok(analysis);

        } catch (IllegalArgumentException e) {
            log.error("Conversation not found: {}", conversationId, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error analyzing conversation: {}", conversationId, e);
            return ResponseEntity.internalServerError().build();
        }
    }



    // ======================== HELPER METHODS FOR MESSAGE HANDLING ========================

    /**
     * Convert ChatMessage entity to ChatResponse DTO
     */
    private ChatResponse convertToResponse(com.example.taskmanagement_backend.agent.entity.ChatMessage message) {
        return ChatResponse.builder()
            .messageId(message.getMessageId())
            .content(message.getContent())
            .senderType(message.getSenderType().toString())
            .timestamp(message.getCreatedAt())
            .conversationId(message.getConversation().getConversationId())
            .success(true)
            .build();
    }

    /**
     * Check if message is within date range
     */
    private boolean isWithinDateRange(com.example.taskmanagement_backend.agent.entity.ChatMessage message,
                                    LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && message.getCreatedAt().isBefore(startDate)) {
            return false;
        }
        if (endDate != null && message.getCreatedAt().isAfter(endDate)) {
            return false;
        }
        return true;
    }

    /**
     * Check if message is system message
     */
    private boolean isSystemMessage(com.example.taskmanagement_backend.agent.entity.ChatMessage message) {
        return message.getSenderType().toString().contains("SYSTEM");
    }

    /**
     * Calculate average response time between messages
     */
    private double calculateAverageResponseTime(List<com.example.taskmanagement_backend.agent.entity.ChatMessage> messages) {
        if (messages.size() < 2) {
            return 0.0;
        }

        long totalMinutes = 0;
        int responseCount = 0;

        for (int i = 1; i < messages.size(); i++) {
            com.example.taskmanagement_backend.agent.entity.ChatMessage prev = messages.get(i - 1);
            com.example.taskmanagement_backend.agent.entity.ChatMessage curr = messages.get(i);

            // Calculate response time between different senders
            if (!prev.getSenderType().equals(curr.getSenderType())) {
                totalMinutes += java.time.Duration.between(prev.getCreatedAt(), curr.getCreatedAt()).toMinutes();
                responseCount++;
            }
        }

        return responseCount > 0 ? (double) totalMinutes / responseCount : 0.0;
    }
}
