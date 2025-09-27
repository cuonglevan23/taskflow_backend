package com.example.taskmanagement_backend.agent.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Conversation Memory Service for Gemini AI Short-Term Memory
 *
 * Uses Redis to store conversation turns for building context prompts.
 * Keeps last N messages for each conversation to provide memory to Gemini.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis keys
    private static final String MEMORY_PREFIX = "gemini_memory:";
    private static final String CONVERSATION_KEY_PATTERN = MEMORY_PREFIX + "conv:%s";

    // Configuration
    private static final int MAX_MEMORY_TURNS = 10; // Keep last 10 turns (5 user + 5 AI)
    private static final int MEMORY_TTL_HOURS = 24; // 24 hours memory retention
    private static final int MAX_MESSAGE_LENGTH = 1000; // Truncate long messages

    /**
     * Store a conversation turn (user message + AI response)
     */
    public void storeConversationTurn(String conversationId, String userMessage, String aiResponse) {
        String memoryKey = String.format(CONVERSATION_KEY_PATTERN, conversationId);

        try {
            // Create turn object
            ConversationTurn turn = new ConversationTurn(
                userMessage,
                aiResponse,
                LocalDateTime.now()
            );

            // Add to Redis list (FIFO)
            redisTemplate.opsForList().rightPush(memoryKey, turn);

            // Keep only last N turns
            Long listSize = redisTemplate.opsForList().size(memoryKey);
            if (listSize != null && listSize > MAX_MEMORY_TURNS) {
                redisTemplate.opsForList().leftPop(memoryKey);
            }

            // Set TTL
            redisTemplate.expire(memoryKey, MEMORY_TTL_HOURS, TimeUnit.HOURS);

            log.debug("📝 Stored conversation turn for {}: user={}, ai={}",
                conversationId,
                truncate(userMessage, 50),
                truncate(aiResponse, 50));

        } catch (Exception e) {
            log.warn("❌ Failed to store conversation turn: {}", e.getMessage());
        }
    }

    /**
     * Store only user message (when we haven't got AI response yet)
     */
    public void storeUserMessage(String conversationId, String userMessage) {
        String memoryKey = String.format(CONVERSATION_KEY_PATTERN, conversationId);

        try {
            // Create partial turn with only user message
            ConversationTurn turn = new ConversationTurn(
                userMessage,
                null, // AI response will be added later
                LocalDateTime.now()
            );

            redisTemplate.opsForList().rightPush(memoryKey, turn);
            redisTemplate.expire(memoryKey, MEMORY_TTL_HOURS, TimeUnit.HOURS);

            log.debug("📝 Stored user message for {}: {}", conversationId, truncate(userMessage, 50));

        } catch (Exception e) {
            log.warn("❌ Failed to store user message: {}", e.getMessage());
        }
    }

    /**
     * Update the last turn with AI response
     */
    public void updateLastTurnWithAIResponse(String conversationId, String aiResponse) {
        String memoryKey = String.format(CONVERSATION_KEY_PATTERN, conversationId);

        try {
            // Get the last turn
            Object lastTurnObj = redisTemplate.opsForList().rightPop(memoryKey);

            if (lastTurnObj instanceof ConversationTurn lastTurn) {
                // Update with AI response
                lastTurn.setAiResponse(aiResponse);

                // Put it back
                redisTemplate.opsForList().rightPush(memoryKey, lastTurn);
                redisTemplate.expire(memoryKey, MEMORY_TTL_HOURS, TimeUnit.HOURS);

                log.debug("✅ Updated last turn with AI response for {}", conversationId);
            }

        } catch (Exception e) {
            log.warn("❌ Failed to update last turn with AI response: {}", e.getMessage());
        }
    }

    /**
     * Get conversation memory for building Gemini prompt
     * Returns formatted messages for Gemini API
     */
    public List<Map<String, String>> getConversationMemoryForGemini(String conversationId) {
        String memoryKey = String.format(CONVERSATION_KEY_PATTERN, conversationId);

        try {
            List<Object> turns = redisTemplate.opsForList().range(memoryKey, 0, -1);

            if (turns == null || turns.isEmpty()) {
                log.debug("🔍 No conversation memory found for {}", conversationId);
                return new ArrayList<>();
            }

            List<Map<String, String>> geminiMessages = new ArrayList<>();

            // Add system message first
            geminiMessages.add(Map.of(
                "role", "system",
                "content", "Bạn là TaskFlow AI Assistant - trợ lý thông minh giúp quản lý tác vụ. " +
                         "Bạn có thể tạo, cập nhật, xóa task và trả lời các câu hỏi về dự án. " +
                         "Luôn thân thiện, hữu ích và ghi nhớ ngữ cảnh cuộc trò chuyện."
            ));

            // Convert turns to Gemini format
            for (Object turnObj : turns) {
                if (turnObj instanceof ConversationTurn turn) {
                    // Add user message
                    if (turn.getUserMessage() != null) {
                        geminiMessages.add(Map.of(
                            "role", "user",
                            "content", truncate(turn.getUserMessage(), MAX_MESSAGE_LENGTH)
                        ));
                    }

                    // Add AI response (if available)
                    if (turn.getAiResponse() != null) {
                        geminiMessages.add(Map.of(
                            "role", "assistant",
                            "content", truncate(turn.getAiResponse(), MAX_MESSAGE_LENGTH)
                        ));
                    }
                }
            }

            log.debug("🧠 Retrieved {} memory messages for Gemini (conversation: {})",
                geminiMessages.size(), conversationId);

            return geminiMessages;

        } catch (Exception e) {
            log.warn("❌ Failed to get conversation memory: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get recent conversation context as formatted string (for RAG/context building)
     */
    public String getConversationContextString(String conversationId, int maxTurns) {
        String memoryKey = String.format(CONVERSATION_KEY_PATTERN, conversationId);

        try {
            // Get last N turns
            List<Object> turns = redisTemplate.opsForList().range(memoryKey, -maxTurns, -1);

            if (turns == null || turns.isEmpty()) {
                return "";
            }

            StringBuilder context = new StringBuilder("=== RECENT CONVERSATION ===\n");

            for (Object turnObj : turns) {
                if (turnObj instanceof ConversationTurn turn) {
                    String timestamp = turn.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm"));

                    if (turn.getUserMessage() != null) {
                        context.append(String.format("[%s] User: %s\n",
                            timestamp, truncate(turn.getUserMessage(), 200)));
                    }

                    if (turn.getAiResponse() != null) {
                        context.append(String.format("[%s] AI: %s\n",
                            timestamp, truncate(turn.getAiResponse(), 200)));
                    }
                }
            }

            context.append("=== END CONVERSATION ===\n");

            return context.toString();

        } catch (Exception e) {
            log.debug("Could not get conversation context: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Clear conversation memory
     */
    public void clearConversationMemory(String conversationId) {
        String memoryKey = String.format(CONVERSATION_KEY_PATTERN, conversationId);

        try {
            redisTemplate.delete(memoryKey);
            log.info("🗑️ Cleared conversation memory for {}", conversationId);

        } catch (Exception e) {
            log.warn("❌ Failed to clear conversation memory: {}", e.getMessage());
        }
    }

    /**
     * Get conversation memory statistics
     */
    public Map<String, Object> getMemoryStats(String conversationId) {
        String memoryKey = String.format(CONVERSATION_KEY_PATTERN, conversationId);

        try {
            Long turnCount = redisTemplate.opsForList().size(memoryKey);
            Long ttl = redisTemplate.getExpire(memoryKey, TimeUnit.SECONDS);

            Map<String, Object> stats = new HashMap<>();
            stats.put("conversationId", conversationId);
            stats.put("turnCount", turnCount != null ? turnCount : 0);
            stats.put("ttlSeconds", ttl != null ? ttl : 0);
            stats.put("maxTurns", MAX_MEMORY_TURNS);
            stats.put("memoryActive", turnCount != null && turnCount > 0);

            return stats;

        } catch (Exception e) {
            log.warn("❌ Failed to get memory stats: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Search conversation memory for specific content
     */
    public List<ConversationTurn> searchMemory(String conversationId, String searchTerm) {
        String memoryKey = String.format(CONVERSATION_KEY_PATTERN, conversationId);

        try {
            List<Object> turns = redisTemplate.opsForList().range(memoryKey, 0, -1);

            if (turns == null) {
                return new ArrayList<>();
            }

            String lowerSearchTerm = searchTerm.toLowerCase();

            return turns.stream()
                .filter(ConversationTurn.class::isInstance)
                .map(ConversationTurn.class::cast)
                .filter(turn -> {
                    String userMsg = turn.getUserMessage();
                    String aiMsg = turn.getAiResponse();

                    return (userMsg != null && userMsg.toLowerCase().contains(lowerSearchTerm)) ||
                           (aiMsg != null && aiMsg.toLowerCase().contains(lowerSearchTerm));
                })
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("❌ Failed to search memory: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Helper method to truncate long text
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Data class for storing conversation turns
     */
    @Data
    public static class ConversationTurn {
        private String userMessage;
        private String aiResponse;
        private LocalDateTime timestamp;

        public ConversationTurn() {}

        public ConversationTurn(String userMessage, String aiResponse, LocalDateTime timestamp) {
            this.userMessage = userMessage;
            this.aiResponse = aiResponse;
            this.timestamp = timestamp;
        }
    }
}
