package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.dto.ChatRequest;
import com.example.taskmanagement_backend.agent.dto.ChatResponse;
import com.example.taskmanagement_backend.agent.entity.ChatMessage;
import com.example.taskmanagement_backend.agent.entity.Conversation;
import com.example.taskmanagement_backend.agent.enums.SenderType;
import com.example.taskmanagement_backend.agent.memory.ChatMessageRepository;
import com.example.taskmanagement_backend.agent.memory.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent Audit Log Service - Persistent logging for AI chat monitoring
 * All messages are logged to DB for compliance, analytics, and monitoring
 */
@Slf4j
@Service("agentAuditLogService")
@RequiredArgsConstructor
@Transactional
public class AuditLogService {

    private final ChatMessageRepository chatMessageRepository;
    @Qualifier("agentConversationRepository")
    private final ConversationRepository conversationRepository;

    /**
     * Log conversation creation for audit
     */
    public void logConversationCreated(Long userId, String sessionConversationId, String sessionId) {
        try {
            Conversation auditConversation = Conversation.builder()
                .conversationId("audit_" + UUID.randomUUID().toString())
                .userId(userId)
                .title("AI Chat Audit Log")
                .status(Conversation.ConversationStatus.ACTIVE)
                .agentActive(true)
                .language("vi")
                .messageCount(0)
                .category("EPHEMERAL_CHAT")
                .tags("{\"session_id\":\"" + sessionId + "\",\"session_conversation_id\":\"" + sessionConversationId + "\"}")
                .lastActivityAt(LocalDateTime.now())
                .build();

            conversationRepository.save(auditConversation);

            log.info("Audit: Conversation created for user: {} with session: {}", userId, sessionId);

        } catch (Exception e) {
            log.error("Failed to log conversation creation for audit", e);
        }
    }

    /**
     * Log message for audit (all messages are permanently stored)
     */
    public void logMessage(Long userId, String sessionConversationId, ChatRequest request,
                          ChatResponse response, String sessionId, String userAgent, String clientIp) {
        try {
            // Find or create audit conversation
            Conversation auditConversation = findOrCreateAuditConversation(userId, sessionConversationId, sessionId);

            // Log user message
            ChatMessage userMessage = ChatMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .conversation(auditConversation)
                .userId(userId)
                .senderType(SenderType.USER)
                .content(request.getContent())
                .sessionId(sessionId)
                .language(request.getLanguage() != null ? request.getLanguage() : "vi")
                .tags("{\"user_agent\":\"" + userAgent + "\",\"client_ip\":\"" + clientIp + "\"}")
                .processedAt(LocalDateTime.now())
                .build();

            chatMessageRepository.save(userMessage);

            // Log AI response if exists
            if (response != null && response.getContent() != null) {
                ChatMessage aiMessage = ChatMessage.builder()
                    .messageId(response.getMessageId() != null ? response.getMessageId() : UUID.randomUUID().toString())
                    .conversation(auditConversation)
                    .senderType(SenderType.AGENT)
                    .content(response.getContent())
                    .aiModel(response.getAiModel())
                    .confidence(response.getConfidence())
                    .intent(response.getIntent())
                    .ragContext(response.getContext())
                    .sessionId(sessionId)
                    .language("vi")
                    .processedAt(LocalDateTime.now())
                    .build();

                chatMessageRepository.save(aiMessage);
            }

            // Update conversation stats
            auditConversation.setMessageCount(auditConversation.getMessageCount() + (response != null ? 2 : 1));
            auditConversation.setLastActivityAt(LocalDateTime.now());
            conversationRepository.save(auditConversation);

            log.debug("Audit: Logged message exchange for user: {} in session: {}", userId, sessionId);

        } catch (Exception e) {
            log.error("Failed to log message for audit", e);
        }
    }

    /**
     * Log takeover event for audit
     */
    public void logTakeoverEvent(String sessionConversationId, Long supervisorId, String action) {
        try {
            // Find audit conversation by session conversation ID
            String tags = "%\"session_conversation_id\":\"" + sessionConversationId + "\"%";
            Page<Conversation> conversations = conversationRepository.findByTagsContaining(tags, PageRequest.of(0, 1));

            if (!conversations.isEmpty()) {
                Conversation auditConversation = conversations.getContent().get(0);

                ChatMessage systemMessage = ChatMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .conversation(auditConversation)
                    .userId(supervisorId)
                    .senderType(SenderType.SUPERVISOR)
                    .content("SYSTEM: " + action)
                    .tags("{\"event_type\":\"takeover\",\"action\":\"" + action + "\"}")
                    .processedAt(LocalDateTime.now())
                    .build();

                chatMessageRepository.save(systemMessage);

                // Update conversation status
                if ("takeover".equals(action)) {
                    auditConversation.setStatus(Conversation.ConversationStatus.TAKEOVER);
                    auditConversation.setSupervisorId(supervisorId);
                    auditConversation.setTakenOverAt(LocalDateTime.now());
                } else if ("return_to_agent".equals(action)) {
                    auditConversation.setStatus(Conversation.ConversationStatus.ACTIVE);
                    auditConversation.setSupervisorId(null);
                    auditConversation.setTakenOverAt(null);
                }

                auditConversation.setLastActivityAt(LocalDateTime.now());
                conversationRepository.save(auditConversation);

                log.info("Audit: Logged takeover event: {} by supervisor: {}", action, supervisorId);
            }

        } catch (Exception e) {
            log.error("Failed to log takeover event for audit", e);
        }
    }

