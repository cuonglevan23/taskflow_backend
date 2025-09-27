package com.example.taskmanagement_backend.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unified AI Service - Single point for all Gemini API calls
 * Prevents 429 errors by centralizing API call management
 * Combines Intent Detection, Moderation, and Response Generation in ONE call
 */
@Slf4j
@Service
public class UnifiedAIService {

    private final WebClient webClient;
    private final GeminiApiRateLimiterService rateLimiterService;
    private final ConcurrentHashMap<String, UnifiedResponse> responseCache = new ConcurrentHashMap<>();

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.chat.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String geminiChatUrl;

    @Value("${gemini.api.timeout.seconds:60}")
    private int geminiApiTimeoutSeconds;

    public UnifiedAIService(
            @Qualifier("geminiWebClient") WebClient webClient,
            GeminiApiRateLimiterService rateLimiterService) {
        this.webClient = webClient;
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Single unified call that handles Intent + Moderation + Response Generation
     */
    public UnifiedResponse processUnifiedRequest(UnifiedRequest request) {
        String cacheKey = buildCacheKey(request);

        // Check cache first
        if (responseCache.containsKey(cacheKey)) {
            log.debug("Returning cached unified response");
            return responseCache.get(cacheKey);
        }

        // Use centralized rate limiter
        if (!rateLimiterService.acquirePermit(5)) {
            log.warn("Rate limiting: Using fallback unified response (denied by rate limiter)");
            return generateFallbackResponse(request);
        }

        try {
            // Make single unified API call
            UnifiedResponse response = callUnifiedGeminiAPI(request);

            // Cache response
            responseCache.put(cacheKey, response);

            return response;
        } catch (Exception e) {
            log.error("Error in unified API processing", e);
            return generateFallbackResponse(request);
        }
    }

    /**
     * Make single API call that handles everything
     */
    private UnifiedResponse callUnifiedGeminiAPI(UnifiedRequest request) {
        try {
            if (geminiApiKey == null || geminiApiKey.isEmpty()) {
                return generateFallbackResponse(request);
            }

            String unifiedPrompt = buildUnifiedPrompt(request);

            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", unifiedPrompt)))
                ),
                "generationConfig", Map.of(
                    "temperature", 0.7,
                    "topK", 40,
                    "topP", 0.95,
                    "maxOutputTokens", 3000
                ),
                "safetySettings", List.of(
                    Map.of("category", "HARM_CATEGORY_HARASSMENT", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                    Map.of("category", "HARM_CATEGORY_HATE_SPEECH", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                    Map.of("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                    Map.of("category", "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold", "BLOCK_MEDIUM_AND_ABOVE")
                )
            );

            log.debug("Making unified Gemini API call for user: {}", request.getUserId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                .uri(geminiChatUrl + "?key=" + geminiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(geminiApiTimeoutSeconds))
                .block();

            String aiResponse = extractAIResponse(response);
            return parseUnifiedResponse(aiResponse, request);

        } catch (WebClientResponseException.TooManyRequests e) {
            log.error("429 Too Many Requests - This should not happen with unified service!");
            return generateFallbackResponse(request);
        } catch (Exception e) {
            log.error("Error in unified Gemini API call", e);
            return generateFallbackResponse(request);
        }
    }

    /**
     * Build comprehensive prompt that handles all tasks in one go
     * ENHANCED: RAG-powered context understanding with conversation memory
     */
    private String buildUnifiedPrompt(UnifiedRequest request) {
        StringBuilder prompt = new StringBuilder();

        // System context - Define AI's role and capabilities
        prompt.append("Bạn là TaskFlow AI Assistant - một trợ lý thông minh với khả năng hiểu ngữ cảnh và nhớ cuộc hội thoại.\n\n");

        // User context for personalization
        if (request.getUserContext() != null) {
            prompt.append("THÔNG TIN NGƯỜI DÙNG:\n");
            prompt.append("- Tên: ").append(request.getUserContext().getFirstName() != null ?
                request.getUserContext().getFirstName() : "Người dùng").append("\n");
            prompt.append("- Email: ").append(request.getUserContext().getEmail()).append("\n");
            prompt.append("- Vai trò: ").append(request.getUserContext().getSystemRole()).append("\n");
            prompt.append("- Premium: ").append(request.getUserContext().getIsPremium() ? "Có" : "Không").append("\n\n");
        }

        // CRITICAL: Conversation memory and context understanding
        prompt.append("NGUYÊN TẮC HIỂU NGỮ CẢNH QUAN TRỌNG:\n\n");
        prompt.append("1. PHÂN TÍCH Ý ĐỊNH THẬT SỰ:\n");
        prompt.append("- 'tôi không muốn' = TỪ CHỐI, không phải tên task\n");
        prompt.append("- 'không' = PHỦ ĐỊNH, dừng hành động hiện tại\n");
        prompt.append("- 'tôi không muốn tạo task' = TỪ CHỐI tạo task, đừng tạo\n");
        prompt.append("- 'không cần' = KHÔNG MUỐN tiếp tục\n");
        prompt.append("- Khi user từ chối, hãy hỏi họ muốn làm gì khác\n\n");

        prompt.append("2. HIỂU NGỮ CẢNH CUỘC HỘI THOẠI:\n");
        prompt.append("- Đọc lịch sử để hiểu user đang từ chối hay đồng ý\n");
        prompt.append("- Nếu user vừa từ chối một hành động, đừng tiếp tục hành động đó\n");
        prompt.append("- Thay vào đó, hỏi họ muốn làm gì khác\n");
        prompt.append("- Luôn tôn trọng ý muốn của user\n\n");

        prompt.append("3. XỬ LÝ TỪ CHỐI THÔNG MINH:\n");
        prompt.append("- Khi user nói 'không', 'không muốn' → Intent = DECLINING (từ chối)\n");
        prompt.append("- Phản hồi: 'Tôi hiểu, bạn không muốn [hành động]. Vậy bạn muốn làm gì khác?'\n");
        prompt.append("- ĐỪNG tiếp tục hành động bị từ chối\n");
        prompt.append("- Đề xuất các options khác\n\n");

        // Enhanced conversation history with context analysis
        if (request.getConversationHistory() != null && !request.getConversationHistory().trim().isEmpty()) {
            prompt.append("LỊCH SỬ HỘI THOẠI (QUAN TRỌNG - phân tích ngữ cảnh):\n");
            prompt.append(request.getConversationHistory()).append("\n");
            prompt.append("PHÂN TÍCH: Dựa vào lịch sử trên, user đang trong trạng thái gì?\n");
            prompt.append("- Có phải user vừa từ chối một yêu cầu không?\n");
            prompt.append("- User có đang tỏ ra khó chịu hay không hài lòng không?\n");
            prompt.append("- Cần thay đổi approach như thế nào?\n\n");
        }

        // Provide contextual information
        if (request.getContext() != null && !request.getContext().trim().isEmpty()) {
            prompt.append("NGỮ CẢNH BỔ SUNG:\n");
            prompt.append(request.getContext()).append("\n\n");
        }

        // The actual user message to analyze
        prompt.append("TIN NHẮN HIỆN TẠI CẦN PHÂN TÍCH: \"").append(request.getUserMessage()).append("\"\n\n");

        // Enhanced intelligent task instructions
        prompt.append("PHÂN TÍCH THÔNG MINH VỚI CONTEXT:\n\n");

        prompt.append("1. KIỂM DUYỆT AN TOÀN:\n");
        prompt.append("- Đánh giá tính an toàn của tin nhắn\n");
        prompt.append("- Nếu không an toàn: trả về [BLOCKED: lý do]\n\n");

        prompt.append("2. PHÂN TÍCH Ý ĐỊNH VỚI NGỮ CẢNH:\n");
        prompt.append("- DECLINING: User từ chối/không muốn (ví dụ: 'tôi không muốn', 'không')\n");
        prompt.append("- GREETING: Chào hỏi, giới thiệu\n");
        prompt.append("- ACTION: Muốn thực hiện hành động (tạo, cập nhật, xóa)\n");
        prompt.append("- INFORMATION_RETRIEVAL: Muốn xem thông tin\n");
        prompt.append("- QUESTION: Hỏi về tính năng, khả năng\n");
        prompt.append("- CONVERSATIONAL: Trò chuyện thông thường\n");
        prompt.append("- CLARIFICATION_REQUEST: Cần làm rõ ý định\n\n");

        prompt.append("3. TẠO PHẢN HỒI THÔNG MINH:\n");
        prompt.append("- Dựa trên lịch sử + tin nhắn hiện tại + ngữ cảnh\n");
        prompt.append("- Nếu user từ chối: tôn trọng và đề xuất options khác\n");
        prompt.append("- Nếu user hài lòng: tiếp tục hỗ trợ tích cực\n");
        prompt.append("- Cá nhân hóa theo thông tin user\n");
        prompt.append("- Sử dụng emoji phù hợp với tâm trạng\n\n");

        // Enhanced response format with context understanding
        prompt.append("PHẢN HỒI JSON (bao gồm phân tích ngữ cảnh):\n");
        prompt.append("{\n");
        prompt.append("  \"moderation\": {\n");
        prompt.append("    \"safe\": true/false,\n");
        prompt.append("    \"reason\": \"lý do nếu không safe\"\n");
        prompt.append("  },\n");
        prompt.append("  \"contextAnalysis\": {\n");
        prompt.append("    \"userMood\": \"positive/neutral/negative/declining\",\n");
        prompt.append("    \"conversationFlow\": \"starting/continuing/declining/frustrated/satisfied\",\n");
        prompt.append("    \"previousAction\": \"action user vừa từ chối hoặc đồng ý\",\n");
        prompt.append("    \"needsClarification\": true/false\n");
        prompt.append("  },\n");
        prompt.append("  \"intent\": {\n");
        prompt.append("    \"type\": \"DECLINING|GREETING|ACTION|INFORMATION_RETRIEVAL|QUESTION|CONVERSATIONAL|CLARIFICATION_REQUEST\",\n");
        prompt.append("    \"confidence\": 0.0-1.0,\n");
        prompt.append("    \"reasoning\": \"tại sao phân loại như vậy dựa trên context\",\n");
        prompt.append("    \"taskAction\": \"CREATE_TASK|GET_TASKS|UPDATE_TASK|DELETE_TASK|GET_STATISTICS|GENERAL|DECLINE\"\n");
        prompt.append("  },\n");
        prompt.append("  \"response\": {\n");
        prompt.append("    \"content\": \"phản hồi thông minh dựa trên context và lịch sử\",\n");
        prompt.append("    \"shouldUseTools\": true/false,\n");
        prompt.append("    \"suggestedActions\": [\"các hành động phù hợp với tâm trạng user\"],\n");
        prompt.append("    \"tone\": \"supportive/apologetic/enthusiastic/neutral\"\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // Critical instructions for context understanding
        prompt.append("LƯU Ý SIÊU QUAN TRỌNG:\n");
        prompt.append("- ĐỪNG BAO GIỜ biến từ chối thành tên task (ví dụ: 'tôi không muốn' ≠ tên task)\n");
        prompt.append("- KHI user nói 'không muốn', hãy hỏi họ muốn làm gì khác\n");
        prompt.append("- LUÔN đọc lịch sử trước khi quyết định hành động\n");
        prompt.append("- TÔN TRỌNG ý muốn và tâm trạng của user\n");
        prompt.append("- NÉU user từ chối 2-3 lần, hãy ngừng đề xuất và chờ họ chỉ dẫn\n");

        return prompt.toString();
    }

    /**
     * Extract AI response from Gemini API
     */
    private String extractAIResponse(Map<String, Object> response) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");

            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> candidate = candidates.getFirst();
                @SuppressWarnings("unchecked")
                Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.getFirst().get("text");
                }
            }
        } catch (Exception e) {
            log.error("Error extracting AI response", e);
        }

        return "{}";
    }

