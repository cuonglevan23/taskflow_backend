package com.example.taskmanagement_backend.agent.exception;

/**
 * Agent Exception - Xử lý lỗi AI/middleware
 */
public class AgentException extends RuntimeException {

    private final String errorCode;
    private final String category;

    public AgentException(String message) {
        super(message);
        this.errorCode = "AGENT_ERROR";
        this.category = "GENERAL";
    }

    public AgentException(String message, String errorCode, String category) {
        super(message);
        this.errorCode = errorCode;
        this.category = category;
    }

    public AgentException(String message, Throwable cause, String errorCode, String category) {
        super(message, cause);
        this.errorCode = errorCode;
        this.category = category;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getCategory() {
        return category;
    }

    // Predefined exceptions
    public static class ModerationException extends AgentException {
        public ModerationException(String message) {
            super(message, "MODERATION_FAILED", "CONTENT_FILTER");
        }
    }

    public static class RAGException extends AgentException {
        public RAGException(String message) {
            super(message, "RAG_FAILED", "RETRIEVAL");
        }
    }

    public static class GeminiException extends AgentException {
        public GeminiException(String message) {
            super(message, "GEMINI_API_FAILED", "AI_SERVICE");
        }
    }

    public static class ConversationNotFoundException extends AgentException {
        public ConversationNotFoundException(String conversationId) {
            super("Conversation not found: " + conversationId, "CONVERSATION_NOT_FOUND", "VALIDATION");
        }
    }
}
