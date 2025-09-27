package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.dto.ChatResponse;
import com.example.taskmanagement_backend.agent.dto.ConversationDto;
import com.example.taskmanagement_backend.agent.entity.ChatMessage;
import com.example.taskmanagement_backend.agent.enums.SenderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced Session Memory Service - Ephemeral chat with Vector Database Integration
 * ENHANCED: Now stores messages in both Redis (short-term) and Pinecone (long-term)
 * Messages exist during session in Redis + permanent searchable memory in Pinecone
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMemoryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RAGService ragService; // NEW: For vector database storage
    private final EmbeddingService embeddingService; // NEW: For generating embeddings

    private static final String SESSION_PREFIX = "chat_session:";
    private static final String CONVERSATION_PREFIX = "conversation:";
    private static final String MEMORY_PREFIX = "memory:"; // NEW: For conversation memory tracking
    private static final int SESSION_TIMEOUT_HOURS = 24; // 24 hours session timeout
    private static final int SHORT_TERM_MEMORY_LIMIT = 10; // Last 10 messages in Redis

    /**
     * Create ephemeral conversation for user session
     */
    public ConversationDto createSessionConversation(Long userId, String sessionId) {
        String conversationId = generateConversationId(userId, sessionId);

        ConversationDto conversation = ConversationDto.builder()
            .conversationId(conversationId)
            .userId(userId)
            .title("AI Chat Session")
            .status("ACTIVE")
            .agentActive(true)
            .language("vi")
            .messageCount(0)
            .createdAt(LocalDateTime.now())
            .lastActivity(LocalDateTime.now())
            .build();

        // Store in Redis with session timeout
        String key = SESSION_PREFIX + conversationId;
        redisTemplate.opsForValue().set(key, conversation, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);

        // Track user's active conversations
        String userSessionsKey = SESSION_PREFIX + "user:" + userId;
        redisTemplate.opsForSet().add(userSessionsKey, conversationId);
        redisTemplate.expire(userSessionsKey, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);

        log.info("Created ephemeral conversation: {} for user: {}", conversationId, userId);
        return conversation;
    }

    /**
     * ENHANCED: Store message in both Redis (ephemeral) and Pinecone (permanent)
     */
    public void storeSessionMessage(String conversationId, ChatResponse message) {
        // Store in Redis for short-term access (existing functionality)
        storeInRedis(conversationId, message);

        // NEW: Store in Pinecone for long-term searchable memory
        storeInVectorDatabase(conversationId, message);

        // Update conversation activity
        updateConversationActivity(conversationId);

        log.debug("Stored message in both Redis and Vector DB for conversation: {}", conversationId);
    }

    /**
     * Store message in Redis (short-term memory)
     */
    private void storeInRedis(String conversationId, ChatResponse message) {
        String messagesKey = SESSION_PREFIX + CONVERSATION_PREFIX + conversationId + ":messages";

        // Add message to list
        redisTemplate.opsForList().rightPush(messagesKey, message);

        // Keep only last N messages in Redis for performance
        Long messageCount = redisTemplate.opsForList().size(messagesKey);
        if (messageCount != null && messageCount > SHORT_TERM_MEMORY_LIMIT) {
            redisTemplate.opsForList().leftPop(messagesKey);
        }

        // Set expiration
        redisTemplate.expire(messagesKey, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
    }

    /**
     * NEW: Store message in Pinecone vector database for long-term memory
     */
    private void storeInVectorDatabase(String conversationId, ChatResponse message) {
        try {
            // Create unique ID for this message
            String messageId = "msg_" + conversationId + "_" + System.currentTimeMillis() + "_" + message.getSenderType();

            // Prepare content for embedding
            String messageContent = message.getContent();
            if (messageContent.length() > 1000) {
                messageContent = messageContent.substring(0, 1000) + "...";
            }

            // Create comprehensive metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("conversation_id", conversationId);
            metadata.put("sender_type", message.getSenderType());
            metadata.put("timestamp", message.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            metadata.put("message_id", message.getMessageId());
            metadata.put("intent", message.getIntent());
            metadata.put("ai_model", message.getAiModel());
            metadata.put("confidence", message.getConfidence());
            metadata.put("tool_called", message.isToolCalled());
            metadata.put("content_length", message.getContent().length());
            metadata.put("category", "conversation_message");
            metadata.put("user_id", extractUserIdFromConversation(conversationId));

            // Store in RAG service (which handles Pinecone)
            ragService.storeKnowledge(messageId,
                "Conversation Message - " + message.getSenderType(),
                messageContent,
                metadata);

            log.debug("✅ Stored message {} in vector database", messageId);

        } catch (Exception e) {
            log.warn("⚠️ Failed to store message in vector database: {}", e.getMessage());
        }
    }

    /**
     * Get session messages (only visible to user during session)
     */
    public List<ChatResponse> getSessionMessages(String conversationId, Long userId) {
        // Verify user owns this conversation
        if (!isUserConversation(conversationId, userId)) {
            throw new RuntimeException("Access denied to conversation");
        }

        // First, try Redis (short-term memory)
        List<ChatResponse> redisMessages = getMessagesFromRedis(conversationId);

        // If Redis is empty or has few messages, supplement with vector database
        if (redisMessages.size() < 5) {
            List<ChatResponse> vectorMessages = getMessagesFromVectorDatabase(conversationId, userId);

            // Merge and deduplicate
            Set<String> messageIds = redisMessages.stream()
                .map(ChatResponse::getMessageId)
                .collect(HashSet::new, Set::add, Set::addAll);

            vectorMessages.stream()
                .filter(msg -> !messageIds.contains(msg.getMessageId()))
                .forEach(redisMessages::add);

            // Sort by timestamp
            redisMessages.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        }

        return redisMessages;
    }

    /**
     * Get messages from Redis
     */
    private List<ChatResponse> getMessagesFromRedis(String conversationId) {
        String messagesKey = SESSION_PREFIX + CONVERSATION_PREFIX + conversationId + ":messages";
        List<Object> messages = redisTemplate.opsForList().range(messagesKey, 0, -1);

        if (messages == null) {
            return new ArrayList<>();
        }

        return messages.stream()
            .filter(ChatResponse.class::isInstance)
            .map(ChatResponse.class::cast)
            .toList();
    }

    /**
     * NEW: Get messages from vector database for conversation context
     */
    private List<ChatResponse> getMessagesFromVectorDatabase(String conversationId, Long userId) {
        try {
            // Search for conversation messages in vector database
            String searchQuery = "conversation_id:" + conversationId + " user messages";
            RAGService.RAGContext ragContext = ragService.retrieveContext(searchQuery, conversationId, userId);

            List<ChatResponse> vectorMessages = new ArrayList<>();

            for (RAGService.KnowledgeDocument doc : ragContext.getRelevantDocuments()) {
                try {
                    ChatResponse message = reconstructMessageFromDocument(doc);
                    if (message != null) {
                        vectorMessages.add(message);
                    }
                } catch (Exception e) {
                    log.debug("Could not reconstruct message from document: {}", doc.getId());
                }
            }

            log.debug("Retrieved {} messages from vector database for conversation {}",
                vectorMessages.size(), conversationId);

            return vectorMessages;

        } catch (Exception e) {
            log.warn("Failed to retrieve messages from vector database: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * NEW: Reconstruct ChatResponse from Pinecone document
     */
    private ChatResponse reconstructMessageFromDocument(RAGService.KnowledgeDocument doc) {
        try {
            Map<String, Object> metadata = doc.getMetadata();

            return ChatResponse.builder()
                .messageId((String) metadata.get("message_id"))
                .content(doc.getContent())
                .senderType((String) metadata.get("sender_type"))
                .timestamp(LocalDateTime.parse((String) metadata.get("timestamp"),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .aiModel((String) metadata.get("ai_model"))
                .confidence(((Number) metadata.getOrDefault("confidence", 0.0)).doubleValue())
                .intent((String) metadata.get("intent"))
                .toolCalled((Boolean) metadata.getOrDefault("tool_called", false))
                .success(true)
                .conversationId((String) metadata.get("conversation_id"))
                .build();

        } catch (Exception e) {
            log.debug("Failed to reconstruct message: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Clear user session on logout
     */
    public void clearUserSession(Long userId, String sessionId) {
        String userSessionsKey = SESSION_PREFIX + "user:" + userId;
        Set<Object> conversationIds = redisTemplate.opsForSet().members(userSessionsKey);

        if (conversationIds != null) {
            for (Object convId : conversationIds) {
                String conversationKey = SESSION_PREFIX + convId.toString();
                String messagesKey = SESSION_PREFIX + CONVERSATION_PREFIX + convId.toString() + ":messages";

                // Delete conversation and messages
                redisTemplate.delete(conversationKey);
                redisTemplate.delete(messagesKey);
            }
        }

        // Delete user sessions tracking
        redisTemplate.delete(userSessionsKey);

        log.info("Cleared session data for user: {} on logout", userId);
    }

    /**
     * Extend session timeout on user activity
     */
    public void extendSessionTimeout(String conversationId, Long userId) {
        if (!isUserConversation(conversationId, userId)) {
            return;
        }

        String conversationKey = SESSION_PREFIX + conversationId;
        String messagesKey = SESSION_PREFIX + CONVERSATION_PREFIX + conversationId + ":messages";
        String userSessionsKey = SESSION_PREFIX + "user:" + userId;

        // Extend timeout for all related keys
        redisTemplate.expire(conversationKey, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        redisTemplate.expire(messagesKey, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        redisTemplate.expire(userSessionsKey, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
    }

    /**
     * Check if conversation belongs to user
     */
    private boolean isUserConversation(String conversationId, Long userId) {
        String userSessionsKey = SESSION_PREFIX + "user:" + userId;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(userSessionsKey, conversationId));
    }

    /**
     * Update conversation last activity
     */
    private void updateConversationActivity(String conversationId) {
        String key = SESSION_PREFIX + conversationId;
        Object conversation = redisTemplate.opsForValue().get(key);

        if (conversation instanceof ConversationDto conv) {
            conv.setLastActivity(LocalDateTime.now());
            conv.setMessageCount(conv.getMessageCount() + 1);
            redisTemplate.opsForValue().set(key, conv, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        }
    }

    /**
     * Generate unique conversation ID for session
     */
    private String generateConversationId(Long userId, String sessionId) {
        return "session_" + userId + "_" + System.currentTimeMillis() + "_" +
               sessionId.substring(0, Math.min(8, sessionId.length()));
    }

    /**
     * Get session statistics
     */
    public Map<String, Object> getSessionStats(Long userId) {
        String userSessionsKey = SESSION_PREFIX + "user:" + userId;
        Set<Object> conversationIds = redisTemplate.opsForSet().members(userSessionsKey);

        int totalConversations = conversationIds != null ? conversationIds.size() : 0;
        int totalMessages = 0;

        if (conversationIds != null) {
            for (Object convId : conversationIds) {
                String messagesKey = SESSION_PREFIX + CONVERSATION_PREFIX + convId.toString() + ":messages";
                Long messageCount = redisTemplate.opsForList().size(messagesKey);
                totalMessages += messageCount != null ? messageCount.intValue() : 0;
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("activeConversations", totalConversations);
        stats.put("totalMessages", totalMessages);
        stats.put("sessionTimeout", SESSION_TIMEOUT_HOURS + " hours");

        return stats;
    }

    /**
     * NEW: Search conversation history by query
     */
    public List<ChatResponse> searchConversationHistory(String conversationId, Long userId, String query) {
        try {
            // Verify access
            if (!isUserConversation(conversationId, userId)) {
                throw new RuntimeException("Access denied to conversation");
            }

            // Enhanced search query with conversation context
            String enhancedQuery = query + " conversation:" + conversationId;

            // Use RAG to search vector database
            RAGService.RAGContext ragContext = ragService.retrieveContext(enhancedQuery, conversationId, userId);

            List<ChatResponse> searchResults = new ArrayList<>();

            for (RAGService.KnowledgeDocument doc : ragContext.getRelevantDocuments()) {
                ChatResponse message = reconstructMessageFromDocument(doc);
                if (message != null && conversationId.equals(message.getConversationId())) {
                    searchResults.add(message);
                }
            }

            // Sort by timestamp (most recent first for search results)
            searchResults.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

            log.info("Found {} messages for search query '{}' in conversation {}",
                searchResults.size(), query, conversationId);

            return searchResults.stream().limit(10).toList(); // Limit search results

        } catch (Exception e) {
            log.error("Error searching conversation history", e);
            return new ArrayList<>();
        }
    }

    /**
     * NEW: Get recent conversation context for AI prompts
     */
    public String getConversationContextForAI(String conversationId, Long userId, int messageLimit) {
        List<ChatResponse> recentMessages = getSessionMessages(conversationId, userId)
            .stream()
            .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
            .limit(messageLimit)
            .toList();

        if (recentMessages.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder("=== RECENT CONVERSATION HISTORY ===\n");

        for (ChatResponse message : recentMessages) {
            String sender = "USER".equals(message.getSenderType()) ? "User" : "AI Assistant";
            String timestamp = message.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm"));

            context.append(String.format("[%s] %s: %s\n",
                timestamp,
                sender,
                truncateMessage(message.getContent(), 200)));
        }

        context.append("=== END CONVERSATION HISTORY ===\n\n");

        return context.toString();
    }

    /**
     * Helper method to truncate long messages
     */
    private String truncateMessage(String message, int maxLength) {
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength) + "...";
    }

    /**
     * Extract user ID from conversation ID
     */
    private Long extractUserIdFromConversation(String conversationId) {
        try {
            // Conversation ID format: session_userId_timestamp_sessionId
            String[] parts = conversationId.split("_");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (Exception e) {
            log.debug("Could not extract user ID from conversation ID: {}", conversationId);
        }
        return null;
    }

    /**
     * Get session conversation (compatibility method)
     */
    public ConversationDto getSessionConversation(String conversationId, Long userId) {
        // Verify user owns this conversation
        if (!isUserConversation(conversationId, userId)) {
            throw new RuntimeException("Access denied to conversation");
        }

        String key = SESSION_PREFIX + conversationId;
        Object conversation = redisTemplate.opsForValue().get(key);

        if (conversation instanceof ConversationDto conv) {
            return conv;
        }

        // Return default if not found
        return ConversationDto.builder()
            .conversationId(conversationId)
            .userId(userId)
            .title("AI Chat Session")
            .status("ACTIVE")
            .agentActive(true)
            .messageCount(0)
            .createdAt(LocalDateTime.now())
            .lastActivity(LocalDateTime.now())
            .build();
    }

    /**
     * Get user session conversations (compatibility method)
     */
    public List<ConversationDto> getUserSessionConversations(Long userId) {
        String userSessionsKey = SESSION_PREFIX + "user:" + userId;
        Set<Object> conversationIds = redisTemplate.opsForSet().members(userSessionsKey);

        if (conversationIds == null || conversationIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<ConversationDto> conversations = new ArrayList<>();
        for (Object convId : conversationIds) {
            String conversationKey = SESSION_PREFIX + convId.toString();
            Object conversation = redisTemplate.opsForValue().get(conversationKey);

            if (conversation instanceof ConversationDto conv) {
                conversations.add(conv);
            }
        }

        return conversations;
    }

    /**
     * Get all ephemeral conversations for all users (admin only)
     */
    public List<ConversationDto> getAllConversations() {
        List<ConversationDto> allConversations = new ArrayList<>();
        // Scan all keys with SESSION_PREFIX
        Set<String> keys = redisTemplate.keys(SESSION_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                Object obj = redisTemplate.opsForValue().get(key);
                if (obj instanceof ConversationDto conversation) {
                    allConversations.add(conversation);
                }
            }
        }
        return allConversations;
    }
}
