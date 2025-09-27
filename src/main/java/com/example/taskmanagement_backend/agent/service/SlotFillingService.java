package com.example.taskmanagement_backend.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Slot Filling Service - Multi-turn Conversation Management
 * Manages incomplete commands and collects required information step by step
 * Ensures all required slots are filled before executing actions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotFillingService {

    private final ConversationStateService stateService;

    /**
     * Slot Filling Result
     */
    public static class SlotFillingResult {
        private boolean isComplete;
        private String nextQuestion;
        private Map<String, String> currentSlots;
        private String action;
        private double confidence;

        public SlotFillingResult(boolean isComplete, String nextQuestion, Map<String, String> slots, String action) {
            this.isComplete = isComplete;
            this.nextQuestion = nextQuestion;
            this.currentSlots = slots != null ? slots : new HashMap<>();
            this.action = action;
            this.confidence = 0.8;
        }

        // Getters and setters
        public boolean isComplete() { return isComplete; }
        public void setComplete(boolean complete) { isComplete = complete; }
        public String getNextQuestion() { return nextQuestion; }
        public void setNextQuestion(String nextQuestion) { this.nextQuestion = nextQuestion; }
        public Map<String, String> getCurrentSlots() { return currentSlots; }
        public void setCurrentSlots(Map<String, String> currentSlots) { this.currentSlots = currentSlots; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }

    /**
     * Process slot filling for incomplete commands
     */
    public SlotFillingResult processSlotFilling(String conversationId, Long userId,
                                              IntentDetectionService.IntentResult intentResult,
                                              String userMessage) {

        log.info("🎯 Processing slot filling for action: {}", intentResult.getAction());

        // Get current conversation state
        ConversationStateService.ConversationState state = stateService.getConversationState(conversationId);

        if (state == null && intentResult.isNeedsMoreInfo()) {
            // Start new slot filling session
            return startSlotFilling(conversationId, userId, intentResult);
        } else if (state != null) {
            // Continue existing slot filling
            return continueSlotFilling(conversationId, state, userMessage, intentResult);
        } else {
            // Command is complete, no slot filling needed
            return new SlotFillingResult(true, null, intentResult.getSlots(), intentResult.getAction());
        }
    }

    /**
     * Public entry point for starting slot filling
     */
    public SlotFillingResult startSlotFilling(IntentDetectionService.IntentResult intentResult,
                                            String conversationId, Long userId) {
        return startSlotFilling(conversationId, userId, intentResult);
    }

    /**
     * Start new slot filling session for incomplete commands
     */
    private SlotFillingResult startSlotFilling(String conversationId, Long userId,
                                             IntentDetectionService.IntentResult intentResult) {

        log.info("🎯 Starting slot filling session for action: {}", intentResult.getAction());

        // Create new conversation state using correct constructor
        ConversationStateService.ConversationType type = mapActionToConversationType(intentResult.getAction());
        ConversationStateService.ConversationState state = stateService.createOrUpdateState(conversationId, type, "SLOT_FILLING");

        // Store the action and user ID in collected data
        stateService.updateCollectedData(conversationId, "action", intentResult.getAction());
        stateService.updateCollectedData(conversationId, "user_id", userId);
        stateService.updateCollectedData(conversationId, "step", 1);

        // Copy existing slots to collected data
        for (Map.Entry<String, String> entry : intentResult.getSlots().entrySet()) {
            stateService.updateCollectedData(conversationId, entry.getKey(), entry.getValue());
        }

        // Determine what information we need to collect
        Map<String, String> currentSlots = getCurrentSlotsFromState(conversationId);
        String nextQuestion = determineNextQuestion(intentResult.getAction(), currentSlots);

        String waitingFor = getWaitingForSlot(intentResult.getAction(), currentSlots);
        stateService.updateCollectedData(conversationId, "waiting_for", waitingFor);

        return new SlotFillingResult(false, nextQuestion, currentSlots, intentResult.getAction());
    }

    /**
     * Continue existing slot filling session
     */
    private SlotFillingResult continueSlotFilling(String conversationId,
                                                ConversationStateService.ConversationState state,
                                                String userMessage,
                                                IntentDetectionService.IntentResult intentResult) {

        String waitingFor = (String) stateService.getCollectedData(conversationId, "waiting_for");
        log.info("🎯 Continuing slot filling session, waiting for: {}", waitingFor);

        // Extract information from user's response
        extractSlotFromUserResponse(conversationId, userMessage, intentResult);

        // Check if we have all required information
        String action = (String) stateService.getCollectedData(conversationId, "action");
        Map<String, String> currentSlots = getCurrentSlotsFromState(conversationId);

        if (hasAllRequiredSlots(action, currentSlots)) {
            // Slot filling complete
            stateService.clearState(conversationId);
            return new SlotFillingResult(true, null, currentSlots, action);
        } else {
            // Need more information
            String nextQuestion = determineNextQuestion(action, currentSlots);
            String newWaitingFor = getWaitingForSlot(action, currentSlots);

            // Update state
            Integer currentStep = (Integer) stateService.getCollectedData(conversationId, "step");
            stateService.updateCollectedData(conversationId, "step", currentStep != null ? currentStep + 1 : 2);
            stateService.updateCollectedData(conversationId, "waiting_for", newWaitingFor);

            return new SlotFillingResult(false, nextQuestion, currentSlots, action);
        }
    }

    /**
     * Extract slot information from user response
     */
    private void extractSlotFromUserResponse(String conversationId,
                                           String userMessage,
                                           IntentDetectionService.IntentResult intentResult) {

        String waitingFor = (String) stateService.getCollectedData(conversationId, "waiting_for");

        switch (waitingFor) {
            case "title":
                // Extract title from user message
                String title = extractTitle(userMessage, intentResult);
                if (title != null && !title.trim().isEmpty()) {
                    stateService.updateCollectedData(conversationId, "title", title);
                    log.info("✅ Extracted title: '{}'", title);
                }
                break;

            case "description":
                // Extract description from user message
                String description = extractDescription(userMessage, intentResult);
                if (description != null && !description.trim().isEmpty()) {
                    stateService.updateCollectedData(conversationId, "description", description);
                    log.info("✅ Extracted description: '{}'", description);
                }
                break;

            case "priority":
                // Extract priority from user message
                String priority = extractPriority(userMessage, intentResult);
                if (priority != null) {
                    stateService.updateCollectedData(conversationId, "priority", priority);
                    log.info("✅ Extracted priority: '{}'", priority);
                }
                break;

            case "deadline":
                // Extract deadline from user message
                String deadline = extractDeadline(userMessage, intentResult);
                if (deadline != null) {
                    stateService.updateCollectedData(conversationId, "deadline", deadline);
                    log.info("✅ Extracted deadline: '{}'", deadline);
                }
                break;

            default:
                log.debug("No extraction needed for waiting_for: {}", waitingFor);
        }
    }

    /**
     * Map action to conversation type
     */
    private ConversationStateService.ConversationType mapActionToConversationType(String action) {
        return switch (action) {
            case "CREATE_TASK" -> ConversationStateService.ConversationType.CREATE_TASK;
            case "UPDATE_TASK" -> ConversationStateService.ConversationType.UPDATE_TASK;
            case "DELETE_TASK" -> ConversationStateService.ConversationType.DELETE_TASK;
            case "GET_TASKS" -> ConversationStateService.ConversationType.GET_TASKS;
            default -> ConversationStateService.ConversationType.GENERAL_CHAT;
        };
    }

    /**
     * Get current slots from conversation state
     */
    private Map<String, String> getCurrentSlotsFromState(String conversationId) {
        Map<String, String> slots = new HashMap<>();

        ConversationStateService.ConversationState state = stateService.getConversationState(conversationId);
        if (state != null && state.getCollectedData() != null) {
            for (Map.Entry<String, Object> entry : state.getCollectedData().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // Only include actual slot data, not metadata
                if (!key.equals("action") && !key.equals("user_id") && !key.equals("step") && !key.equals("waiting_for")) {
                    slots.put(key, value != null ? value.toString() : null);
                }
            }
        }

        return slots;
    }

    /**
     * Determine next question to ask user
     */
    private String determineNextQuestion(String action, Map<String, String> currentSlots) {
        switch (action) {
            case "CREATE_TASK":
                if (!currentSlots.containsKey("title") || currentSlots.get("title") == null) {
                    return "📝 **What should I name this task?**\n\nPlease provide a title for the task you want to create.";
                }
                if (!currentSlots.containsKey("priority") || currentSlots.get("priority") == null) {
                    return "⚡ **What priority should this task have?**\n\n**Options:** HIGH, MEDIUM, LOW\n\nExample: \"HIGH priority\" or just \"MEDIUM\"";
                }
                if (!currentSlots.containsKey("deadline") || currentSlots.get("deadline") == null) {
                    return "📅 **When should this task be completed?**\n\n**Examples:**\n• \"tomorrow\"\n• \"next week\"\n• \"2025-10-01\"\n• \"no deadline\" (if none)";
                }
                break;

            case "UPDATE_TASK":
                if (!currentSlots.containsKey("task_id") || currentSlots.get("task_id") == null) {
                    return "🔍 **Which task would you like to update?**\n\nPlease provide the task ID or task name.";
                }
                if (!currentSlots.containsKey("field") || currentSlots.get("field") == null) {
                    return "🔧 **What would you like to update?**\n\n**Options:**\n• title\n• description\n• priority\n• deadline\n• status";
                }
                if (!currentSlots.containsKey("value") || currentSlots.get("value") == null) {
                    return "✏️ **What should be the new value?**\n\nPlease provide the new value for the field you want to update.";
                }
                break;

            case "DELETE_TASK":
                if (!currentSlots.containsKey("task_id") || currentSlots.get("task_id") == null) {
                    return "🗑️ **Which task would you like to delete?**\n\nPlease provide the task ID or task name to delete.";
                }
                break;
        }

        return "I need more information to complete this action. What would you like to provide?";
    }

    /**
     * Determine what slot we're waiting for
     */
    private String getWaitingForSlot(String action, Map<String, String> currentSlots) {
        switch (action) {
            case "CREATE_TASK":
                if (!currentSlots.containsKey("title") || currentSlots.get("title") == null) {
                    return "title";
                }
                if (!currentSlots.containsKey("priority") || currentSlots.get("priority") == null) {
                    return "priority";
                }
                if (!currentSlots.containsKey("deadline") || currentSlots.get("deadline") == null) {
                    return "deadline";
                }
                break;

            case "UPDATE_TASK":
                if (!currentSlots.containsKey("task_id") || currentSlots.get("task_id") == null) {
                    return "task_id";
                }
                if (!currentSlots.containsKey("field") || currentSlots.get("field") == null) {
                    return "field";
                }
                if (!currentSlots.containsKey("value") || currentSlots.get("value") == null) {
                    return "value";
                }
                break;

            case "DELETE_TASK":
                if (!currentSlots.containsKey("task_id") || currentSlots.get("task_id") == null) {
                    return "task_id";
                }
                break;
        }

        return "unknown";
    }

    /**
     * Check if all required slots are filled
     */
    private boolean hasAllRequiredSlots(String action, Map<String, String> slots) {
        switch (action) {
            case "CREATE_TASK":
                return slots.containsKey("title") && slots.get("title") != null && !slots.get("title").trim().isEmpty();

            case "UPDATE_TASK":
                return slots.containsKey("task_id") && slots.get("task_id") != null &&
                       slots.containsKey("field") && slots.get("field") != null &&
                       slots.containsKey("value") && slots.get("value") != null;

            case "DELETE_TASK":
                return slots.containsKey("task_id") && slots.get("task_id") != null;

            case "GET_TASKS":
                return true; // No slots required for getting tasks

            default:
                return false;
        }
    }

    /**
     * Extract title from user message
     */
    private String extractTitle(String userMessage, IntentDetectionService.IntentResult intentResult) {
        // First try to get from intent result
        String title = intentResult.getSlots().get("title");
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }

        // Extract from user message directly
        String cleanMessage = userMessage.trim();

        // If user just provided a simple response, use it as title
        if (!cleanMessage.toLowerCase().contains("tạo") &&
            !cleanMessage.toLowerCase().contains("create") &&
            !cleanMessage.toLowerCase().contains("task") &&
            cleanMessage.length() > 2 && cleanMessage.length() < 100) {
            return cleanMessage;
        }

        return null;
    }

    /**
     * Extract description from user message
     */
    private String extractDescription(String userMessage, IntentDetectionService.IntentResult intentResult) {
        String description = intentResult.getSlots().get("description");
        if (description != null && !description.trim().isEmpty()) {
            return description;
        }

        // Use user message as description if reasonable
        if (userMessage.trim().length() > 5) {
            return userMessage.trim();
        }

        return null;
    }

    /**
     * Extract priority from user message
     */
    private String extractPriority(String userMessage, IntentDetectionService.IntentResult intentResult) {
        String priority = intentResult.getSlots().get("priority");
        if (priority != null) {
            return priority;
        }

        String lowerMessage = userMessage.toLowerCase();

        // Vietnamese priority detection
        if (lowerMessage.contains("cao") || lowerMessage.contains("quan trọng") || lowerMessage.contains("high")) {
            return "HIGH";
        }
        if (lowerMessage.contains("thấp") || lowerMessage.contains("low")) {
            return "LOW";
        }
        if (lowerMessage.contains("trung bình") || lowerMessage.contains("medium")) {
            return "MEDIUM";
        }

        // Check for direct priority values
        if (lowerMessage.equals("high") || lowerMessage.equals("cao")) {
            return "HIGH";
        }
        if (lowerMessage.equals("medium") || lowerMessage.equals("trung bình")) {
            return "MEDIUM";
        }
        if (lowerMessage.equals("low") || lowerMessage.equals("thấp")) {
            return "LOW";
        }

        return null;
    }

    /**
     * Extract deadline from user message
     */
    private String extractDeadline(String userMessage, IntentDetectionService.IntentResult intentResult) {
        String deadline = intentResult.getSlots().get("deadline");
        if (deadline != null) {
            return deadline;
        }

        String lowerMessage = userMessage.toLowerCase();

        // Handle Vietnamese deadline expressions
        if (lowerMessage.contains("ngày mai") || lowerMessage.contains("tomorrow")) {
            return java.time.LocalDate.now().plusDays(1).atTime(23, 59, 59).toString();
        }
        if (lowerMessage.contains("tuần sau") || lowerMessage.contains("next week")) {
            return java.time.LocalDate.now().plusWeeks(1).atTime(23, 59, 59).toString();
        }
        if (lowerMessage.contains("tháng sau") || lowerMessage.contains("next month")) {
            return java.time.LocalDate.now().plusMonths(1).atTime(23, 59, 59).toString();
        }
        if (lowerMessage.contains("hôm nay") || lowerMessage.contains("today")) {
            return java.time.LocalDate.now().atTime(23, 59, 59).toString();
        }
        if (lowerMessage.contains("không có") || lowerMessage.contains("no deadline") || lowerMessage.contains("none")) {
            return null; // No deadline
        }

        // Try to parse ISO date format
        try {
            if (userMessage.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return userMessage + "T23:59:59";
            }
        } catch (Exception e) {
            log.debug("Could not parse date: {}", userMessage);
        }

        return null;
    }
}

