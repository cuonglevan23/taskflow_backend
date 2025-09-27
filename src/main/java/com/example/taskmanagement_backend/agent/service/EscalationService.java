package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.dto.ChatRequest;
import com.example.taskmanagement_backend.agent.dto.ChatResponse;
import com.example.taskmanagement_backend.agent.exception.AgentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Escalation Service - Error handling, fallback strategies, retry logic
 * Xử lý lỗi, retry logic, fallback responses
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationService {

    @Qualifier("agentAuditLogService")
    private final AuditLogService auditLogService;

    /**
     * Execute với retry logic và exponential backoff
     */
    public <T> T executeWithRetry(Supplier<T> operation, String operationName, int maxRetries) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                log.debug("Executing operation: {} (attempt {}/{})", operationName, attempt + 1, maxRetries);
                return operation.get();

            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt >= maxRetries) {
                    log.error("Operation {} failed after {} attempts", operationName, maxRetries, e);
                    break;
                }

                long delayMs = calculateBackoffDelay(attempt);
                log.warn("Operation {} failed (attempt {}), retrying in {}ms", operationName, attempt, delayMs, e);

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // Log failure for admin monitoring
        logEscalationEvent(operationName, lastException, attempt);
        throw new AgentException("Operation failed after " + maxRetries + " attempts: " + operationName,
            "RETRY_EXHAUSTED", "SYSTEM_ERROR");
    }

    /**
     * Execute với timeout handling
     */
    public <T> T executeWithTimeout(Supplier<T> operation, String operationName, long timeoutSeconds) {
        try {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(operation);
            return future.get(timeoutSeconds, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Operation {} timed out after {} seconds", operationName, timeoutSeconds, e);
            logEscalationEvent(operationName, e, 1);
            throw new AgentException("Operation timed out: " + operationName, "TIMEOUT", "SYSTEM_ERROR");
        }
    }

    /**
     * Handle Gemini API errors với specific fallbacks
     */
    public ChatResponse handleGeminiError(Exception error, String userMessage, String conversationId) {
        log.error("Gemini API error occurred", error);

        String fallbackContent;
        String errorCode;

        if (error.getMessage().contains("quota") || error.getMessage().contains("limit")) {
            errorCode = "QUOTA_EXCEEDED";
            fallbackContent = "Xin lỗi, hệ thống đang quá tải. Vui lòng thử lại sau ít phút.";

        } else if (error.getMessage().contains("timeout")) {
            errorCode = "API_TIMEOUT";
            fallbackContent = "Kết nối đang chậm, vui lòng thử lại.";

        } else if (error.getMessage().contains("unauthorized") || error.getMessage().contains("api key")) {
            errorCode = "AUTH_ERROR";
            fallbackContent = "Hệ thống đang bảo trì, vui lòng thử lại sau.";

        } else {
            errorCode = "UNKNOWN_ERROR";
            fallbackContent = generateContextualFallback(userMessage);
        }

        // Log escalation for admin
        logEscalationEvent("GEMINI_API_ERROR", error, 1);

        return ChatResponse.builder()
            .messageId("fallback_" + System.currentTimeMillis())
            .content(fallbackContent)
            .senderType("SYSTEM")
            .timestamp(LocalDateTime.now())
            .success(false)
            .status("FALLBACK_RESPONSE")
            .errorMessage(errorCode)
            .conversationId(conversationId)
            .build();
    }

    /**
     * Handle insufficient data scenarios
     */
    public ChatResponse handleInsufficientData(String userMessage, String conversationId, String reason) {
        log.info("Handling insufficient data: reason={}, message={}", reason, userMessage);

        String fallbackContent = generateInsufficientDataResponse(userMessage, reason);

        // Log for admin analysis
        logDataQualityIssue(userMessage, reason, conversationId);

        return ChatResponse.builder()
            .messageId("insufficient_data_" + System.currentTimeMillis())
            .content(fallbackContent)
            .senderType("AGENT")
            .timestamp(LocalDateTime.now())
            .success(true)
            .status("INSUFFICIENT_DATA")
            .conversationId(conversationId)
            .intent("clarification_needed")
            .build();
    }

    /**
     * Handle context not found scenarios
     */
    public ChatResponse handleContextNotFound(String userMessage, String conversationId) {
        log.info("Context not found for message: {}", userMessage);

        String fallbackContent = generateContextNotFoundResponse(userMessage);

        // Log for admin - might need knowledge base expansion
        logContextGap(userMessage, conversationId);

        return ChatResponse.builder()
            .messageId("no_context_" + System.currentTimeMillis())
            .content(fallbackContent)
            .senderType("AGENT")
            .timestamp(LocalDateTime.now())
            .success(true)
            .status("NO_CONTEXT_FOUND")
            .conversationId(conversationId)
            .intent("general_response")
            .build();
    }

    /**
     * Handle moderation failures
     */
    public ChatResponse handleModerationFailure(String userMessage, String conversationId, String reason) {
        log.warn("Moderation failure: reason={}, message={}", reason, userMessage);

        // Always err on the side of caution
        String safeResponse = "Xin lỗi, tôi không thể xử lý tin nhắn này. Vui lòng diễn đạt lại một cách khác.";

        // Log for admin review
        logModerationFailure(userMessage, reason, conversationId);

        return ChatResponse.builder()
            .messageId("moderation_block_" + System.currentTimeMillis())
            .content(safeResponse)
            .senderType("SYSTEM")
            .timestamp(LocalDateTime.now())
            .success(false)
            .status("MODERATION_BLOCKED")
            .errorMessage(reason)
            .conversationId(conversationId)
            .build();
    }

    /**
     * Escalate to human supervisor
     */
    public void escalateToHuman(String conversationId, String reason, String userMessage) {
        log.info("Escalating conversation {} to human: {}", conversationId, reason);

        // In a real implementation, this would:
        // 1. Notify available supervisors
        // 2. Add conversation to supervisor queue
        // 3. Set conversation status to PENDING_HUMAN

        // For now, just log the escalation
        logHumanEscalation(conversationId, reason, userMessage);
    }

    /**
     * Escalate to admin when AI response quality is insufficient
     * This method flags conversations that need admin intervention
     */
    public void escalateToAdmin(ChatResponse aiResponse, ChatRequest request, Long userId, String conversationId, Long projectId) {
        try {
            log.warn("Escalating to admin: conversationId={}, userId={}, projectId={}, quality={}",
                conversationId, userId, projectId, aiResponse.getQualityAssessment());

            // Set escalation flags in the response
            aiResponse.setAdminEscalated(true);
            aiResponse.setEscalationReason("Low quality AI response requires admin review");
            aiResponse.setSupervisorId("PENDING_ASSIGNMENT");

            // Log detailed escalation event for admin dashboard
            logAdminEscalation(aiResponse, request, userId, conversationId, projectId);

            // In a real implementation, this would:
            // 1. Notify available admins/supervisors
            // 2. Add conversation to admin review queue
            // 3. Set conversation status to PENDING_ADMIN_REVIEW
            // 4. Send alert to admin dashboard
            // 5. Optionally pause AI responses until admin reviews

            log.info("Admin escalation completed for conversation: {}", conversationId);

        } catch (Exception e) {
            log.error("Failed to escalate to admin for conversation: {}", conversationId, e);
        }
    }

    /**
     * Calculate exponential backoff delay
     */
    private long calculateBackoffDelay(int attempt) {
        // Exponential backoff: 1s, 2s, 4s, 8s, max 30s
        long baseDelay = 1000; // 1 second
        long maxDelay = 30000;  // 30 seconds

        long delay = baseDelay * (1L << (attempt - 1));
        return Math.min(delay, maxDelay);
    }

    /**
     * Generate contextual fallback based on user message
     */
    private String generateContextualFallback(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();

        if (lowerMessage.contains("task") || lowerMessage.contains("công việc")) {
            return "Tôi hiểu bạn đang hỏi về quản lý task. Mặc dù tôi đang gặp sự cố nhỏ, " +
                   "bạn có thể thử hỏi cụ thể hơn về tạo task, phân công, hoặc theo dõi tiến độ.";
        }

        if (lowerMessage.contains("project") || lowerMessage.contains("dự án")) {
            return "Về quản lý dự án, tôi có thể hỗ trợ bạn sau khi khắc phục sự cố. " +
                   "Trong lúc đó, bạn có thể xem tài liệu hướng dẫn hoặc liên hệ support team.";
        }

        return "Xin lỗi, tôi đang gặp sự cố kỹ thuật tạm thời. " +
               "Vui lòng thử lại sau ít phút hoặc diễn đạt câu hỏi khác cách.";
    }

    /**
     * Generate response for insufficient data
     */
    private String generateInsufficientDataResponse(String userMessage, String reason) {
        if ("TOO_SHORT".equals(reason)) {
            return "Tin nhắn của bạn hơi ngắn. Bạn có thể mô tả chi tiết hơn về vấn đề cần hỗ trợ không?";
        }

        if ("UNCLEAR_INTENT".equals(reason)) {
            return "Tôi chưa hiểu rõ ý của bạn. Bạn có thể giải thích cụ thể hơn hoặc đưa ra ví dụ không?";
        }

        if ("MISSING_CONTEXT".equals(reason)) {
            return "Để hỗ trợ tốt hơn, bạn có thể cung cấp thêm thông tin về: " +
                   "dự án đang làm, vấn đề gặp phải, hoặc mục tiêu muốn đạt được?";
        }

        return "Để tôi hỗ trợ hiệu quả, bạn có thể mô tả rõ hơn về vấn đề cần giải quyết không?";
    }

    /**
     * Generate response when context not found
     */
    private String generateContextNotFoundResponse(String userMessage) {
        return "Tôi chưa có đủ thông tin để trả lời câu hỏi này một cách chính xác. " +
               "Bạn có thể thử hỏi về các chủ đề khác như quản lý task, dự án, hoặc làm việc nhóm? " +
               "Hoặc liên hệ support team để được hỗ trợ chi tiết hơn.";
    }

    // Logging methods for admin monitoring
    private void logEscalationEvent(String operationName, Exception error, int attempts) {
        try {
            // Log structured data for admin dashboard
            log.error("ESCALATION_EVENT: operation={}, attempts={}, error={}",
                operationName, attempts, error.getMessage());

            // In a real implementation, this would store in database for admin analysis

        } catch (Exception e) {
            log.error("Failed to log escalation event", e);
        }
    }

    private void logDataQualityIssue(String userMessage, String reason, String conversationId) {
        try {
            log.info("DATA_QUALITY_ISSUE: conversationId={}, reason={}, messageLength={}",
                conversationId, reason, userMessage.length());

        } catch (Exception e) {
            log.error("Failed to log data quality issue", e);
        }
    }

    private void logContextGap(String userMessage, String conversationId) {
        try {
            log.info("CONTEXT_GAP: conversationId={}, message={}",
                conversationId, userMessage.substring(0, Math.min(100, userMessage.length())));

        } catch (Exception e) {
            log.error("Failed to log context gap", e);
        }
    }

    private void logModerationFailure(String userMessage, String reason, String conversationId) {
        try {
            log.warn("MODERATION_FAILURE: conversationId={}, reason={}", conversationId, reason);

        } catch (Exception e) {
            log.error("Failed to log moderation failure", e);
        }
    }

    private void logHumanEscalation(String conversationId, String reason, String userMessage) {
        try {
            log.info("HUMAN_ESCALATION: conversationId={}, reason={}", conversationId, reason);

        } catch (Exception e) {
            log.error("Failed to log human escalation", e);
        }
    }

    private void logAdminEscalation(ChatResponse aiResponse, ChatRequest request, Long userId, String conversationId, Long projectId) {
        try {
            log.info("ADMIN_ESCALATION: conversationId={}, userId={}, projectId={}, quality={}",
                conversationId, userId, projectId, aiResponse.getQualityAssessment());

            // Log request and response details
            log.info("Request: {}", request);
            log.info("AI Response: {}", aiResponse);

        } catch (Exception e) {
            log.error("Failed to log admin escalation", e);
        }
    }
}
