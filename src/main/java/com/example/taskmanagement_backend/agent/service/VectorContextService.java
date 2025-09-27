package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Vector-Based Context Service - AI-Powered Context Understanding
 * Uses Pinecone vector database for true contextual conversation understanding
 * Eliminates rule-based limitations by leveraging conversation embeddings
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorContextService {

    private final SessionMemoryService sessionMemoryService;
    private final EmbeddingService embeddingService;
    private final UnifiedAIService unifiedAIService;
    private final PineconeService pineconeService;

    /**
     * Context Analysis Result
     */
    public static class ContextAnalysis {
        private String currentFlow;
        private String intentType;
        private String fieldMapping;
        private String extractedValue;
        private double confidence;
        private List<ConversationTurn> relevantHistory;
        private boolean shouldContinueFlow;
        private String nextExpectedInput;
        private Map<String, Object> contextMetadata;

        // Getters and setters
        public String getCurrentFlow() { return currentFlow; }
        public void setCurrentFlow(String currentFlow) { this.currentFlow = currentFlow; }
        public String getIntentType() { return intentType; }
        public void setIntentType(String intentType) { this.intentType = intentType; }
        public String getFieldMapping() { return fieldMapping; }
        public void setFieldMapping(String fieldMapping) { this.fieldMapping = fieldMapping; }
        public String getExtractedValue() { return extractedValue; }
        public void setExtractedValue(String extractedValue) { this.extractedValue = extractedValue; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public List<ConversationTurn> getRelevantHistory() { return relevantHistory; }
        public void setRelevantHistory(List<ConversationTurn> relevantHistory) { this.relevantHistory = relevantHistory; }
        public boolean isShouldContinueFlow() { return shouldContinueFlow; }
        public void setShouldContinueFlow(boolean shouldContinueFlow) { this.shouldContinueFlow = shouldContinueFlow; }
        public String getNextExpectedInput() { return nextExpectedInput; }
        public void setNextExpectedInput(String nextExpectedInput) { this.nextExpectedInput = nextExpectedInput; }
        public Map<String, Object> getContextMetadata() { return contextMetadata; }
        public void setContextMetadata(Map<String, Object> contextMetadata) { this.contextMetadata = contextMetadata; }
    }

    /**
     * Conversation Turn for context
     */
    public static class ConversationTurn {
        private String role;
        private String content;
        private LocalDateTime timestamp;
        private String intent;
        private double similarity;

        public ConversationTurn(String role, String content, LocalDateTime timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }

        // Getters and setters
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }
        public double getSimilarity() { return similarity; }
        public void setSimilarity(double similarity) { this.similarity = similarity; }
    }

    /**
     * Analyze conversation context using vector database
     */
    public ContextAnalysis analyzeConversationContext(String userInput, String conversationId, Long userId) {
        try {
            log.info("🧠 Analyzing conversation context using vector database for: '{}'",
                    userInput.substring(0, Math.min(50, userInput.length())));

            // Step 1: Get relevant conversation history from vector database
            List<ConversationTurn> relevantHistory = getRelevantConversationHistory(userInput, conversationId, userId);

            // Step 2: Build context-aware prompt for AI analysis
            String contextPrompt = buildVectorContextPrompt(userInput, relevantHistory);

            // Step 3: Get AI analysis
            UnifiedAIService.UnifiedRequest request = new UnifiedAIService.UnifiedRequest(
                    contextPrompt, userId, "You are a conversation context analysis expert.", "", null);

            UnifiedAIService.UnifiedResponse response = unifiedAIService.processUnifiedRequest(request);

            // Step 4: Parse AI response into ContextAnalysis
            ContextAnalysis analysis = parseContextAnalysisResponse(response.getResponseContent(), relevantHistory, userInput);

            log.info("🎯 Context analysis complete: flow={}, intent={}, field={}, confidence={}",
                    analysis.getCurrentFlow(), analysis.getIntentType(),
                    analysis.getFieldMapping(), analysis.getConfidence());

            return analysis;

        } catch (Exception e) {
            log.error("❌ Context analysis failed: {}", e.getMessage(), e);
            return createFallbackContextAnalysis(userInput);
        }
    }

    /**
     * Parse AI context analysis response
     */
    private ContextAnalysis parseContextAnalysisResponse(String aiResponse, List<ConversationTurn> relevantHistory, String userInput) {
        ContextAnalysis analysis = new ContextAnalysis();
        analysis.setRelevantHistory(relevantHistory);
        analysis.setContextMetadata(new HashMap<>());

        try {
            log.debug("🔍 Parsing AI response: {}", aiResponse.substring(0, Math.min(200, aiResponse.length())));

            String jsonStr = extractJsonFromResponse(aiResponse);

            if (jsonStr == null || jsonStr.trim().isEmpty()) {
                log.warn("⚠️ No JSON found in AI response, using intelligent fallback");
                return createIntelligentFallbackContextAnalysis(userInput, relevantHistory);
            }

            // Parse JSON manually
            analysis.setCurrentFlow(extractJsonValue(jsonStr, "current_flow", "IDLE"));
            analysis.setIntentType(extractJsonValue(jsonStr, "intent_type", "SMALL_TALK"));
            analysis.setFieldMapping(extractJsonValue(jsonStr, "field_mapping", null));
            analysis.setExtractedValue(extractJsonValue(jsonStr, "extracted_value", null));

            String confidenceStr = extractJsonValue(jsonStr, "confidence", "0.5");
            try {
                analysis.setConfidence(Double.parseDouble(confidenceStr != null ? confidenceStr : "0.5"));
            } catch (NumberFormatException e) {
                analysis.setConfidence(0.5);
            }

            analysis.setShouldContinueFlow(Boolean.parseBoolean(extractJsonValue(jsonStr, "should_continue_flow", "false")));
            analysis.setNextExpectedInput(extractJsonValue(jsonStr, "next_expected_input", null));

            analysis.getContextMetadata().put("relevant_turns_count", relevantHistory.size());
            analysis.getContextMetadata().put("parsing_successful", true);

            return analysis;

        } catch (Exception e) {
            log.error("❌ Failed to parse context analysis: {}", e.getMessage());
            return createIntelligentFallbackContextAnalysis(userInput, relevantHistory);
        }
    }

    /**
     * Create intelligent fallback context analysis when AI parsing fails
     */
    private ContextAnalysis createIntelligentFallbackContextAnalysis(String userInput, List<ConversationTurn> relevantHistory) {
        ContextAnalysis fallback = new ContextAnalysis();
        fallback.setRelevantHistory(relevantHistory);
        fallback.setContextMetadata(new HashMap<>());

        log.info("🤖 Creating intelligent fallback for input: '{}'", userInput);

        // Analyze conversation history for context clues
        boolean hasRecentTaskCreation = hasRecentTaskCreationInHistory(relevantHistory);
        boolean hasRecentFieldInputs = hasRecentFieldInputsInHistory(relevantHistory);

        log.info("🔍 Context analysis: hasRecentTaskCreation={}, hasRecentFieldInputs={}",
                hasRecentTaskCreation, hasRecentFieldInputs);

        // Apply intelligent pattern matching for Vietnamese task creation
        if (isTaskCreationPattern(userInput)) {
            String extractedTitle = extractTaskTitleFromInput(userInput);

            fallback.setCurrentFlow("TASK_CREATION");
            fallback.setIntentType("TASK_CREATION");
            fallback.setFieldMapping("title");
            fallback.setExtractedValue(extractedTitle);
            fallback.setConfidence(0.85);
            fallback.setShouldContinueFlow(true);
            fallback.setNextExpectedInput("priority level or description");

            // CRITICAL: Store the task title in metadata for SlotFillingService
            fallback.getContextMetadata().put("task_title", extractedTitle);
            fallback.getContextMetadata().put("initial_task_creation", true);

            log.info("🎯 Detected task creation pattern: title='{}', stored in metadata", extractedTitle);
        }
        // Check for priority input patterns
        else if (isPriorityInputPattern(userInput)) {
            // Get task title from conversation history if available
            String historicalTitle = extractTaskTitleFromHistory(relevantHistory);

            fallback.setCurrentFlow("TASK_CREATION");
            fallback.setIntentType("FIELD_INPUT");
            fallback.setFieldMapping("priority");
            fallback.setExtractedValue(extractPriorityFromInput(userInput));
            fallback.setConfidence(0.9);
            fallback.setShouldContinueFlow(true);
            fallback.setNextExpectedInput("deadline or additional fields");

            // Store historical context
            if (!historicalTitle.isEmpty()) {
                fallback.getContextMetadata().put("task_title", historicalTitle);
                log.info("🔄 Preserved task title from history: '{}'", historicalTitle);
            }

            log.info("🎯 Detected priority input: value='{}'", fallback.getExtractedValue());
        }
        // Check for deadline/time input patterns
        else if (isDeadlineInputPattern(userInput)) {
            // Get task title from conversation history if available
            String historicalTitle = extractTaskTitleFromHistory(relevantHistory);

            fallback.setCurrentFlow("TASK_CREATION");
            fallback.setIntentType("FIELD_INPUT");
            fallback.setFieldMapping("deadline");
            fallback.setExtractedValue(extractDeadlineFromInput(userInput));
            fallback.setConfidence(0.8);
            fallback.setShouldContinueFlow(true);
            fallback.setNextExpectedInput("confirmation or additional fields");

            // Store historical context
            if (!historicalTitle.isEmpty()) {
                fallback.getContextMetadata().put("task_title", historicalTitle);
                log.info("🔄 Preserved task title from history: '{}'", historicalTitle);
            }

            log.info("🎯 Detected deadline input: value='{}'", fallback.getExtractedValue());
        }
        // Check for task creation confirmation patterns - ENHANCED with context awareness
        else if (isTaskCreationConfirmationPattern(userInput) ||
                 (hasRecentTaskCreation && hasRecentFieldInputs && isLikelyConfirmation(userInput))) {
            // Get task title from conversation history
            String historicalTitle = extractTaskTitleFromHistory(relevantHistory);

            fallback.setCurrentFlow("TASK_CREATION");
            fallback.setIntentType("CONFIRMATION");
            fallback.setFieldMapping("confirmation");
            fallback.setExtractedValue("yes");
            fallback.setConfidence(hasRecentTaskCreation && hasRecentFieldInputs ? 0.95 : 0.9);
            fallback.setShouldContinueFlow(false);
            fallback.setNextExpectedInput("task creation execution");

            // Store all historical context for final task creation
            if (!historicalTitle.isEmpty()) {
                fallback.getContextMetadata().put("task_title", historicalTitle);
            }
            String historicalPriority = extractPriorityFromHistory(relevantHistory);
            if (!historicalPriority.isEmpty()) {
                fallback.getContextMetadata().put("task_priority", historicalPriority);
            }
            String historicalDeadline = extractDeadlineFromHistory(relevantHistory);
            if (!historicalDeadline.isEmpty()) {
                fallback.getContextMetadata().put("task_deadline", historicalDeadline);
            }

            log.info("🎯 Detected task creation confirmation (context-aware) with title: '{}'", historicalTitle);
        }
        // Context-aware fallback: if we have recent task creation context, treat ambiguous input as continuation
        else if (hasRecentTaskCreation && hasRecentFieldInputs) {
            // If we're in a task creation flow but input doesn't match specific patterns,
            // assume it's additional information or confirmation
            String historicalTitle = extractTaskTitleFromHistory(relevantHistory);

            fallback.setCurrentFlow("TASK_CREATION");
            fallback.setIntentType("FIELD_INPUT");
            fallback.setFieldMapping("additional_info");
            fallback.setExtractedValue(userInput.trim());
            fallback.setConfidence(0.7);
            fallback.setShouldContinueFlow(true);
            fallback.setNextExpectedInput("confirmation or more details");

            // Preserve task title
            if (!historicalTitle.isEmpty()) {
                fallback.getContextMetadata().put("task_title", historicalTitle);
            }

            log.info("🎯 Context-aware continuation: treating as additional task info, title: '{}'", historicalTitle);
        }
        // Default fallback
        else {
            fallback.setCurrentFlow("IDLE");
            fallback.setIntentType("SMALL_TALK");
            fallback.setFieldMapping(null);
            fallback.setExtractedValue(null);
            fallback.setConfidence(0.3);
            fallback.setShouldContinueFlow(false);
            fallback.setNextExpectedInput(null);
        }

        // Add fallback metadata
        fallback.getContextMetadata().put("fallback", true);
        fallback.getContextMetadata().put("user_input_analyzed", userInput);
        fallback.getContextMetadata().put("pattern_matching", true);
        fallback.getContextMetadata().put("context_aware", hasRecentTaskCreation || hasRecentFieldInputs);

        return fallback;
    }

    // Helper methods for context analysis

    private boolean hasRecentTaskCreationInHistory(List<ConversationTurn> history) {
        return history.stream()
                .filter(turn -> "USER".equals(turn.getRole()))
                .limit(5) // Check last 5 user turns
                .anyMatch(turn -> isTaskCreationPattern(turn.getContent()));
    }

    private boolean hasRecentFieldInputsInHistory(List<ConversationTurn> history) {
        return history.stream()
                .filter(turn -> "USER".equals(turn.getRole()))
                .limit(3) // Check last 3 user turns
                .anyMatch(turn -> isPriorityInputPattern(turn.getContent()) ||
                                isDeadlineInputPattern(turn.getContent()));
    }

    private boolean isLikelyConfirmation(String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String lowerInput = input.toLowerCase().trim();

        // Broader confirmation patterns for context-aware detection
        return lowerInput.contains("luôn") || lowerInput.contains("ngay") ||
               lowerInput.contains("đi") || lowerInput.contains("rồi") ||
               lowerInput.contains("được") || lowerInput.contains("ok") ||
               lowerInput.contains("yes") || lowerInput.contains("ừ") ||
               lowerInput.length() <= 15; // Short responses often indicate agreement
    }

    // Helper methods

    private String extractUserInputFromContext(String aiResponse, List<ConversationTurn> relevantHistory) {
        String userInput = "";

        // Try to get from conversation history first
        if (!relevantHistory.isEmpty()) {
            for (ConversationTurn turn : relevantHistory) {
                if ("USER".equals(turn.getRole())) {
                    userInput = turn.getContent();
                    break;
                }
            }
        }

        // If not found, try to extract from AI response
        if (userInput.isEmpty() && aiResponse != null) {
            if (aiResponse.contains("Input:")) {
                int start = aiResponse.indexOf("Input:") + 6;
                int end = aiResponse.indexOf("\n", start);
                if (end > start) {
                    userInput = aiResponse.substring(start, end).trim().replace("\"", "");
                }
            } else if (aiResponse.contains("**Input:**")) {
                int start = aiResponse.indexOf("**Input:**") + 10;
                int end = aiResponse.indexOf("\n", start);
                if (end > start) {
                    userInput = aiResponse.substring(start, end).trim().replace("\"", "");
                }
            }
        }

        return userInput;
    }

    private boolean isTaskCreationPattern(String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String lowerInput = input.toLowerCase().trim();

        return lowerInput.startsWith("tạo task") || lowerInput.startsWith("task ") ||
               lowerInput.contains("tạo công việc") || lowerInput.contains("thêm task") ||
               (lowerInput.contains("task") && lowerInput.contains("tên là")) ||
               (lowerInput.contains("tạo") && lowerInput.contains("task"));
    }

    private String extractTaskTitleFromInput(String input) {
        if (input == null) return "";

        String cleanInput = input.trim();

        if (cleanInput.toLowerCase().contains("tên là")) {
            int start = cleanInput.toLowerCase().indexOf("tên là") + 6;
            return cleanInput.substring(start).trim();
        }

        if (cleanInput.toLowerCase().startsWith("tạo task")) {
            return cleanInput.substring(8).trim();
        }

        if (cleanInput.toLowerCase().startsWith("task ")) {
            return cleanInput.substring(5).trim();
        }

        return cleanInput.replaceAll("(?i)(tạo|task|công việc|tên là|tiêu đề)", "").trim();
    }

    private boolean isPriorityInputPattern(String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String lowerInput = input.toLowerCase().trim();

        // Check for exact priority values
        return lowerInput.equals("high") || lowerInput.equals("medium") || lowerInput.equals("low") ||
               lowerInput.equals("cao") || lowerInput.equals("thấp") || lowerInput.equals("trung bình") ||
               // Check for priority with context
               lowerInput.contains("ưu tiên") || lowerInput.contains("priority") ||
               // Check for priority patterns
               lowerInput.matches(".*\\b(high|medium|low|cao|thấp)\\b.*") ||
               // Check for Vietnamese priority phrases
               lowerInput.contains("mức độ ưu tiên") || lowerInput.contains("độ ưu tiên");
    }

    private String extractPriorityFromInput(String input) {
        if (input == null) return "";

        String lowerInput = input.toLowerCase().trim();

        // Direct priority mapping
        if (lowerInput.equals("high") || lowerInput.equals("cao")) return "HIGH";
        if (lowerInput.equals("medium") || lowerInput.equals("trung bình")) return "MEDIUM";
        if (lowerInput.equals("low") || lowerInput.equals("thấp")) return "LOW";

        // Pattern-based extraction
        if (lowerInput.contains("high") || lowerInput.contains("cao")) return "HIGH";
        if (lowerInput.contains("medium") || lowerInput.contains("trung bình")) return "MEDIUM";
        if (lowerInput.contains("low") || lowerInput.equals("thấp")) return "LOW";

        // Default fallback - return the input as-is for further processing
        return input.trim().toUpperCase();
    }

    private boolean isDeadlineInputPattern(String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String lowerInput = input.toLowerCase().trim();

        return lowerInput.contains("deadline") || lowerInput.contains("hạn chót") ||
               lowerInput.contains("ngày mai") || lowerInput.contains("hôm nay") ||
               lowerInput.contains("tuần sau") || lowerInput.contains("tomorrow") ||
               lowerInput.contains("next week") || lowerInput.contains("next month") ||
               lowerInput.contains("today") || lowerInput.contains("yesterday") ||
               lowerInput.contains("this week") || lowerInput.contains("this month") ||
               lowerInput.matches(".*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}.*");
    }

    private String extractDeadlineFromInput(String input) {
        if (input == null) return "";

        String lowerInput = input.toLowerCase();

        if (lowerInput.contains("ngày mai") || lowerInput.contains("tomorrow")) return "tomorrow";
        if (lowerInput.contains("hôm nay") || lowerInput.contains("today")) return "today";
        if (lowerInput.contains("tuần sau") || lowerInput.contains("next week")) return "next week";
        if (lowerInput.contains("next month")) return "next month";
        if (lowerInput.contains("this week")) return "this week";
        if (lowerInput.contains("this month")) return "this month";

        return input.trim();
    }

    private boolean isTaskCreationConfirmationPattern(String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String lowerInput = input.toLowerCase().trim();

        return lowerInput.equals("tạo ngay") || lowerInput.equals("tạo luôn") ||
               lowerInput.equals("tạo đi") || lowerInput.equals("đồng ý") ||
               lowerInput.equals("có") || lowerInput.equals("yes") ||
               lowerInput.equals("ok") || lowerInput.equals("create") ||
               lowerInput.contains("tạo ngay lập tức") ||
               // Handle Vietnamese negative confirmation patterns (meaning "don't wait, create now")
               lowerInput.equals("không tạo ngay") || lowerInput.equals("không chờ tạo ngay") ||
               lowerInput.equals("không cần chờ") || lowerInput.equals("không cần gì thêm") ||
               // Handle common Vietnamese confirmation phrases
               lowerInput.contains("tạo ngay đi") || lowerInput.contains("thực hiện ngay") ||
               lowerInput.equals("xong") || lowerInput.equals("được") ||
               lowerInput.equals("oke") || lowerInput.equals("okie");
    }

    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) return null;

        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }

        int startIndex = response.indexOf("{");
        int endIndex = response.lastIndexOf("}");

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }

        return null;
    }

    private String extractJsonValue(String json, String key, String defaultValue) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*\"([^\"]*)\""
            );
            java.util.regex.Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }

            java.util.regex.Pattern pattern2 = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*([^,}\\n\\r]+)"
            );
            java.util.regex.Matcher matcher2 = pattern2.matcher(json);
            if (matcher2.find()) {
                return matcher2.group(1).trim().replaceAll("\"", "");
            }
        } catch (Exception e) {
            log.debug("Could not extract JSON value for key {}: {}", key, e.getMessage());
        }
        return defaultValue;
    }

    private ContextAnalysis createFallbackContextAnalysis(String input) {
        return createIntelligentFallbackContextAnalysis(input, new ArrayList<>());
    }

    private List<ConversationTurn> getRelevantConversationHistory(String userInput, String conversationId, Long userId) {
        return getRecentConversationTurns(conversationId, userId, 20); // Increased from 5 to 20
    }

    private String buildVectorContextPrompt(String userInput, List<ConversationTurn> relevantHistory) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 🧠 CONVERSATION CONTEXT ANALYSIS\n\n");
        prompt.append("## 📋 CURRENT USER INPUT\n");
        prompt.append("**Input:** \"").append(userInput).append("\"\n\n");

        prompt.append("## 📚 FULL CONVERSATION HISTORY (Recent to Old)\n");
        if (relevantHistory.isEmpty()) {
            prompt.append("*No conversation history found*\n\n");
        } else {
            prompt.append("**Complete Context (").append(relevantHistory.size()).append(" messages):**\n");
            for (int i = 0; i < relevantHistory.size(); i++) {
                ConversationTurn turn = relevantHistory.get(i);
                prompt.append(String.format("%d. [%s]: \"%s\"\n",
                        i + 1, turn.getRole(),
                        turn.getContent()));
            }
            prompt.append("\n");

            // Extract task creation context for AI
            String taskTitle = extractTaskTitleFromHistory(relevantHistory);
            String priority = extractPriorityFromHistory(relevantHistory);
            String deadline = extractDeadlineFromHistory(relevantHistory);

            if (!taskTitle.isEmpty() || !priority.isEmpty() || !deadline.isEmpty()) {
                prompt.append("**🎯 EXTRACTED CONTEXT:**\n");
                if (!taskTitle.isEmpty()) prompt.append("- Task Title: \"").append(taskTitle).append("\"\n");
                if (!priority.isEmpty()) prompt.append("- Priority: \"").append(priority).append("\"\n");
                if (!deadline.isEmpty()) prompt.append("- Deadline: \"").append(deadline).append("\"\n");
                prompt.append("\n");
            }
        }

        prompt.append("""
                ## 🎯 ANALYSIS MISSION
                Analyze the COMPLETE conversation flow and classify the current user input based on ALL conversation history.
                
                ## 📤 REQUIRED JSON OUTPUT
                ```json
                {
                  "current_flow": "TASK_CREATION|TASK_UPDATE|CONVERSATION|IDLE|SMALL_TALK",
                  "intent_type": "TASK_CREATION|FIELD_INPUT|OFFTOPIC|SMALL_TALK|CLARIFICATION|CONFIRMATION",
                  "field_mapping": "title|description|priority|deadline|status|confirmation|null",
                  "extracted_value": "actual extracted value or null",
                  "confidence": 0.95,
                  "should_continue_flow": true/false,
                  "next_expected_input": "what to expect next or null"
                }
                ```
                
                ## 🧠 CRITICAL ANALYSIS RULES
                
                ### Context-Aware Task Creation Flow:
                - If conversation shows "tạo task [title]" followed by priority/deadline inputs, maintain TASK_CREATION flow
                - "TẠO NGAY", "tạo ngay luôn" with prior context → intent_type: "CONFIRMATION"
                - Always preserve task title from original "tạo task" command throughout the flow
                
                ### Vietnamese Language Support:
                - "tạo task báo cáo q3" → TASK_CREATION, extracted_value: "báo cáo q3"
                - "MEDIUM" in task context → FIELD_INPUT, field_mapping: "priority", extracted_value: "MEDIUM"  
                - "NEXT WEEK" in task context → FIELD_INPUT, field_mapping: "deadline", extracted_value: "next week"
                - "TẠO NGAY" with task context → CONFIRMATION, extracted_value: "yes"
                
                ## ⚠️ CRITICAL REQUIREMENTS
                - MUST analyze COMPLETE conversation history, not just current input
                - MUST maintain task creation context across multiple turns
                - Return ONLY the JSON object
                - Vietnamese task creation should have high confidence (0.8+)
                - Always extract original task title from conversation history
                """);

        return prompt.toString();
    }

    // Helper methods to extract context from conversation history

    private String extractTaskTitleFromHistory(List<ConversationTurn> history) {
        return history.stream()
                .filter(turn -> "USER".equals(turn.getRole()))
                .map(ConversationTurn::getContent)
                .filter(this::isTaskCreationPattern)
                .map(this::extractTaskTitleFromInput)
                .filter(title -> !title.isEmpty())
                .findFirst()
                .orElse("");
    }

    private String extractPriorityFromHistory(List<ConversationTurn> history) {
        return history.stream()
                .filter(turn -> "USER".equals(turn.getRole()))
                .map(ConversationTurn::getContent)
                .filter(this::isPriorityInputPattern)
                .map(this::extractPriorityFromInput)
                .filter(priority -> !priority.isEmpty())
                .findFirst()
                .orElse("");
    }

    private String extractDeadlineFromHistory(List<ConversationTurn> history) {
        return history.stream()
                .filter(turn -> "USER".equals(turn.getRole()))
                .map(ConversationTurn::getContent)
                .filter(this::isDeadlineInputPattern)
                .map(this::extractDeadlineFromInput)
                .filter(deadline -> !deadline.isEmpty())
                .findFirst()
                .orElse("");
    }

    private List<ConversationTurn> getRecentConversationTurns(String conversationId, Long userId, int maxResults) {
        try {
            List<ChatResponse> recentMessages = sessionMemoryService.getSessionMessages(conversationId, userId);

            return recentMessages.stream()
                .map(msg -> {
                    ConversationTurn turn = new ConversationTurn(
                        msg.getSenderType() != null ? msg.getSenderType() : "USER",
                        msg.getContent(),
                        msg.getTimestamp()
                    );
                    turn.setIntent(msg.getIntent());
                    return turn;
                })
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(maxResults)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.debug("Failed to get recent conversation turns: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get conversation flow state for debugging
     * ENHANCED: Include Pinecone connectivity status
     */
    public Map<String, Object> getConversationFlowState(String conversationId, Long userId) {
        try {
            // Get recent context
            List<ConversationTurn> recent = getRecentConversationTurns(conversationId, userId, 5);

            Map<String, Object> flowState = new HashMap<>();
            flowState.put("conversation_id", conversationId);
            flowState.put("recent_turns", recent.size());
            flowState.put("last_activity", recent.isEmpty() ? null : recent.get(0).getTimestamp());
            flowState.put("vector_enabled", true);
            flowState.put("context_source", "pinecone_vector_database");

            // Test Pinecone connectivity
            try {
                // Try a simple query to test Pinecone connectivity
                double[] testEmbedding = embeddingService.generateEmbedding("test");
                List<Double> testVector = Arrays.stream(testEmbedding).boxed().collect(Collectors.toList());

                pineconeService.queryVectors(testVector, 1, Map.of())
                    .timeout(java.time.Duration.ofSeconds(3))
                    .doOnError(error -> flowState.put("pinecone_status", "error"))
                    .doOnSuccess(result -> flowState.put("pinecone_status", "connected"))
                    .onErrorComplete()
                    .block();

            } catch (Exception e) {
                flowState.put("pinecone_status", "unavailable");
                flowState.put("pinecone_error", e.getMessage());
            }

            return flowState;

        } catch (Exception e) {
            log.error("Error getting conversation flow state: {}", e.getMessage());
            return Map.of(
                "error", true,
                "message", e.getMessage(),
                "conversation_id", conversationId
            );
        }
    }
}