    /**
     * Parse unified response from AI
     */
    private UnifiedResponse parseUnifiedResponse(String aiResponse, UnifiedRequest request) {
        try {
            // Clean JSON response
            String jsonStr = aiResponse.trim();
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            }
            if (jsonStr.contains("{")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("{"));
                jsonStr = jsonStr.substring(0, jsonStr.lastIndexOf("}") + 1);
            }

            // Simple parsing for unified response
            return parseJsonUnifiedResponse(jsonStr, request);

        } catch (Exception e) {
            log.warn("Error parsing unified response, using fallback: {}", e.getMessage());
            return generateFallbackResponse(request);
        }
    }

    /**
     * Parse JSON unified response - ENHANCED to handle intelligent analysis
     */
    private UnifiedResponse parseJsonUnifiedResponse(String jsonStr, UnifiedRequest request) {
        try {
            // Parse moderation
            boolean isSafe = !jsonStr.contains("\"safe\": false");
            String moderationReason = "";
            if (!isSafe && jsonStr.contains("\"reason\"")) {
                moderationReason = extractJsonField(jsonStr, "reason");
            }

            // Parse intent with enhanced extraction
            String intentType = extractJsonField(jsonStr, "type", "CONVERSATIONAL");
            double confidence = parseConfidence(jsonStr);
            String taskAction = extractJsonField(jsonStr, "taskAction", "GENERAL");

            // Extract intelligent analysis data
            String reasoning = extractJsonField(jsonStr, "reasoning");
            String contextUnderstanding = extractJsonField(jsonStr, "context_understanding");

            // Parse response content with better extraction
            String responseContent = extractResponseContent(jsonStr);
            boolean shouldUseTools = jsonStr.contains("\"shouldUseTools\": true") ||
                                   shouldAutoEnableTools(taskAction, responseContent);

            log.debug("Parsed unified response: intent={}, taskAction={}, confidence={}, reasoning={}",
                     intentType, taskAction, confidence, reasoning);

            return UnifiedResponse.builder()
                .safe(isSafe)
                .moderationReason(moderationReason)
                .intentType(intentType)
                .confidence(confidence)
                .taskAction(taskAction)
                .responseContent(responseContent)
                .shouldUseTools(shouldUseTools)
                .timestamp(LocalDateTime.now())
                .source("AI_UNIFIED_INTELLIGENT")
                .build();

        } catch (Exception e) {
            log.warn("JSON parsing failed, using intelligent fallback: {}", e.getMessage());
            return generateIntelligentFallback(request);
        }
    }

    /**
     * Extract JSON field with fallback
     */
    private String extractJsonField(String jsonStr, String fieldName) {
        return extractJsonField(jsonStr, fieldName, "");
    }

    private String extractJsonField(String jsonStr, String fieldName, String defaultValue) {
        try {
            String pattern = "\"" + fieldName + "\":";
            int start = jsonStr.indexOf(pattern);
            if (start == -1) return defaultValue;

            start += pattern.length();
            // Skip whitespace and quotes
            while (start < jsonStr.length() && (jsonStr.charAt(start) == ' ' || jsonStr.charAt(start) == '"')) {
                start++;
            }

            int end = jsonStr.indexOf("\"", start);
            if (end == -1) {
                end = jsonStr.indexOf(",", start);
                if (end == -1) {
                    end = jsonStr.indexOf("}", start);
                }
            }

            if (end > start) {
                return jsonStr.substring(start, end).trim().replace("\"", "");
            }
        } catch (Exception e) {
            log.debug("Error extracting field {}: {}", fieldName, e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Parse confidence with smart fallback
     */
    private double parseConfidence(String jsonStr) {
        try {
            String confidenceStr = extractJsonField(jsonStr, "confidence", "0.5");
            return Double.parseDouble(confidenceStr.replace("\"", ""));
        } catch (Exception e) {
            return 0.5;
        }
    }

    /**
     * Auto-enable tools based on task action and content analysis
     */
    private boolean shouldAutoEnableTools(String taskAction, String responseContent) {
        // Auto-enable tools for specific actions
        if ("CREATE_TASK".equals(taskAction) || "GET_TASKS".equals(taskAction) ||
            "GET_STATISTICS".equals(taskAction) || "UPDATE_TASK".equals(taskAction) ||
            "DELETE_TASK".equals(taskAction)) {
            return true;
        }

        // Content-based detection
        String lowerContent = responseContent.toLowerCase();
        return lowerContent.contains("tạo task") || lowerContent.contains("hiển thị") ||
               lowerContent.contains("thống kê") || lowerContent.contains("danh sách");
    }

    /**
     * Generate intelligent fallback when parsing fails
     */
    private UnifiedResponse generateIntelligentFallback(UnifiedRequest request) {
        String userMessage = request.getUserMessage().toLowerCase().trim();

        // Use more intelligent pattern matching
        String intentType = analyzeIntentIntelligently(userMessage);
        String taskAction = analyzeTaskActionIntelligently(userMessage);
        String responseContent = generateContextualResponse(userMessage, request.getUserContext());
        boolean shouldUseTools = shouldAutoEnableTools(taskAction, responseContent);

        log.info("Generated intelligent fallback: intent={}, taskAction={}, tools={}",
                intentType, taskAction, shouldUseTools);

        return UnifiedResponse.builder()
            .safe(true)
            .moderationReason("")
            .intentType(intentType)
            .confidence(0.7)
            .taskAction(taskAction)
            .responseContent(responseContent)
            .shouldUseTools(shouldUseTools)
            .timestamp(LocalDateTime.now())
            .source("INTELLIGENT_FALLBACK")
            .build();
    }

    /**
     * Intelligent intent analysis - IMPROVED: Trust AI model classification over hard-coding
     */
    private String analyzeIntentIntelligently(String userMessage) {
        // FIXED: Remove hard-coded declining detection - let Gemini AI classify properly
        // Questions like "bạn tạo được project không?" should be QUESTION, not DECLINING

        // Greeting patterns
        if (userMessage.contains("chào") || userMessage.contains("hello") ||
            userMessage.contains("xin chào") || userMessage.contains("bạn là")) {
            return "GREETING";
        }

        // Question patterns - include "không" questions as QUESTION not DECLINING
        if (userMessage.contains("có thể") || userMessage.contains("can") ||
            userMessage.contains("bạn có") || userMessage.contains("làm được") ||
            userMessage.contains("model nào") || userMessage.contains("tính năng") ||
            userMessage.endsWith("không?") || userMessage.endsWith("không")) { // FIXED: Questions ending with "không?" are QUESTIONS
            return "QUESTION";
        }

        // Action patterns - more comprehensive
        if ((userMessage.contains("tạo") && (userMessage.contains("task") || userMessage.contains("công việc"))) ||
            userMessage.contains("create") || userMessage.contains("add") ||
            userMessage.contains("thêm") || userMessage.contains("new") ||
            (userMessage.contains("như thế nào") && userMessage.contains("tạo"))) {
            return "ACTION";
        }

        // Information retrieval patterns
        if (userMessage.contains("hiển thị") || userMessage.contains("show") ||
            userMessage.contains("list") || userMessage.contains("xem") ||
            userMessage.contains("danh sách") || userMessage.contains("thống kê") ||
            userMessage.contains("báo cáo") || userMessage.contains("statistics")) {
            return "INFORMATION_RETRIEVAL";
        }

        // Only detect DECLINING for very explicit rejections, not questions
        if (userMessage.trim().equals("không") || userMessage.trim().equals("no") ||
            userMessage.contains("không muốn làm") || userMessage.contains("dừng lại") ||
            userMessage.contains("thôi không") || userMessage.contains("bỏ qua")) {
            return "DECLINING";
        }

        return "CONVERSATIONAL";
    }

    /**
     * Intelligent task action analysis - IMPROVED: Trust AI model over hard-coding
     */
    private String analyzeTaskActionIntelligently(String userMessage) {
        // FIXED: Only detect DECLINE for explicit rejections, not questions
        if (userMessage.trim().equals("không") || userMessage.trim().equals("no") ||
            userMessage.contains("không muốn làm") || userMessage.contains("dừng lại")) {
            return "DECLINE";
        }

        if (userMessage.contains("tạo") && (userMessage.contains("task") || userMessage.contains("công việc") || userMessage.contains("project"))) {
            return "CREATE_TASK";
        }
        if (userMessage.contains("hiển thị") || userMessage.contains("show") || userMessage.contains("list")) {
            return "GET_TASKS";
        }
        if (userMessage.contains("thống kê") || userMessage.contains("statistics") || userMessage.contains("báo cáo")) {
            return "GET_STATISTICS";
        }
        if (userMessage.contains("cập nhật") || userMessage.contains("update") || userMessage.contains("sửa")) {
            return "UPDATE_TASK";
        }
        if (userMessage.contains("xóa") || userMessage.contains("delete") || userMessage.contains("remove")) {
            return "DELETE_TASK";
        }
        return "GENERAL";
    }

    /**
     * Generate contextual response - IMPROVED: Handle questions properly
     */
    private String generateContextualResponse(String userMessage, UserContextService.UserChatContext userContext) {
        String userName = userContext != null && userContext.getFirstName() != null ?
                         userContext.getFirstName() : "bạn";

        // FIXED: Handle questions ending with "không?" properly as QUESTIONS not DECLINING
        if (userMessage.endsWith("không?") ||
            (userMessage.contains("bạn") && userMessage.contains("tạo được") && userMessage.contains("không"))) {
            // This is a question about capabilities, not declining
            if (userMessage.contains("project")) {
                return String.format("Chào %s! 🤔 Hiện tại tôi chưa thể trực tiếp tạo project, " +
                       "nhưng tôi có thể hỗ trợ bạn:\n\n" +
                       "• 📝 Tạo và quản lý task\n" +
                       "• 📊 Xem thống kê công việc\n" +
                       "• 💡 Gợi ý cách tổ chức dự án\n" +
                       "• 📋 Theo dõi tiến độ\n\n" +
                       "Bạn muốn tôi giúp gì cụ thể? 😊", userName);
            }
            return String.format("Chào %s! Đó là câu hỏi hay! Tôi có thể:\n\n" +
                   "• 📝 Quản lý task và công việc\n• 📊 Cung cấp thống kê\n" +
                   "• ❓ Trả lời câu hỏi\n• 💬 Trò chuyện hỗ trợ\n\n" +
                   "Bạn muốn biết thêm về tính năng nào? 😊", userName);
        }

        // Handle only explicit declining/rejection (not questions)
        if (userMessage.trim().equals("không") || userMessage.trim().equals("no") ||
            userMessage.contains("không muốn làm") || userMessage.contains("thôi không")) {
            return String.format("Tôi hiểu rồi %s! 😊 Bạn không muốn làm điều đó. " +
                   "Không sao cả, tôi hoàn toàn tôn trọng quyết định của bạn.\n\n" +
                   "Vậy bạn có muốn:\n" +
                   "• 💬 Trò chuyện về chủ đề khác\n" +
                   "• 📊 Xem thống kê công việc\n" +
                   "• ❓ Hỏi tôi điều gì đó\n" +
                   "• 📝 Xem danh sách task hiện có\n\n" +
                   "Hoặc chỉ cần nói với tôi bạn muốn gì nhé! 🙂", userName);
        }

        if (userMessage.contains("chào") || userMessage.contains("hello")) {
            return String.format("Xin chào %s! 👋 Tôi là TaskFlow AI Assistant. " +
                   "Tôi có thể giúp bạn quản lý task, trả lời câu hỏi và hỗ trợ công việc. Bạn cần gì? 😊", userName);
        }

        if (userMessage.contains("tạo") && userMessage.contains("task")) {
            return String.format("💼 Chào %s! Tôi sẽ giúp bạn tạo task mới. " +
                   "Bạn có thể nói cụ thể hơn về task muốn tạo không?\n\n" +
                   "Ví dụ: 'Tạo task hoàn thành báo cáo tháng 9'", userName);
        }

        if (userMessage.contains("model") || userMessage.contains("bạn là")) {
            return String.format("Chào %s! 🤖 Tôi là TaskFlow AI Assistant - trợ lý thông minh được tích hợp " +
                   "Gemini AI. Tôi có thể:\n\n• Quản lý task và dự án\n• Phân tích và thống kê\n" +
                   "• Trả lời câu hỏi\n• Hỗ trợ công việc hàng ngày\n\nBạn muốn thử tính năng nào? 😊", userName);
        }

        return String.format("💬 Chào %s! Tôi hiểu bạn muốn trò chuyện. Tôi có thể giúp bạn:\n\n" +
               "• 📝 Quản lý task và công việc\n• 📊 Xem thống kê và báo cáo\n" +
               "• ❓ Trả lời câu hỏi\n• 💬 Chat thân thiện\n\nBạn muốn làm gì? 😊", userName);
    }

    /**
     * Extract response content from JSON
     */
    private String extractResponseContent(String jsonStr) {
        try {
            if (jsonStr.contains("\"content\":")) {
                int contentStart = jsonStr.indexOf("\"content\":") + 11;
                int contentEnd = jsonStr.indexOf("\",", contentStart);
                if (contentEnd == -1) {
                    contentEnd = jsonStr.indexOf("\"", contentStart + 1);
                }
                if (contentEnd > contentStart) {
                    return jsonStr.substring(contentStart, contentEnd)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"");
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract content: {}", e.getMessage());
        }

        return "Xin chào! Tôi là TaskFlow AI Assistant. Tôi có thể giúp bạn quản lý task và trả lời câu hỏi. Bạn cần hỗ trợ gì?";
    }

    /**
     * Generate fallback response when API fails
     */
    private UnifiedResponse generateFallbackResponse(UnifiedRequest request) {
        String userMessage = request.getUserMessage().toLowerCase();

        // Rule-based intent detection
        String intentType = "CONVERSATIONAL";
        String taskAction = "GENERAL";
        String responseContent;

        if (userMessage.contains("chào") || userMessage.contains("hello") || userMessage.contains("bạn là ai")) {
            intentType = "GREETING";
            responseContent = "Xin chào! Tôi là TaskFlow AI Assistant - trợ lý thông minh của bạn. " +
                            "Tôi có thể giúp bạn quản lý task, trả lời câu hỏi và hỗ trợ công việc. Bạn cần gì? 😊";
        } else if (userMessage.contains("tạo") && userMessage.contains("task")) {
            intentType = "ACTION";
            taskAction = "CREATE_TASK";
            responseContent = "💼 Tôi sẽ giúp bạn tạo task! Bạn có thể nói cụ thể hơn về task muốn tạo không?\n\n" +
                            "Ví dụ: 'Tạo task hoàn thành báo cáo tháng 9'";
        } else if (userMessage.contains("hiển thị") || userMessage.contains("show") || userMessage.contains("list")) {
            intentType = "INFORMATION_RETRIEVAL";
            taskAction = "GET_TASKS";
            responseContent = "📋 Tôi có thể hiển thị danh sách task cho bạn. Bạn muốn xem task nào?\n\n" +
                            "• Tất cả task của tôi\n• Task theo trạng th��i\n• Task theo độ ưu tiên";
        } else if (userMessage.contains("thống kê") || userMessage.contains("statistics")) {
            intentType = "INFORMATION_RETRIEVAL";
            taskAction = "GET_STATISTICS";
            responseContent = "📊 Đang chuẩn bị thống kê công việc cho bạn...\n\n" +
                            "Hệ thống đang tạm thời bận, vui lòng thử lại sau ít phút! 🔄";
        } else {
            responseContent = "💬 Tôi hiểu bạn muốn trò chuyện! Tôi có thể giúp bạn:\n\n" +
                            "• Quản lý task và công việc\n• Trả lời câu hỏi\n• Cung cấp thống kê\n• Chat thân thiện\n\n" +
                            "Bạn muốn làm gì? 😊";
        }

        return UnifiedResponse.builder()
            .safe(true)
            .moderationReason("")
            .intentType(intentType)
            .confidence(0.7)
            .taskAction(taskAction)
            .responseContent(responseContent)
            .shouldUseTools(taskAction.equals("CREATE_TASK") || taskAction.equals("GET_TASKS") || taskAction.equals("GET_STATISTICS"))
            .timestamp(LocalDateTime.now())
            .source("FALLBACK_RULE_BASED")
            .build();
    }

    /**
     * Build cache key for request
     */
    private String buildCacheKey(UnifiedRequest request) {
        return request.getUserMessage().toLowerCase().trim() + "_" + request.getUserId();
    }

    // Data classes
    public static class UnifiedRequest {
        private final String userMessage;
        private final Long userId;
        private final String context;
        private final String conversationHistory;
        private final UserContextService.UserChatContext userContext;
        private List<Map<String, String>> conversationMemory; // NEW: For Gemini conversation memory

        public UnifiedRequest(String userMessage, Long userId, String context, String conversationHistory,
                            UserContextService.UserChatContext userContext) {
            this.userMessage = userMessage;
            this.userId = userId;
            this.context = context;
            this.conversationHistory = conversationHistory;
            this.userContext = userContext;
        }

        // Getters
        public String getUserMessage() { return userMessage; }
        public Long getUserId() { return userId; }
        public String getContext() { return context; }
        public String getConversationHistory() { return conversationHistory; }
        public UserContextService.UserChatContext getUserContext() { return userContext; }

        // NEW: Conversation memory setter/getter
        public List<Map<String, String>> getConversationMemory() { return conversationMemory; }
        public void setConversationMemory(List<Map<String, String>> conversationMemory) {
            this.conversationMemory = conversationMemory;
        }
    }

    public static class UnifiedResponse {
        private final boolean safe;
        private final String moderationReason;
        private final String intentType;
        private final double confidence;
        private final String taskAction;
        private final String responseContent;
        private final boolean shouldUseTools;
        private final LocalDateTime timestamp;
        private final String source;

        private UnifiedResponse(Builder builder) {
            this.safe = builder.safe;
            this.moderationReason = builder.moderationReason;
            this.intentType = builder.intentType;
            this.confidence = builder.confidence;
            this.taskAction = builder.taskAction;
            this.responseContent = builder.responseContent;
            this.shouldUseTools = builder.shouldUseTools;
            this.timestamp = builder.timestamp;
            this.source = builder.source;
        }

        // Getters
        public boolean isSafe() { return safe; }
        public String getModerationReason() { return moderationReason; }
        public String getIntentType() { return intentType; }
        public double getConfidence() { return confidence; }
        public String getTaskAction() { return taskAction; }
        public String getResponseContent() { return responseContent; }
        public boolean shouldUseTools() { return shouldUseTools; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getSource() { return source; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean safe;
            private String moderationReason;
            private String intentType;
            private double confidence;
            private String taskAction;
            private String responseContent;
            private boolean shouldUseTools;
            private LocalDateTime timestamp;
            private String source;

            public Builder safe(boolean safe) { this.safe = safe; return this; }
            public Builder moderationReason(String reason) { this.moderationReason = reason; return this; }
            public Builder intentType(String type) { this.intentType = type; return this; }
            public Builder confidence(double confidence) { this.confidence = confidence; return this; }
            public Builder taskAction(String action) { this.taskAction = action; return this; }
            public Builder responseContent(String content) { this.responseContent = content; return this; }
            public Builder shouldUseTools(boolean should) { this.shouldUseTools = should; return this; }
            public Builder timestamp(LocalDateTime time) { this.timestamp = time; return this; }
            public Builder source(String source) { this.source = source; return this; }

            public UnifiedResponse build() {
                return new UnifiedResponse(this);
            }
        }
    }
}
