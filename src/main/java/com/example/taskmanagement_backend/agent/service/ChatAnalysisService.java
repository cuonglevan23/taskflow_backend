package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.dto.ChatAnalysisRequest;
import com.example.taskmanagement_backend.agent.dto.ChatAnalysisResponse;
import com.example.taskmanagement_backend.agent.entity.ChatMessage;
import com.example.taskmanagement_backend.agent.entity.Conversation;
import com.example.taskmanagement_backend.agent.entity.ConversationAnalysis;
import com.example.taskmanagement_backend.agent.memory.ChatMessageRepository;
import com.example.taskmanagement_backend.agent.memory.ConversationRepository;
import com.example.taskmanagement_backend.agent.memory.ConversationAnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatAnalysisService {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationAnalysisRepository analysisRepository; // Added repository for persistent storage
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final GeminiApiRateLimiterService rateLimiterService; // Centralized rate limiter

    // In-memory cache - conversationId -> analysis result
    private final Map<String, CachedAnalysis> analysisCache;

    // Request deduplication - conversationId -> future result
    private final Map<String, CompletableFuture<ChatAnalysisResponse>> pendingRequests;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.chat.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}")
    private String geminiChatUrl;

    @Value("${gemini.api.cache.minutes:60}")
    private int cacheExpiryMinutes;

    public ChatAnalysisService(
            ChatMessageRepository chatMessageRepository,
            ConversationRepository conversationRepository,
            ConversationAnalysisRepository analysisRepository,
            @Qualifier("geminiWebClient") WebClient webClient,
            ObjectMapper objectMapper,
            GeminiApiRateLimiterService rateLimiterService) {
        this.chatMessageRepository = chatMessageRepository;
        this.conversationRepository = conversationRepository;
        this.analysisRepository = analysisRepository;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.rateLimiterService = rateLimiterService;
        this.analysisCache = new ConcurrentHashMap<>();
        this.pendingRequests = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void init() {
        // Load analysis results from database into cache (optional, for fast startup)
        try {
            int cachedCount = preloadCacheFromDatabase();
            log.info("Preloaded {} conversation analysis results from database into memory cache", cachedCount);
        } catch (Exception e) {
            log.warn("Error preloading analysis results from database: {}", e.getMessage());
        }

        log.info("ChatAnalysisService initialized with caching ({} min expiry) and persistent storage",
                cacheExpiryMinutes);
    }

    /**
     * Preload frequently accessed analysis results from database
     * Only loads recent analysis results (within 24 hours)
     */
    private int preloadCacheFromDatabase() {
        // This is an optional optimization - can be implemented with pagination if many results
        int cachedCount = 0;
        try {
            // Query for most recently created analyses
            List<ConversationAnalysis> recentAnalyses = analysisRepository.findAll().stream()
                .filter(a -> !a.getIsDeleted())
                .filter(a -> a.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24)))
                .limit(100) // Limit to 100 most recent analyses
                .collect(Collectors.toList());

            // Add to cache
            for (ConversationAnalysis analysis : recentAnalyses) {
                ChatAnalysisResponse response = analysis.toDto(objectMapper);
                analysisCache.put(analysis.getConversationId(),
                    new CachedAnalysis(response, analysis.getUpdatedAt()));
                cachedCount++;
            }
        } catch (Exception e) {
            log.error("Error preloading cache from database", e);
        }
        return cachedCount;
    }

    /**
     * Get existing analysis for conversation if available
     * This API doesn't perform any new analysis - returns null if not available
     */
    public ChatAnalysisResponse getExistingAnalysis(String conversationId) {
        log.info("Getting existing analysis for conversation: {}", conversationId);

        // First check memory cache
        CachedAnalysis cachedResult = analysisCache.get(conversationId);
        if (cachedResult != null && !cachedResult.isExpired()) {
            log.debug("Found analysis in memory cache for conversation: {}", conversationId);
            return cachedResult.response;
        }

        // Then check database
        try {
            Optional<ConversationAnalysis> storedAnalysis =
                analysisRepository.findByConversationIdAndIsDeletedFalse(conversationId);

            if (storedAnalysis.isPresent()) {
                ChatAnalysisResponse response = storedAnalysis.get().toDto(objectMapper);

                // Update cache
                analysisCache.put(conversationId, new CachedAnalysis(response, storedAnalysis.get().getUpdatedAt()));

                log.info("Retrieved existing analysis from database for conversation: {}", conversationId);
                return response;
            }
        } catch (Exception e) {
            log.error("Error retrieving existing analysis from database", e);
        }

        // No analysis found
        return null;
    }

    /**
     * Analyze a single conversation
     * First tries to get existing analysis, then performs new analysis if needed
     */
    @Transactional
    public ChatAnalysisResponse analyzeSingleConversation(ChatAnalysisRequest request) {
        return analyzeSingleConversation(request, false);
    }

    /**
     * Analyze a single conversation with option to force refresh
     * @param request The analysis request
     * @param forceRefresh If true, always creates a new analysis (ignores cache and existing data)
     * @return The analysis response
     */
    @Transactional
    public ChatAnalysisResponse analyzeSingleConversation(ChatAnalysisRequest request, boolean forceRefresh) {
        log.info("Analyzing conversation: {} for user: {} (forceRefresh={})",
                request.getConversationId(), request.getUserId(), forceRefresh);

        String conversationId = request.getConversationId();

        // Step 1: Check if analysis is already in database (skip if forceRefresh)
        if (!forceRefresh) {
            ChatAnalysisResponse existingAnalysis = getExistingAnalysis(conversationId);
            if (existingAnalysis != null) {
                log.info("Using existing analysis for conversation: {}", conversationId);
                return existingAnalysis;
            }

            // Step 2: Check if there's already a pending request for this conversation
            CompletableFuture<ChatAnalysisResponse> pendingRequest = pendingRequests.get(conversationId);
            if (pendingRequest != null && !pendingRequest.isDone()) {
                try {
                    log.info("Reusing pending analysis request for conversation: {}", conversationId);
                    return pendingRequest.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Error getting pending analysis result: {}", e.getMessage());
                    // Continue with a new analysis if waiting fails
                }
            }
        } else {
            log.info("Force refresh requested, creating new analysis for conversation: {}", conversationId);
        }

        // Step 3: Create a new pending request
        CompletableFuture<ChatAnalysisResponse> newRequest = new CompletableFuture<>();
        pendingRequests.put(conversationId, newRequest);

        try {
            // Get conversation and messages
            Conversation conversation = conversationRepository
                    .findByConversationIdAndIsDeletedFalse(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

            List<ChatMessage> messages = chatMessageRepository
                    .findByConversationIdOrderByCreatedAtAsc(conversationId);

            messages = filterMessages(messages, request);

            if (messages.isEmpty()) {
                throw new IllegalStateException("No messages available for analysis in conversation " + conversationId);
            }

            String conversationText = buildConversationText(messages);

            // Perform analysis with rate limiting
            ChatAnalysisResponse response = performRateLimitedAnalysis(conversationText, conversation, messages.size());

            // Persist the analysis result to database
            saveAnalysisToDatabase(response);

            // Cache the result in memory
            cacheAnalysisResult(conversationId, response);

            // Complete the pending request
            newRequest.complete(response);

            return response;
        } catch (Exception e) {
            newRequest.completeExceptionally(e);
            pendingRequests.remove(conversationId);
            throw e;
        }
    }

    /**
     * Save analysis result to database for persistence
     */
    private void saveAnalysisToDatabase(ChatAnalysisResponse response) {
        try {
            String conversationId = response.getConversationId();

            // First check if analysis already exists
            Optional<ConversationAnalysis> existingAnalysis =
                analysisRepository.findByConversationIdAndIsDeletedFalse(conversationId);

            ConversationAnalysis entity;
            if (existingAnalysis.isPresent()) {
                // Update existing entity
                entity = existingAnalysis.get();
                // Update fields from the response
                entity.updateFromDto(response, objectMapper);
                log.info("Updating existing analysis for conversation: {}", conversationId);
            } else {
                // Create new entity
                entity = ConversationAnalysis.fromDto(response, objectMapper);
                log.info("Creating new analysis for conversation: {}", conversationId);
            }

            // Save to database
            analysisRepository.save(entity);

        } catch (Exception e) {
            log.error("Error saving analysis to database: {}", e.getMessage());
        }
    }

    /**
     * Perform analysis with rate limiting
     */
    private ChatAnalysisResponse performRateLimitedAnalysis(String conversationText, Conversation conversation, int messageCount) {
        try {
            // Try to acquire a rate limit token from centralized service
            if (!rateLimiterService.acquirePermit(5)) {
                log.warn("Gemini API rate limit reached for conversation: {}", conversation.getConversationId());
                return buildFallbackAnalysis(conversation, messageCount, conversationText, "RATE_LIMITED");
            }

            try {
                // Perform the actual analysis
                return performGeminiAnalysis(conversationText, conversation, messageCount);
            } catch (Exception e) {
                log.error("Error performing Gemini analysis", e);
                return buildFallbackAnalysis(conversation, messageCount, conversationText, "API_ERROR");
            }
        } catch (Exception e) {
            log.error("Error in rate-limited analysis", e);
            return buildFallbackAnalysis(conversation, messageCount, conversationText, "SYSTEM_ERROR");
        }
    }

    /**
     * Perform Gemini Analysis with strict rate limiting
     */
    private ChatAnalysisResponse performGeminiAnalysis(String conversationText, Conversation conversation, int messageCount) {
        try {
            log.info("Sending analysis request to Gemini API for conversation: {}", conversation.getConversationId());
            String prompt = buildGeminiAnalysisPrompt(conversationText);

            Map<String, Object> contents = new HashMap<>();
            contents.put("role", "user");
            contents.put("parts", Collections.singletonList(Collections.singletonMap("text", prompt)));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", Collections.singletonList(contents));

            try {
                String geminiResponse = webClient.post()
                        .uri(geminiChatUrl + "?key=" + geminiApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                log.info("Received response from Gemini API for conversation: {}", conversation.getConversationId());

                // Report successful API call
                rateLimiterService.reportSuccess();

                return parseGeminiResponse(geminiResponse, conversation, messageCount, conversationText);

            } catch (WebClientResponseException.TooManyRequests e) {
                // Handle 429 Too Many Requests error specifically
                log.error("Gemini API rate limit exceeded (429 Too Many Requests) for conversation: {}",
                    conversation.getConversationId());

                // Report to centralized rate limiter
                rateLimiterService.reportRateLimitExceeded();

                return buildFallbackAnalysis(conversation, messageCount, conversationText,
                    "RATE_LIMITED_429");
            }

        } catch (Exception e) {
            log.error("Gemini API analysis failed for conversation: {}", conversation.getConversationId(), e);
            return buildFallbackAnalysis(conversation, messageCount, conversationText, "API_ERROR");
        }
    }

    /**
     * Build prompt for Gemini
     */
    private String buildGeminiAnalysisPrompt(String conversationText) {
        return """
        BẠN LÀ CHUYÊN GIA PHÂN TÍCH CUỘC HỘI THOẠI.

        YÊU CẦU: Phân tích cuộc hội thoại sau và TRẢ VỀ JSON CHÍNH XÁC.

        CUỘC HỘI THOẠI:
        ========================================
        """ + conversationText + """

        ========================================

        PHÂN LOẠI:
        - POTENTIAL_CUSTOMER: khách hàng tiềm năng
        - COMPLAINT: khiếu nại, phàn nàn
        - SUPPORT_REQUEST: yêu cầu hỗ trợ
        - SMALLTALK: chuyện phiếm
        - TASK_COMMAND: quản lý task
        - MISSING_INFO: thiếu thông tin
        - SPAM: spam

        ĐỊNH DẠNG JSON PHẢI TRẢ VỀ:

        {
          "summary": "Tóm tắt ngắn",
          "primary_category": "DANH_MỤC_CHÍNH",
          "secondary_categories": ["DANH_MỤC_PHỤ"],
          "confidence": 0.85,
          "reasoning": "Giải thích ngắn"
        }
        """;
    }

    /**
     * Parse Gemini Response
     */
    private ChatAnalysisResponse parseGeminiResponse(String geminiResponse, Conversation conversation,
                                                     int messageCount, String conversationText) {
        try {
            JsonNode rootNode = objectMapper.readTree(geminiResponse);
            JsonNode candidatesNode = rootNode.path("candidates");

            if (candidatesNode.isArray() && candidatesNode.size() > 0) {
                JsonNode contentNode = candidatesNode.get(0).path("content");
                JsonNode partsNode = contentNode.path("parts");

                if (partsNode.isArray() && partsNode.size() > 0) {
                    String textResponse = partsNode.get(0).path("text").asText("");
                    String jsonStr = extractJsonFromText(textResponse);
                    if (!jsonStr.isEmpty()) {
                        return convertJsonToAnalysisResponse(jsonStr, conversation, messageCount);
                    }
                }
            }

            throw new RuntimeException("Invalid Gemini API response (no JSON found)");

        } catch (Exception e) {
            log.error("Error parsing Gemini API response", e);
            throw new RuntimeException("Failed to parse Gemini response", e);
        }
    }

    /**
     * Extract JSON from text
     */
    private String extractJsonFromText(String text) {
        int startIndex = text.indexOf('{');
        if (startIndex == -1) return "";

        int depth = 0;
        int endIndex = -1;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    endIndex = i;
                    break;
                }
            }
        }
        return (endIndex != -1) ? text.substring(startIndex, endIndex + 1) : "";
    }

    /**
     * Convert JSON -> Response
     */
    private ChatAnalysisResponse convertJsonToAnalysisResponse(String jsonStr, Conversation conversation, int messageCount) {
        try {
            JsonNode analysisNode = objectMapper.readTree(jsonStr);

            String summary = analysisNode.path("summary").asText("N/A");
            String primaryCategoryStr = analysisNode.path("primary_category").asText("SMALLTALK");
            double confidence = analysisNode.path("confidence").asDouble(0.7);
            String reasoning = analysisNode.path("reasoning").asText("");

            ChatAnalysisResponse.ChatCategory primaryCategory = parseCategory(primaryCategoryStr);
            List<ChatAnalysisResponse.ChatCategory> secondaryCategories = new ArrayList<>();

            JsonNode secondaryCategoriesNode = analysisNode.path("secondary_categories");
            if (secondaryCategoriesNode.isArray()) {
                for (JsonNode categoryNode : secondaryCategoriesNode) {
                    try {
                        secondaryCategories.add(parseCategory(categoryNode.asText()));
                    } catch (Exception ignored) {}
                }
            }

            Map<String, Object> additionalMetrics = new HashMap<>();
            additionalMetrics.put("reasoning", reasoning);
            additionalMetrics.put("analysisMethod", "GEMINI_API");
            additionalMetrics.put("model", "gemini-1.5-flash");

            return ChatAnalysisResponse.builder()
                    .conversationId(conversation.getConversationId())
                    .userId(conversation.getUserId())
                    .summary(summary)
                    .primaryCategory(primaryCategory)
                    .secondaryCategories(secondaryCategories)
                    .confidence(confidence)
                    .totalMessages(messageCount)
                    .analyzedMessages(messageCount)
                    .analysisTimestamp(LocalDateTime.now())
                    .additionalMetrics(additionalMetrics)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini JSON: " + jsonStr, e);
        }
    }

    private ChatAnalysisResponse.ChatCategory parseCategory(String categoryStr) {
        try {
            return ChatAnalysisResponse.ChatCategory.valueOf(categoryStr.toUpperCase());
        } catch (Exception e) {
            return ChatAnalysisResponse.ChatCategory.SMALLTALK;
        }
    }

    private boolean isSystemMessage(ChatMessage message) {
        return message.getSenderType().toString().contains("SYSTEM");
    }

    private boolean isWithinDateRange(ChatMessage message, LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && message.getCreatedAt().isBefore(startDate)) return false;
        if (endDate != null && message.getCreatedAt().isAfter(endDate)) return false;
        return true;
    }

    private String getSenderLabel(ChatMessage message) {
        return switch (message.getSenderType()) {
            case USER -> "User";
            case AGENT -> "AI";
            case SUPERVISOR -> "Admin";
        };
    }

    /**
     * Filter messages based on request parameters
     */
    private List<ChatMessage> filterMessages(List<ChatMessage> messages, ChatAnalysisRequest request) {
        LocalDateTime startDate = request.getStartDate();
        LocalDateTime endDate = request.getEndDate();
        Boolean includeSystemMessages = request.getIncludeSystemMessages();

        return messages.stream()
                .filter(message -> includeSystemMessages != null && includeSystemMessages || !isSystemMessage(message))
                .filter(message -> isWithinDateRange(message, startDate, endDate))
                .collect(Collectors.toList());
    }

    /**
     * Build text representation of conversation for analysis
     */
    private String buildConversationText(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();

        for (ChatMessage message : messages) {
            String senderLabel = getSenderLabel(message);
            builder.append(senderLabel).append(": ").append(message.getContent()).append("\n\n");
        }

        return builder.toString().trim();
    }

    /**
     * Cache analysis result in memory
     */
    private void cacheAnalysisResult(String conversationId, ChatAnalysisResponse response) {
        analysisCache.put(conversationId, new CachedAnalysis(response, LocalDateTime.now()));
        log.debug("Cached analysis result for conversation: {}", conversationId);
    }

    /**
     * Build fallback analysis when API fails or rate limit is exceeded
     */
    private ChatAnalysisResponse buildFallbackAnalysis(Conversation conversation, int messageCount,
                                                     String conversationText, String errorReason) {
        Map<String, Object> additionalMetrics = new HashMap<>();
        additionalMetrics.put("errorReason", errorReason);
        additionalMetrics.put("analysisMethod", "FALLBACK");

        // Extract a simple summary (first few characters of conversation)
        String simpleSummary = conversationText.length() > 100
            ? conversationText.substring(0, 100) + "..."
            : conversationText;

        return ChatAnalysisResponse.builder()
                .conversationId(conversation.getConversationId())
                .userId(conversation.getUserId())
                .summary(simpleSummary)
                .primaryCategory(ChatAnalysisResponse.ChatCategory.MISSING_INFO)
                .secondaryCategories(new ArrayList<>())
                .confidence(0.5)
                .totalMessages(messageCount)
                .analyzedMessages(messageCount)
                .analysisTimestamp(LocalDateTime.now())
                .additionalMetrics(additionalMetrics)
                .build();
    }

    /**
     * Inner class for caching analysis results with timestamp
     */
    private class CachedAnalysis {
        final ChatAnalysisResponse response;
        final LocalDateTime timestamp;

        CachedAnalysis(ChatAnalysisResponse response, LocalDateTime timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return timestamp.plusMinutes(cacheExpiryMinutes).isBefore(LocalDateTime.now());
        }
    }
}