    /**
     * Log session end for audit
     */
    public void logSessionEnd(Long userId, String sessionId, String reason) {
        try {
            String tags = "%\"session_id\":\"" + sessionId + "\"%";
            Page<Conversation> conversations = conversationRepository.findByUserIdAndTagsContaining(
                userId, tags, PageRequest.of(0, 10));

            for (Conversation conversation : conversations.getContent()) {
                ChatMessage endMessage = ChatMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .conversation(conversation)
                    .userId(userId)
                    .senderType(SenderType.USER)
                    .content("SYSTEM: Session ended - " + reason)
                    .tags("{\"event_type\":\"session_end\",\"reason\":\"" + reason + "\"}")
                    .processedAt(LocalDateTime.now())
                    .build();

                chatMessageRepository.save(endMessage);

                // Close conversation
                conversation.setStatus(Conversation.ConversationStatus.CLOSED);
                conversation.setLastActivityAt(LocalDateTime.now());
                conversationRepository.save(conversation);
            }

            log.info("Audit: Logged session end for user: {} with reason: {}", userId, reason);

        } catch (Exception e) {
            log.error("Failed to log session end for audit", e);
        }
    }

    /**
     * Get audit logs for admin dashboard
     */
    public Page<ChatMessage> getAuditLogs(int page, int size, Long userId, String sessionId,
                                         LocalDateTime startDate, LocalDateTime endDate) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (userId != null && sessionId != null) {
            return chatMessageRepository.findByUserIdAndSessionIdAndCreatedAtBetween(
                userId, sessionId, startDate, endDate, pageable);
        } else if (userId != null) {
            return chatMessageRepository.findByUserIdAndCreatedAtBetween(
                userId, startDate, endDate, pageable);
        } else {
            return chatMessageRepository.findByCreatedAtBetween(startDate, endDate, pageable);
        }
    }

    /**
     * Get audit statistics for admin
     */
    public Map<String, Object> getAuditStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> stats = new HashMap<>();

        try {
            Long totalMessages = chatMessageRepository.countByCreatedAtBetween(startDate, endDate);
            Long totalUsers = chatMessageRepository.countDistinctUsersByCreatedAtBetween(startDate, endDate);
            Long totalConversations = conversationRepository.countByCreatedAtBetween(startDate, endDate);
            Long takeoverEvents = chatMessageRepository.countByContentContainingAndCreatedAtBetween(
                "SYSTEM: takeover", startDate, endDate);

            stats.put("totalMessages", totalMessages);
            stats.put("totalUsers", totalUsers);
            stats.put("totalConversations", totalConversations);
            stats.put("takeoverEvents", takeoverEvents);
            stats.put("periodStart", startDate);
            stats.put("periodEnd", endDate);

        } catch (Exception e) {
            log.error("Failed to get audit statistics", e);
            stats.put("error", "Failed to retrieve statistics");
        }

        return stats;
    }

    /**
     * Search audit logs
     */
    public Page<ChatMessage> searchAuditLogs(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return chatMessageRepository.findByContentContainingIgnoreCase(query, pageable);
    }

    /**
     * Find or create audit conversation for session
     */
    private Conversation findOrCreateAuditConversation(Long userId, String sessionConversationId, String sessionId) {
        String tags = "%\"session_conversation_id\":\"" + sessionConversationId + "\"%";
        Page<Conversation> existing = conversationRepository.findByTagsContaining(tags, PageRequest.of(0, 1));

        if (!existing.isEmpty()) {
            return existing.getContent().get(0);
        }

        // Create new audit conversation
        Conversation auditConversation = Conversation.builder()
            .conversationId("audit_" + UUID.randomUUID().toString())
            .userId(userId)
            .title("AI Chat Audit Log")
            .status(Conversation.ConversationStatus.ACTIVE)
            .agentActive(true)
            .language("vi")
            .messageCount(0)
            .category("EPHEMERAL_CHAT")
            .tags("{\"session_id\":\"" + sessionId + "\",\"session_conversation_id\":\"" + sessionConversationId + "\"}")
            .lastActivityAt(LocalDateTime.now())
            .build();

        return conversationRepository.save(auditConversation);
    }
}
