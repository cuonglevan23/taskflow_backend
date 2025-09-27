package com.example.taskmanagement_backend.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Intent Detection Service - ENHANCED WITH VECTOR-BASED CONTEXT UNDERSTANDING
 * Now uses Pinecone vector database for true contextual conversation analysis
 * Eliminates rule-based limitations with AI-powered context comprehension
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentDetectionService {

    private final VectorContextService vectorContextService; // NEW: Vector-based analysis
    private final UnifiedAIService unifiedAIService;

    /**
     * Enhanced Intent Classification Result with Vector Context
     */
    public static class IntentResult {
        private String intentType;
        private String action;
        private double confidence;
        private Map<String, String> slots;
        private boolean needsMoreInfo;
        private String followUpQuestion;
        private VectorContextService.ContextAnalysis contextAnalysis; // NEW: Vector context

        public IntentResult(String intentType, String action, double confidence) {
            this.intentType = intentType;
            this.action = action;
            this.confidence = confidence;
            this.slots = new HashMap<>();
            this.needsMoreInfo = false;
        }

        // Getters and setters
        public String getIntentType() { return intentType; }
        public void setIntentType(String intentType) { this.intentType = intentType; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public Map<String, String> getSlots() { return slots; }
        public void setSlots(Map<String, String> slots) { this.slots = slots; }
        public boolean isNeedsMoreInfo() { return needsMoreInfo; }
        public void setNeedsMoreInfo(boolean needsMoreInfo) { this.needsMoreInfo = needsMoreInfo; }
        public String getFollowUpQuestion() { return followUpQuestion; }
        public void setFollowUpQuestion(String followUpQuestion) { this.followUpQuestion = followUpQuestion; }
        public VectorContextService.ContextAnalysis getContextAnalysis() { return contextAnalysis; }
        public void setContextAnalysis(VectorContextService.ContextAnalysis contextAnalysis) { this.contextAnalysis = contextAnalysis; }
    }

    /**
     * ENHANCED: Vector-based intent detection using conversation context
     * Replaces rule-based approach with AI + Pinecone vector analysis
     */
    public IntentResult detectIntent(String userMessage, String conversationId, Long userId) {
        try {
            log.info("🎯 Vector-based intent detection for: '{}'", userMessage);

            // Step 1: Get vector-based context analysis
            VectorContextService.ContextAnalysis contextAnalysis =
                vectorContextService.analyzeConversationContext(userMessage, conversationId, userId);

            // Step 2: Convert context analysis to IntentResult
            IntentResult result = convertContextAnalysisToIntent(contextAnalysis, userMessage);

            // Step 3: Store context analysis in result for debugging
            result.setContextAnalysis(contextAnalysis);

            log.info("✅ Vector intent detected: type={}, action={}, flow={}, confidence={}",
                result.getIntentType(), result.getAction(),
                contextAnalysis.getCurrentFlow(), result.getConfidence());

            return result;

        } catch (Exception e) {
            log.error("❌ Vector intent detection failed, using fallback: {}", e.getMessage());
            return fallbackIntentDetection(userMessage);
        }
    }

    /**
     * Convert VectorContextService analysis to IntentResult
     * Maps AI context analysis to our intent classification system
     */
    private IntentResult convertContextAnalysisToIntent(VectorContextService.ContextAnalysis contextAnalysis,
                                                       String userMessage) {

        String intentType = mapContextToIntentType(contextAnalysis);
        String action = mapContextToAction(contextAnalysis);

        IntentResult result = new IntentResult(intentType, action, contextAnalysis.getConfidence());

        // Extract slots from context analysis
        Map<String, String> slots = new HashMap<>();

        // Primary field mapping from context analysis
        if (contextAnalysis.getFieldMapping() != null && contextAnalysis.getExtractedValue() != null) {
            slots.put(contextAnalysis.getFieldMapping(), contextAnalysis.getExtractedValue());
        }

        // CRITICAL FIX: Extract task title and other context from metadata
        Map<String, Object> contextMetadata = contextAnalysis.getContextMetadata();
        if (contextMetadata != null) {
            // Extract task title from metadata
            String taskTitle = (String) contextMetadata.get("task_title");
            if (taskTitle != null && !taskTitle.trim().isEmpty()) {
                slots.put("title", taskTitle);
                log.info("🎯 Extracted task title from context metadata: '{}'", taskTitle);
            }

            // Extract other task-related context
            String taskPriority = (String) contextMetadata.get("task_priority");
            if (taskPriority != null && !taskPriority.trim().isEmpty()) {
                slots.put("priority", taskPriority);
                log.info("🎯 Extracted task priority from context metadata: '{}'", taskPriority);
            }

            String taskDeadline = (String) contextMetadata.get("task_deadline");
            if (taskDeadline != null && !taskDeadline.trim().isEmpty()) {
                slots.put("deadline", taskDeadline);
                log.info("🎯 Extracted task deadline from context metadata: '{}'", taskDeadline);
            }

            // Log if this is initial task creation
            Boolean initialTaskCreation = (Boolean) contextMetadata.get("initial_task_creation");
            if (Boolean.TRUE.equals(initialTaskCreation)) {
                log.info("🎯 Initial task creation detected with complete context");
            }
        }

        // Add flow state information
        slots.put("current_flow", contextAnalysis.getCurrentFlow());
        slots.put("should_continue_flow", String.valueOf(contextAnalysis.isShouldContinueFlow()));

        result.setSlots(slots);

        // Determine if more info is needed
        result.setNeedsMoreInfo(determineIfMoreInfoNeeded(contextAnalysis));

        // Generate follow-up question based on context
        if (result.isNeedsMoreInfo()) {
            result.setFollowUpQuestion(generateContextualFollowUp(contextAnalysis, userMessage));
        }

        return result;
    }

    /**
     * Map vector context analysis to our intent types
     */
    private String mapContextToIntentType(VectorContextService.ContextAnalysis contextAnalysis) {
        return switch (contextAnalysis.getIntentType()) {
            case "TASK_CREATION" -> "COMMAND";
            case "FIELD_INPUT" -> {
                // If we're in a flow and providing field input, it's still a command
                if ("TASK_CREATION".equals(contextAnalysis.getCurrentFlow()) ||
                    "TASK_UPDATE".equals(contextAnalysis.getCurrentFlow())) {
                    yield "COMMAND";
                } else {
                    yield "QUERY";
                }
            }
            case "OFFTOPIC" -> "CHITCHAT";
            case "SMALL_TALK" -> "CHITCHAT";
            case "CLARIFICATION" -> "QUERY";
            case "CONFIRMATION" -> "COMMAND";
            default -> "QUERY";
        };
    }

    /**
     * Map vector context analysis to our action types
     */
    private String mapContextToAction(VectorContextService.ContextAnalysis contextAnalysis) {
        String currentFlow = contextAnalysis.getCurrentFlow();
        String intentType = contextAnalysis.getIntentType();

        // Handle multi-turn flows
        if ("TASK_CREATION".equals(currentFlow)) {
            if ("FIELD_INPUT".equals(intentType) || "TASK_CREATION".equals(intentType)) {
                return "CREATE_TASK";
            }
        } else if ("TASK_UPDATE".equals(currentFlow)) {
            return "UPDATE_TASK";
        }

        // Handle single-turn commands
        return switch (intentType) {
            case "TASK_CREATION" -> "CREATE_TASK";
            case "CONFIRMATION" -> {
                // Check what we're confirming based on flow
                if ("TASK_CREATION".equals(currentFlow)) yield "CREATE_TASK";
                if ("TASK_UPDATE".equals(currentFlow)) yield "UPDATE_TASK";
                yield "NONE";
            }
            default -> "NONE";
        };
    }

    /**
     * Determine if more information is needed based on context
     */
    private boolean determineIfMoreInfoNeeded(VectorContextService.ContextAnalysis contextAnalysis) {
        // If we're in a multi-turn flow and expecting more input
        if (contextAnalysis.isShouldContinueFlow() && contextAnalysis.getNextExpectedInput() != null) {
            return true;
        }

        // If we detected a field input but no specific field mapping
        if ("FIELD_INPUT".equals(contextAnalysis.getIntentType()) &&
            contextAnalysis.getFieldMapping() == null) {
            return true;
        }

        // If we're starting task creation but missing required fields
        if ("TASK_CREATION".equals(contextAnalysis.getIntentType()) &&
            (contextAnalysis.getExtractedValue() == null || contextAnalysis.getExtractedValue().trim().isEmpty())) {
            return true;
        }

        return false;
    }

    /**
     * Generate contextual follow-up questions based on vector analysis
     */
    private String generateContextualFollowUp(VectorContextService.ContextAnalysis contextAnalysis,
                                             String userMessage) {

        String currentFlow = contextAnalysis.getCurrentFlow();
        String nextExpected = contextAnalysis.getNextExpectedInput();

        if ("TASK_CREATION".equals(currentFlow)) {
            if (nextExpected != null) {
                return switch (nextExpected) {
                    case "priority or deadline" ->
                        "🎯 **What priority should this task have?**\n\n" +
                        "Options: **HIGH** (urgent), **MEDIUM** (normal), **LOW** (when you have time)\n" +
                        "Or you can specify a deadline instead.";

                    case "priority level" ->
                        "⚡ **What priority level for this task?**\n\n" +
                        "• **HIGH** - Urgent/important\n" +
                        "• **MEDIUM** - Normal priority\n" +
                        "• **LOW** - When you have time";

                    case "deadline" ->
                        "📅 **When should this task be completed?**\n\n" +
                        "Examples: 'tomorrow', 'next week', 'October 15th', or 'no deadline'";

                    default ->
                        "🤔 **I need more information to continue creating this task.**\n\n" +
                        "What would you like to specify next?";
                };
            }

            // If no specific next expected input, ask for missing required fields
            if (contextAnalysis.getExtractedValue() == null) {
                return "📝 **What should I name this task?**\n\n" +
                       "Please provide a clear title for your task.";
            }
        }

        // Generic fallback
        return "🤔 **I need more information to help you.**\n\n" +
               "Could you please provide more details?";
    }

    /**
     * Fallback intent detection (simplified, for emergencies only)
     */
    private IntentResult fallbackIntentDetection(String userMessage) {
        log.warn("⚠️ Using fallback intent detection for: {}", userMessage);

        String lowerMessage = userMessage.toLowerCase().trim();

        // Very basic fallback logic
        if (lowerMessage.contains("tạo task") || lowerMessage.contains("create task")) {
            IntentResult result = new IntentResult("COMMAND", "CREATE_TASK", 0.6);
            result.setNeedsMoreInfo(true);
            result.setFollowUpQuestion("What would you like to name this task?");
            return result;
        } else if (lowerMessage.contains("?") || lowerMessage.contains("có thể") || lowerMessage.contains("can you")) {
            return new IntentResult("QUERY", "NONE", 0.6);
        } else {
            return new IntentResult("CHITCHAT", "NONE", 0.5);
        }
    }

    /**
     * Get debug information about intent detection
     */
    public Map<String, Object> getIntentDetectionDebugInfo(String conversationId, Long userId) {
        Map<String, Object> debugInfo = new HashMap<>();

        try {
            // Get vector context service debug info
            Map<String, Object> flowState = vectorContextService.getConversationFlowState(conversationId, userId);
            debugInfo.put("vector_context", flowState);
            debugInfo.put("detection_method", "vector_based_ai");
            debugInfo.put("pinecone_enabled", true);
            debugInfo.put("rule_based_fallback", true);

        } catch (Exception e) {
            debugInfo.put("error", e.getMessage());
        }

        return debugInfo;
    }
}
