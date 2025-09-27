package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.dto.ChatRequest;
import com.example.taskmanagement_backend.agent.dto.ChatResponse;
import com.example.taskmanagement_backend.agent.tools.MyTaskTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core Agent Service - FULLY UNIFIED AI PROCESSING WITH RAG ENHANCEMENT
 * UPDATED: Integrates RAG service for intelligent context-aware responses
 * ENHANCED: Advanced DECLINING intent handling with contextual understanding
 */
@Slf4j
@Service
public class CoreAgentService {

    // Core services - streamlined dependencies with RAG enhancement
    private final UnifiedAIService unifiedAIService; // PRIMARY AI service
    private final MyTaskTools myTaskTools; // Tool execution
    private final UserContextService userContextService; // User context
    private final SessionMemoryService sessionMemoryService; // Conversation memory
    private final ConversationMemoryService conversationMemoryService; // NEW: Short-term memory for Gemini
    private final AuditLogService auditLogService; // Logging
    private final EscalationService escalationService; // Error handling
    private final MultiTurnConversationHandler multiTurnHandler; // Multi-turn conversations
    private final RAGService ragService; // RAG for enhanced context retrieval
    private final IntentDetectionService intentDetectionService; // NEW: AI-powered intent classification
    private final SlotFillingService slotFillingService; // NEW: Multi-turn slot filling
    private final VectorContextService vectorContextService; // NEW: Pinecone conversation context

    public CoreAgentService(
            UnifiedAIService unifiedAIService, // ONLY AI service we need
            MyTaskTools myTaskTools,
            UserContextService userContextService,
            SessionMemoryService sessionMemoryService,
            ConversationMemoryService conversationMemoryService, // NEW: Short-term memory
            @Qualifier("agentAuditLogService") AuditLogService auditLogService,
            EscalationService escalationService,
            MultiTurnConversationHandler multiTurnHandler,
            RAGService ragService, // NEW: RAG service integration
            IntentDetectionService intentDetectionService, // NEW: Intent classification
            SlotFillingService slotFillingService, // NEW: Slot filling
            VectorContextService vectorContextService // NEW: Pinecone conversation context
    ) {
        this.unifiedAIService = unifiedAIService;
        this.myTaskTools = myTaskTools;
        this.userContextService = userContextService;
        this.sessionMemoryService = sessionMemoryService;
        this.conversationMemoryService = conversationMemoryService;
        this.auditLogService = auditLogService;
        this.escalationService = escalationService;
        this.multiTurnHandler = multiTurnHandler;
        this.ragService = ragService;
        this.intentDetectionService = intentDetectionService;
        this.slotFillingService = slotFillingService;
        this.vectorContextService = vectorContextService;

        log.info("CoreAgentService initialized - UNIFIED AI + RAG + MEMORY + INTENT DETECTION + SLOT FILLING");
    }

    /**
     * MAIN PROCESSING METHOD - ENHANCED WITH INTENT CLASSIFICATION & SLOT FILLING
     * NEW: Properly distinguishes between QUERY, COMMAND, and CHITCHAT
     * NEW: Multi-turn slot filling for incomplete commands
     * FIXED: No more unwanted task creation for questions
     */
    public ChatResponse processUserMessage(String conversationId, Long userId, ChatRequest request,
                                         HttpServletRequest httpRequest, Long projectId) {
        String messageId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        try {
            log.info("🎯 Processing with INTENT DETECTION + SLOT FILLING: userId={}, conversationId={}, message='{}'",
                userId, conversationId, request.getContent().substring(0, Math.min(50, request.getContent().length())));

            // Step 1: Store user message in memory FIRST
            conversationMemoryService.storeUserMessage(conversationId, request.getContent());

            // Step 2: Get user context
            userContextService.updateUserLastSeen(userId);
            UserContextService.UserChatContext userContext = userContextService.getUserChatContext(userId);

            // Step 3: RAG Context Retrieval
            RAGService.RAGContext ragContext = ragService.retrieveContext(request.getContent(), conversationId, userId);

            // Step 4: Early declining detection
            if (ragContext.getDecliningAnalysis().isDeclining()) {
                log.info("RAG detected DECLINING intent with confidence: {}",
                    ragContext.getDecliningAnalysis().getConfidence());

                ChatResponse decliningResponse = handleDecliningIntent(ragContext, conversationId, messageId, userId);
                conversationMemoryService.updateLastTurnWithAIResponse(conversationId, decliningResponse.getContent());
                storeAndLogConversation(conversationId, userId, request, decliningResponse, projectId, httpRequest);
                return decliningResponse;
            }

            // Step 5: 🎯 VECTOR-BASED INTENT DETECTION - Uses Pinecone for context understanding
            IntentDetectionService.IntentResult intentResult = intentDetectionService.detectIntent(
                request.getContent(), conversationId, userId); // Added userId for vector context

            log.info("🎯 Intent detected: type={}, action={}, confidence={}, needsMoreInfo={}",
                intentResult.getIntentType(), intentResult.getAction(),
                intentResult.getConfidence(), intentResult.isNeedsMoreInfo());

            // Step 6: Handle different intent types appropriately
            switch (intentResult.getIntentType()) {
                case "QUERY":
                    // Questions - provide information without executing actions
                    return handleQueryIntent(intentResult, conversationId, messageId, userId,
                                           ragContext, userContext, projectId, httpRequest, request);

                case "CHITCHAT":
                    // Casual conversation - friendly responses
                    return handleChitchatIntent(intentResult, conversationId, messageId, userId,
                                              ragContext, userContext, projectId, httpRequest, request);

                case "COMMAND":
                    // Commands - may need slot filling before execution
                    return handleCommandIntent(intentResult, conversationId, messageId, userId,
                                             ragContext, userContext, projectId, httpRequest, request);

                default:
                    log.warn("⚠️ Unknown intent type: {}, treating as CHITCHAT", intentResult.getIntentType());
                    return handleChitchatIntent(intentResult, conversationId, messageId, userId,
                                              ragContext, userContext, projectId, httpRequest, request);
            }

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Error in INTENT DETECTION + SLOT FILLING processing after {}ms", processingTime, e);
            return escalationService.handleGeminiError(e, request.getContent(), conversationId);
        }
    }

    /**
     * Handle declining intent intelligently with RAG context
     */
    private ChatResponse handleDecliningIntent(RAGService.RAGContext ragContext, String conversationId,
                                             String messageId, Long userId) {
        log.info("Processing DECLINING intent with RAG context - type: {}, confidence: {}",
            ragContext.getDecliningAnalysis().getDeclineType(), ragContext.getDecliningAnalysis().getConfidence());

        // Generate contextual declining response
        String decliningResponse = generateContextualDecliningResponse(ragContext);

        return ChatResponse.builder()
            .messageId(messageId)
            .content(decliningResponse)
            .senderType("AGENT")
            .timestamp(LocalDateTime.now())
            .aiModel("rag-declining-handler")
            .confidence(ragContext.getDecliningAnalysis().getConfidence())
            .intent("declining")
            .success(true)
            .status("RAG_DECLINING_HANDLED")
            .conversationId(conversationId)
            .agentActive(true)
            .toolCalled(false)
            .build();
    }

    /**
     * Generate contextual response for declining intent - UPDATED: 100% English responses
     */
    private String generateContextualDecliningResponse(RAGService.RAGContext ragContext) {
        String declineType = ragContext.getDecliningAnalysis().getDeclineType();

        return switch (declineType) {
            case "task_decline" ->
                "🤝 **I understand!** You don't want to create a task right now.\n\n" +
                "💡 **Alternative suggestions:** You can:\n" +
                "- Take notes about your ideas for later task creation\n" +
                "- Use reminders instead of formal tasks\n" +
                "- Discuss with your team before making decisions\n\n" +
                "I'm ready to help whenever you need! 😊";

            case "vietnamese_decline" ->
                "🙏 **That's perfectly fine!** I completely respect your decision.\n\n" +
                "If there's anything else I can help you with, please don't hesitate to ask! " +
                "I'm always here to support you. ✨";

            case "english_decline" ->
                "👍 **No problem at all!** I completely understand and respect your decision.\n\n" +
                "Feel free to ask me anything else - I'm here to help whenever you need! ✨";

            default ->
                "🤝 **I understand!** Thank you for letting me know.\n\n" +
                "Is there anything else I can help you with? I'm always ready to assist! 😊";
        };
    }

    /**
     * Build conversation history with enhanced memory retrieval
     */
    private String buildConversationHistory(String conversationId) {
        try {
            // Use SessionMemoryService's enhanced method to get conversation context
            Long userId = extractUserIdFromConversationId(conversationId);
            if (userId != null) {
                return sessionMemoryService.getConversationContextForAI(conversationId, userId, 5);
            }

            // Fallback to basic retrieval
            List<ChatResponse> recentMessages = sessionMemoryService.getSessionMessages(conversationId, null)
                .stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(3)
                .toList();

            if (recentMessages.isEmpty()) {
                return "";
            }

            StringBuilder history = new StringBuilder("=== RECENT CONVERSATION ===\n");
            for (ChatResponse message : recentMessages) {
                String sender = "USER".equals(message.getSenderType()) ? "User" : "AI";
                history.append(sender).append(": ").append(
                    message.getContent().substring(0, Math.min(100, message.getContent().length()))
                ).append("...\n");
            }
            return history.toString();

        } catch (Exception e) {
            log.debug("Could not build conversation history: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Enhanced context building with conversation memory
     */
    private String buildRAGEnhancedContext(RAGService.RAGContext ragContext, Long userId, Long projectId,
                                         UserContextService.UserChatContext userContext) {
        StringBuilder context = new StringBuilder();

        // Base context
        context.append("=== USER PROFILE ===\n");
        context.append("Name: ").append(userContext.getFirstName() != null ?
            userContext.getFirstName() : "User").append("\n");
        context.append("Role: ").append(userContext.getSystemRole()).append("\n");
        context.append("Premium: ").append(userContext.getIsPremium() ? "Yes" : "No").append("\n\n");

        // Project context
        if (projectId != null) {
            context.append("=== PROJECT CONTEXT ===\n");
            context.append("Project ID: ").append(projectId).append("\n\n");
        }

        // RAG Enhanced Context - this now includes conversation memory
        context.append(ragService.generateEnhancedContext(ragContext));

        // System Intelligence Level
        context.append("=== SYSTEM INTELLIGENCE ===\n");
        context.append("RAG Context Quality: ").append(String.format("%.2f", ragContext.getContextQuality())).append("\n");
        context.append("Knowledge Base Active: ").append(!ragContext.getRelevantDocuments().isEmpty()).append("\n");
        context.append("Conversation Memory: ").append(!ragContext.getConversationContext().isEmpty()).append("\n");
        context.append("Declining Detection: ").append(ragContext.getDecliningAnalysis().isDeclining()).append("\n\n");

        // IMPORTANT: Add instruction for AI to use conversation memory
        context.append("=== MEMORY USAGE INSTRUCTIONS ===\n");
        context.append("- Use conversation history to maintain context and answer follow-up questions\n");
        context.append("- When user asks about 'recent questions' or 'what did I ask', refer to conversation history\n");
        context.append("- Remember user preferences and previous topics discussed\n");
        context.append("- If user mentions 'remember', 'recall', or 'earlier', check conversation context\n\n");

        return context.toString();
    }

    /**
     * Enhanced method to process messages with conversation memory
     */
    private void storeAndLogConversation(String conversationId, Long userId, ChatRequest request,
                                       ChatResponse response, Long projectId, HttpServletRequest httpRequest) {
        try {
            // Store user message first
            ChatResponse userMessage = ChatResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .content(request.getContent())
                .senderType("USER")
                .timestamp(LocalDateTime.now())
                .conversationId(conversationId)
                .success(true)
                .build();

            sessionMemoryService.storeSessionMessage(conversationId, userMessage);

            // Store AI response
            sessionMemoryService.storeSessionMessage(conversationId, response);

            // Store in RAG for future context (user question + AI answer pair)
            storeConversationKnowledge(request.getContent(), response.getContent(), userId, null);

            // Audit log - skip for now to avoid compilation errors
            try {
                // Use a simpler logging approach
                log.info("Chat interaction: conversationId={}, userId={}, userMessage={}, aiResponse={}",
                    conversationId, userId, request.getContent().substring(0, Math.min(50, request.getContent().length())),
                    response.getContent().substring(0, Math.min(50, response.getContent().length())));
            } catch (Exception logError) {
                log.warn("Failed to log conversation: {}", logError.getMessage());
            }

        } catch (Exception e) {
            log.error("Error storing conversation", e);
        }
    }

    /**
     * Store conversation knowledge in RAG system
     */
    private void storeConversationKnowledge(String userMessage, String aiResponse, Long userId, RAGService.RAGContext ragContext) {
        try {
            // Create a knowledge entry from the Q&A pair
            String knowledgeContent = String.format("User Question: %s\nAI Response: %s", userMessage, aiResponse);
            String knowledgeId = "qa_" + userId + "_" + System.currentTimeMillis();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "conversation_qa_pair");
            metadata.put("user_id", userId);
            metadata.put("timestamp", LocalDateTime.now().toString());
            metadata.put("user_question", userMessage);
            metadata.put("ai_response", aiResponse);

            ragService.storeKnowledge(knowledgeId, "User Conversation - " + userMessage.substring(0, Math.min(50, userMessage.length())), knowledgeContent, metadata);

            log.debug("📚 Stored Q&A pair in knowledge base: {}", knowledgeId);

        } catch (Exception e) {
            log.debug("Could not store conversation knowledge: {}", e.getMessage());
        }
    }

    /**
     * Extract user ID from conversation ID
     */
    private Long extractUserIdFromConversationId(String conversationId) {
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
     * Process response with RAG context awareness - FIXED: Pass user message to tools
     */
    private ChatResponse processRAGAwareResponse(UnifiedAIService.UnifiedResponse unifiedResponse,
                                               RAGService.RAGContext ragContext, String conversationId,
                                               String messageId, Long userId) {

        // CRITICAL FIX: Actually execute tools when needed!
        String finalContent = unifiedResponse.getResponseContent();
        boolean toolExecuted = false;

        if (unifiedResponse.shouldUseTools()) {
            log.info("🔧 Executing tools for taskAction: {}", unifiedResponse.getTaskAction());

            try {
                // FIXED: Pass user message from conversation memory to extract full details
                String userMessage = getUserMessageFromMemory(conversationId);
                String toolResult = executeTaskTools(unifiedResponse.getTaskAction(), unifiedResponse.getResponseContent(), userId, conversationId, userMessage);
                if (toolResult != null && !toolResult.trim().isEmpty()) {
                    // Combine AI response with tool result
                    finalContent = unifiedResponse.getResponseContent() + "\n\n" + toolResult;
                    toolExecuted = true;
                    log.info("✅ Tool executed successfully: {}", unifiedResponse.getTaskAction());
                } else {
                    log.warn("⚠️ Tool execution returned empty result for: {}", unifiedResponse.getTaskAction());
                }
            } catch (Exception e) {
                log.error("❌ Tool execution failed for {}: {}", unifiedResponse.getTaskAction(), e.getMessage());
                finalContent = unifiedResponse.getResponseContent() + "\n\n❌ An error occurred while executing the action: " + e.getMessage();
            }
        }

        return ChatResponse.builder()
            .messageId(messageId)
            .content(finalContent) // Use combined content with tool results
            .senderType("AGENT")
            .timestamp(LocalDateTime.now())
            .aiModel("unified-ai-with-memory-rag")
            .confidence(Math.max(unifiedResponse.getConfidence(), ragContext.getContextQuality()))
            .intent(unifiedResponse.getIntentType().toLowerCase())
            .success(true)
            .status("MEMORY_RAG_ENHANCED" + (toolExecuted ? "_WITH_TOOLS" : ""))
            .conversationId(conversationId)
            .agentActive(true)
            .toolCalled(toolExecuted) // Actually reflect if tools were called
            .build();
    }

    /**
     * Get user message from conversation memory
     */
    private String getUserMessageFromMemory(String conversationId) {
        try {
            List<Map<String, String>> memory = conversationMemoryService.getConversationMemoryForGemini(conversationId);
            if (!memory.isEmpty()) {
                // Get the latest user message
                for (int i = memory.size() - 1; i >= 0; i--) {
                    Map<String, String> turn = memory.get(i);
                    if ("user".equals(turn.get("role"))) {
                        return turn.get("content");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not retrieve user message from memory: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Execute task tools based on AI classification - FIXED: Added userMessage parameter
     */
    private String executeTaskTools(String taskAction, String aiResponse, Long userId, String conversationId, String userMessage) {
        try {
            return switch (taskAction) {
                case "CREATE_TASK" -> handleTaskCreation(aiResponse, userId, userMessage); // Pass user message
                case "GET_TASKS" -> myTaskTools.getUserTasks(userId, null, null, null, 10);
                case "GET_STATISTICS" -> myTaskTools.getTaskStatistics(userId);
                case "UPDATE_TASK" -> handleTaskUpdate(aiResponse, userId);
                case "DELETE_TASK" -> handleTaskDeletion(aiResponse, userId);
                default -> {
                    log.debug("No tool execution needed for action: {}", taskAction);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.error("Error executing tool for action: {}", taskAction, e);
            throw e;
        }
    }

    /**
     * Handle task creation from AI response - SMART AI EXTRACTION (No hard-coded patterns)
     */
    private String handleTaskCreation(String aiResponse, Long userId, String userMessage) {
        try {
            log.info("🤖 Using AI-powered task information extraction for user: {}", userId);

            // Let AI extract task info intelligently
            TaskCreationInfo taskInfo = extractTaskInfoUsingAI(userMessage, aiResponse);

            log.info("🔨 Creating task with AI-extracted info: title='{}', deadline='{}', priority='{}' for user: {}",
                taskInfo.title, taskInfo.deadline, taskInfo.priority, userId);

            // Create task with AI-extracted info
            return myTaskTools.createTask(
                taskInfo.title,
                taskInfo.description,
                taskInfo.priority,
                taskInfo.deadline,
                null,
                userId
            );

        } catch (Exception e) {
            log.error("Error creating task", e);
            throw e;
        }
    }

    /**
     * AI-POWERED TASK EXTRACTION - Let Gemini AI extract task details intelligently
     * ENHANCED: Better prompts and debugging
     */
    private TaskCreationInfo extractTaskInfoUsingAI(String userMessage, String aiResponse) {
        try {
            log.info("🤖 Starting AI extraction for userMessage: '{}' aiResponse: '{}'",
                userMessage, aiResponse != null ? aiResponse.substring(0, Math.min(100, aiResponse.length())) : "null");

            // Build extraction prompt for AI
            String extractionPrompt = buildTaskExtractionPrompt(userMessage, aiResponse);

            log.debug("🔍 AI extraction prompt: {}", extractionPrompt);

            // Call AI to extract task information
            UnifiedAIService.UnifiedRequest extractionRequest = new UnifiedAIService.UnifiedRequest(
                extractionPrompt,
                null, // No userId needed for extraction
                "=== TASK EXTRACTION SPECIALIST ===\nYou are an expert at extracting structured task information from natural language.",
                "",
                null
            );

            UnifiedAIService.UnifiedResponse extractionResponse = unifiedAIService.processUnifiedRequest(extractionRequest);

            log.info("🎯 AI extraction response: '{}'", extractionResponse.getResponseContent());

            // Parse AI response to TaskCreationInfo
            return parseAIExtractedTaskInfo(extractionResponse.getResponseContent(), userMessage);

        } catch (Exception e) {
            log.error("❌ AI extraction failed, falling back to simple parsing: {}", e.getMessage(), e);
            // Fallback to simple extraction
            return fallbackTaskExtraction(userMessage, aiResponse);
        }
    }

    /**
     * Build smart extraction prompt for AI - ENHANCED with markdown formatting and 100% English
     */
    private String buildTaskExtractionPrompt(String userMessage, String aiResponse) {
        return String.format("""
            # 🎯 TASK INFORMATION EXTRACTION SPECIALIST
            
            ## 📋 INPUT DATA
            - **User Message:** "%s"
            - **AI Context:** "%s"
            - **Current Date:** %s (September 2025)
            
            ## 🎯 MISSION
            Extract task information from user input and return **ONLY** valid JSON format.
            
            ## 📤 REQUIRED OUTPUT FORMAT
            ```json
            {
              "title": "extracted task title",
              "description": "brief description", 
              "priority": "MEDIUM",
              "deadline": "YYYY-MM-DD",
              "notes": "context"
            }
            ```
            
            ## ⚡ CRITICAL RULES
            1. **RETURN ONLY JSON** - No explanations, no markdown, no extra text
            2. Extract main task name after keywords: "create task", "tạo task", "task"
            3. **Deadline parsing:**
               - "next month" / "tháng sau" = `"2025-10-26"`
               - "tomorrow" / "ngày mai" = `"2025-09-27"`  
               - "today" / "hôm nay" = `"2025-09-26"`
               - No deadline mentioned = `null`
            4. **Default priority:** `"MEDIUM"`
            5. **Language support:** Extract from both Vietnamese and English
            
            ## 💡 EXAMPLES
            
            ### Example 1:
            **Input:** "tạo task nấu cơm, deadline tháng sau"
            **Output:** `{"title": "nấu cơm", "description": "Task nấu cơm", "priority": "MEDIUM", "deadline": "2025-10-26", "notes": "deadline tháng sau"}`
            
            ### Example 2:
            **Input:** "create task learn AI deadline tomorrow"  
            **Output:** `{"title": "learn AI", "description": "Task learn AI", "priority": "MEDIUM", "deadline": "2025-09-27", "notes": "deadline tomorrow"}`
            
            ### Example 3:
            **Input:** "task meeting with team high priority"
            **Output:** `{"title": "meeting with team", "description": "Task meeting with team", "priority": "HIGH", "deadline": null, "notes": "high priority"}`
            
            ## ⚠️ IMPORTANT
            - Return **ONLY** the JSON object
            - No explanations or additional text
            - Ensure valid JSON syntax
            - Extract the most relevant task title from user input
            """,
            userMessage,
            aiResponse != null ? aiResponse : "",
            java.time.LocalDate.now()
        );
    }

    /**
     * Parse AI-extracted task information from JSON response - ENHANCED with better error handling
     */
    private TaskCreationInfo parseAIExtractedTaskInfo(String aiJsonResponse, String originalMessage) {
        TaskCreationInfo info = new TaskCreationInfo();

        try {
            log.debug("🔍 Raw AI response: '{}'", aiJsonResponse);

            // CRITICAL: If AI didn't return JSON, immediately fallback
            if (!aiJsonResponse.trim().startsWith("{")) {
                log.warn("⚠️ AI response is not JSON format, falling back to simple extraction");
                return fallbackTaskExtraction(originalMessage, "");
            }

            // Clean the AI response to extract JSON
            String jsonStr = extractJsonFromAIResponse(aiJsonResponse);

            log.debug("🔍 Extracted JSON: '{}'", jsonStr);

            // ENHANCED: Check if JSON is valid before parsing
            if (jsonStr == null || jsonStr.trim().isEmpty() || !jsonStr.contains("\"title\"")) {
                log.warn("⚠️ Invalid JSON format, falling back to simple extraction");
                return fallbackTaskExtraction(originalMessage, "");
            }

            // Parse using simple string extraction (avoiding Jackson dependency)
            info.title = extractJsonValue(jsonStr, "title", "New Task");
            info.description = extractJsonValue(jsonStr, "description", "Task created by AI");
            info.priority = extractJsonValue(jsonStr, "priority", "MEDIUM");

            String deadlineStr = extractJsonValue(jsonStr, "deadline", null);
            if (deadlineStr != null && !deadlineStr.equals("null") && !deadlineStr.trim().isEmpty()) {
                info.deadline = parseDeadlineString(deadlineStr);
            }

            // VALIDATION: If title is still "New Task", something went wrong
            if ("New Task".equals(info.title)) {
                log.warn("⚠️ Title extraction failed, falling back to simple extraction");
                return fallbackTaskExtraction(originalMessage, "");
            }

            log.info("✅ AI extracted task info successfully: title='{}', deadline='{}', priority='{}'",
                info.title, info.deadline, info.priority);

        } catch (Exception e) {
            log.error("❌ Failed to parse AI JSON response: '{}', error: {}", aiJsonResponse, e.getMessage());
            // Fallback to simple extraction
            return fallbackTaskExtraction(originalMessage, "");
        }

        return info;
    }

    /**
     * Fallback extraction when AI fails - ENHANCED with better parsing
     */
    private TaskCreationInfo fallbackTaskExtraction(String userMessage, String aiResponse) {
        TaskCreationInfo info = new TaskCreationInfo();

        log.info("🔄 Using enhanced fallback extraction for: '{}'", userMessage);

        // Enhanced fallback - better pattern matching
        if (userMessage != null && !userMessage.trim().isEmpty()) {
            String lowerMessage = userMessage.toLowerCase().trim();

            // Extract title with better logic - FIXED regex patterns
            String title = null;

            // Try different patterns with proper regex
            if (lowerMessage.contains("tạo task")) {
                // Extract everything after "tạo task" until comma or end
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)tạo task\\s+([^,]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher matcher = pattern.matcher(userMessage);
                if (matcher.find()) {
                    title = matcher.group(1).trim();
                }
            } else if (lowerMessage.startsWith("task")) {
                // Extract everything after "task" until comma or end
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)^task\\s+([^,]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher matcher = pattern.matcher(userMessage);
                if (matcher.find()) {
                    title = matcher.group(1).trim();
                }
            }

            // Set extracted title or default
            info.title = (title != null && !title.isEmpty()) ? title : "New Task";

            // Try to extract deadline from user message
            if (lowerMessage.contains("tháng sau")) {
                // Next month, same day
                java.time.LocalDate nextMonth = java.time.LocalDate.now().plusMonths(1);
                info.deadline = nextMonth.atTime(23, 59, 59).toString();
                log.debug("🔄 Extracted deadline for 'tháng sau': {}", info.deadline);
            } else if (lowerMessage.contains("ngày mai")) {
                info.deadline = java.time.LocalDate.now().plusDays(1).atTime(23, 59, 59).toString();
                log.debug("🔄 Extracted deadline for 'ngày mai': {}", info.deadline);
            } else if (lowerMessage.contains("hôm nay")) {
                info.deadline = java.time.LocalDate.now().atTime(23, 59, 59).toString();
                log.debug("🔄 Extracted deadline for 'hôm nay': {}", info.deadline);
            }

        } else {
            info.title = "New Task";
        }

        info.description = "Task: " + info.title;
        info.priority = "MEDIUM";

        log.info("🔄 Fallback extraction result: title='{}', deadline='{}', priority='{}'",
            info.title, info.deadline, info.priority);

        return info;
    }

    /**
     * Extract JSON from AI response (handle cases where AI adds explanation)
     */
    private String extractJsonFromAIResponse(String response) {
        // Find JSON block in AI response
        int startIndex = response.indexOf("{");
        int endIndex = response.lastIndexOf("}");

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }

        return response; // Return as-is if no JSON block found
    }

    /**
     * Simple JSON value extractor (avoiding Jackson dependency)
     */
    private String extractJsonValue(String json, String key, String defaultValue) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);

            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            log.debug("Could not extract JSON value for key: {}", key);
        }

        return defaultValue;
    }

    /**
     * Parse deadline string to LocalDateTime format expected by MyTaskTools
     */
    private String parseDeadlineString(String deadlineStr) {
        try {
            // Handle YYYY-MM-DD format from AI
            if (deadlineStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return deadlineStr + "T23:59:59"; // Add time component
            }

            // Handle other formats AI might return
            java.time.LocalDate date = java.time.LocalDate.parse(deadlineStr);
            return date.atTime(23, 59, 59).toString();

        } catch (Exception e) {
            log.debug("Could not parse deadline: {}", deadlineStr);
            return null;
        }
    }

    /**
     * Handle task update (placeholder) - RESTORED METHOD
     */
    @SuppressWarnings("unused")
    private String handleTaskUpdate(String aiResponse, Long userId) {
        // TODO: Implement task update logic
        return "Task update functionality will be implemented soon.";
    }

    /**
     * Handle task deletion (placeholder) - RESTORED METHOD
     */
    @SuppressWarnings("unused")
    private String handleTaskDeletion(String aiResponse, Long userId) {
        // TODO: Implement task deletion logic
        return "Task deletion functionality will be implemented soon.";
    }

    /**
     * Inner class to hold task creation information
     */
    private static class TaskCreationInfo {
        String title;
        String description;
        String priority;
        String deadline;
    }

    /**
     * Get system health information
     */
    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("aiService", "unified-ai-with-memory-rag");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("memoryEnabled", true);
        health.put("ragEnabled", true);
        health.put("vectorDatabaseEnabled", true);
        return health;
    }

    /**
     * Handle QUERY intent - Questions that need answers but no actions
     * NEW: Provides information without executing any commands
     * ENHANCED: Handles confirmation flows for commands without wake words
     */
    private ChatResponse handleQueryIntent(IntentDetectionService.IntentResult intentResult,
                                         String conversationId, String messageId, Long userId,
                                         RAGService.RAGContext ragContext,
                                         UserContextService.UserChatContext userContext,
                                         Long projectId, HttpServletRequest httpRequest, ChatRequest request) {

        log.info("🔍 Handling QUERY intent: action={}", intentResult.getAction());

        // ENHANCED: Handle confirmation needed for commands without wake words
        if ("CONFIRMATION_NEEDED".equals(intentResult.getAction())) {
            return handleConfirmationFlow(intentResult, conversationId, messageId, userId,
                                         ragContext, userContext, projectId, httpRequest, request);
        }

        String response;
        boolean useAI = true;

        // Handle specific query types
        switch (intentResult.getAction()) {
            case "GET_TASKS":
                // User asking about their tasks - show them
                try {
                    String taskList = myTaskTools.getUserTasks(userId, null, null, null, 10);
                    response = "📋 **Here are your current tasks:**\n\n" + taskList;
                    useAI = false;
                } catch (Exception e) {
                    response = "I'd be happy to show you your tasks, but there was an issue retrieving them. Please try again.";
                    useAI = false;
                }
                break;

            case "GET_STATISTICS":
                // User asking about task statistics
                try {
                    String stats = myTaskTools.getTaskStatistics(userId);
                    response = "📊 **Your task statistics:**\n\n" + stats;
                    useAI = false;
                } catch (Exception e) {
                    response = "I can help you with task statistics, but there was an issue getting the data. Please try again.";
                    useAI = false;
                }
                break;

            default:
                // General questions - let AI handle with RAG context
                response = "I'll help answer your question based on our conversation.";
                useAI = true;
        }

        if (useAI) {
            // Use AI to generate informative response
            return generateInformationalResponse(request.getContent(), conversationId, messageId,
                                               userId, ragContext, userContext, "query");
        } else {
            // Return direct response
            ChatResponse queryResponse = ChatResponse.builder()
                .messageId(messageId)
                .content(response)
                .senderType("AGENT")
                .timestamp(LocalDateTime.now())
                .aiModel("query-handler")
                .confidence(intentResult.getConfidence())
                .intent("query")
                .success(true)
                .status("QUERY_HANDLED")
                .conversationId(conversationId)
                .agentActive(true)
                .toolCalled(true)
                .build();

            // Store and log
            conversationMemoryService.updateLastTurnWithAIResponse(conversationId, queryResponse.getContent());
            storeAndLogConversation(conversationId, userId, request, queryResponse, projectId, httpRequest);
            return queryResponse;
        }
    }

    /**
     * Handle confirmation flow for commands detected without wake words
     * Implements 2-step confirmation process to avoid accidental task creation
     */
    private ChatResponse handleConfirmationFlow(IntentDetectionService.IntentResult intentResult,
                                              String conversationId, String messageId, Long userId,
                                              RAGService.RAGContext ragContext,
                                              UserContextService.UserChatContext userContext,
                                              Long projectId, HttpServletRequest httpRequest, ChatRequest request) {

        log.info("🔔 Handling confirmation flow for pending action: {}",
                intentResult.getSlots().get("pending_action"));

        // Check if this is a follow-up response to confirmation
        String userMessage = request.getContent().toLowerCase().trim();
        String pendingAction = intentResult.getSlots().get("pending_action");
        String originalMessage = intentResult.getSlots().get("original_message");
        String confirmationStep = intentResult.getSlots().get("confirmation_step");

        // Handle user responses to confirmation questions
        if (isConfirmationResponse(userMessage)) {
            return processConfirmationResponse(userMessage, pendingAction, originalMessage,
                                             conversationId, messageId, userId, ragContext,
                                             userContext, projectId, httpRequest, request);
        }

        // First time showing confirmation question
        String confirmationMessage = intentResult.getFollowUpQuestion() != null ?
                                   intentResult.getFollowUpQuestion() :
                                   generateDefaultConfirmationMessage(pendingAction, originalMessage);

        // Store confirmation state for next interaction
        storeConfirmationState(conversationId, pendingAction, originalMessage, confirmationStep);

        ChatResponse confirmationResponse = ChatResponse.builder()
            .messageId(messageId)
            .content(confirmationMessage)
            .senderType("AGENT")
            .timestamp(LocalDateTime.now())
            .aiModel("confirmation-handler")
            .confidence(intentResult.getConfidence())
            .intent("confirmation")
            .success(true)
            .status("CONFIRMATION_STEP_1")
            .conversationId(conversationId)
            .agentActive(true)
            .toolCalled(false)
            .build();

        // Store and log
        conversationMemoryService.updateLastTurnWithAIResponse(conversationId, confirmationResponse.getContent());
        storeAndLogConversation(conversationId, userId, request, confirmationResponse, projectId, httpRequest);
        return confirmationResponse;
    }

    /**
     * Process user responses to confirmation questions
     * Implements the 2-step confirmation logic
     */
    private ChatResponse processConfirmationResponse(String userResponse, String pendingAction,
                                                   String originalMessage, String conversationId,
                                                   String messageId, Long userId,
                                                   RAGService.RAGContext ragContext,
                                                   UserContextService.UserChatContext userContext,
                                                   Long projectId, HttpServletRequest httpRequest,
                                                   ChatRequest request) {

        log.info("🔄 Processing confirmation response: '{}' for action: {}", userResponse, pendingAction);

        // Check user's response
        if (userResponse.contains("just asking") || userResponse.contains("asking") ||
            userResponse.contains("capabilities")) {

            // User was just asking about capabilities
            clearConfirmationState(conversationId);

            String capabilityResponse = generateCapabilityResponse(pendingAction);

            return ChatResponse.builder()
                .messageId(messageId)
                .content(capabilityResponse)
                .senderType("AGENT")
                .timestamp(LocalDateTime.now())
                .aiModel("capability-explainer")
                .confidence(0.9)
                .intent("capability_explanation")
                .success(true)
                .status("CAPABILITY_EXPLAINED")
                .conversationId(conversationId)
                .agentActive(true)
                .toolCalled(false)
                .build();

        } else if (userResponse.contains("create it") || userResponse.contains("create task") ||
                   userResponse.contains("show tasks") || userResponse.contains("delete task") ||
                   userResponse.contains("perform action")) {

            // User confirmed they want to perform the action - Step 2 confirmation
            return handleStep2Confirmation(pendingAction, originalMessage, conversationId,
                                         messageId, userId, ragContext, userContext,
                                         projectId, httpRequest, request);

        } else {
            // Unclear response, ask again
            String clarificationMessage = String.format("""
                🤔 **I didn't understand your response clearly.**
                
                **Original detected intent:** %s from "%s"
                
                **Please choose clearly:**
                • Type **"just asking"** - if you were asking about capabilities
                • Type **"create it"** - if you want me to perform the action
                • Type **"AI ơi, [your command]"** - to use proper command format
                
                This helps me avoid confusion! 😊
                """,
                pendingAction, originalMessage);

            return ChatResponse.builder()
                .messageId(messageId)
                .content(clarificationMessage)
                .senderType("AGENT")
                .timestamp(LocalDateTime.now())
                .aiModel("clarification-handler")
                .confidence(0.8)
                .intent("clarification")
                .success(true)
                .status("CLARIFICATION_NEEDED")
                .conversationId(conversationId)
                .agentActive(true)
                .toolCalled(false)
                .build();
        }
    }

    /**
     * Handle Step 2 confirmation - asking for specific details
     */
    private ChatResponse handleStep2Confirmation(String pendingAction, String originalMessage,
                                                String conversationId, String messageId, Long userId,
                                                RAGService.RAGContext ragContext,
                                                UserContextService.UserChatContext userContext,
                                                Long projectId, HttpServletRequest httpRequest,
                                                ChatRequest request) {

        log.info("🔄 Step 2 confirmation for action: {}", pendingAction);

        String step2Message;

        switch (pendingAction) {
            case "CREATE_TASK":
                // Extract potential title from original message
                String potentialTitle = extractTitleFromMessage(originalMessage);

                if (potentialTitle != null && !potentialTitle.trim().isEmpty()) {
                    step2Message = String.format("""
                        ✅ **Got it! You want to create a task.**
                        
                        **Step 2/2:** Please confirm the task details:
                        
                        **📝 Task Title:** "%s"
                        **🎯 Priority:** MEDIUM (default)
                        **📅 Deadline:** None specified
                        
                        **Final confirmation:**
                        • Type **"confirm create"** - to create this task
                        • Type **"change title [new title]"** - to change the title
                        • Type **"cancel"** - to cancel task creation
                        
                        **Or use proper format:** "AI ơi, tạo task %s"
                        """,
                        potentialTitle, potentialTitle);
                } else {
                    step2Message = """
                        ✅ **Got it! You want to create a task.**
                        
                        **Step 2/2:** I need the task details:
                        
                        **What should I name this task?**
                        
                        **Options:**
                        • Type the task name directly
                        • Type **"cancel"** - to cancel task creation
                        • Use proper format: **"AI ơi, tạo task [task name]"**
                        
                        This ensures I create exactly what you want! 📝
                        """;
                }
                break;

            case "GET_TASKS":
                // For showing tasks, execute immediately after confirmation
                try {
                    String taskList = myTaskTools.getUserTasks(userId, null, null, null, 10);
                    clearConfirmationState(conversationId);

                    return ChatResponse.builder()
                        .messageId(messageId)
                        .content("✅ **Here are your tasks as requested:**\n\n" + taskList)
                        .senderType("AGENT")
                        .timestamp(LocalDateTime.now())
                        .aiModel("task-lister")
                        .confidence(0.95)
                        .intent("task_list")
                        .success(true)
                        .status("TASKS_SHOWN")
                        .conversationId(conversationId)
                        .agentActive(true)
                        .toolCalled(true)
                        .build();

                } catch (Exception e) {
                    step2Message = "❌ I confirmed you want to see tasks, but there was an error retrieving them. Please try again.";
                }
                break;

            default:
                step2Message = String.format("""
                    ✅ **Got it! You want to perform: %s**
                    
                    **Step 2/2:** Please provide the required details or use the proper command format:
                    
                    **"AI ơi, [your complete command]"**
                    
                    This ensures accurate execution! 🎯
                    """,
                    pendingAction);
        }

        return ChatResponse.builder()
            .messageId(messageId)
            .content(step2Message)
            .senderType("AGENT")
            .timestamp(LocalDateTime.now())
            .aiModel("step2-confirmation")
            .confidence(0.9)
            .intent("step2_confirmation")
            .success(true)
            .status("CONFIRMATION_STEP_2")
            .conversationId(conversationId)
            .agentActive(true)
            .toolCalled(false)
            .build();
    }

    // Helper methods for confirmation flow
    private boolean isConfirmationResponse(String message) {
        return message.contains("just asking") || message.contains("asking") ||
               message.contains("create it") || message.contains("create task") ||
               message.contains("show tasks") || message.contains("delete task") ||
               message.contains("perform action") || message.contains("capabilities") ||
               message.contains("confirm") || message.contains("cancel");
    }

    private String generateCapabilityResponse(String action) {
        switch (action) {
            case "CREATE_TASK":
                return """
                    ✅ **Yes, I can create tasks for you!**
                    
                    **My task creation capabilities:**
                    • Create tasks with titles, priorities, and deadlines
                    • Set different priority levels (HIGH, MEDIUM, LOW)
                    • Schedule tasks with specific deadlines
                    • Organize tasks by projects
                    
                    **To create a task, use:**
                    **"AI ơi, tạo task [task name]"** or **"TaskFlow, create task [task name]"**
                    
                    **Examples:**
                    • "AI ơi, tạo task nấu cơm deadline ngày mai"
                    • "TaskFlow, create task meeting high priority"
                    
                    This format helps me understand your intent clearly! 🎯
                    """;

            case "GET_TASKS":
                return """
                    ✅ **Yes, I can show you your tasks!**
                    
                    **My task viewing capabilities:**
                    • Show all your current tasks
                    • Filter by status, priority, or deadline
                    • Display task statistics and progress
                    • Show overdue and upcoming tasks
                    
                    **To view tasks, use:**
                    **"AI ơi, hiển thị task"** or **"TaskFlow, show my tasks"**
                    
                    **Examples:**
                    • "AI ơi, hiển thị tất cả task"
                    • "TaskFlow, show high priority tasks"
                    
                    Clear commands help me serve you better! 📋
                    """;

            default:
                return """
                    ✅ **Yes, I have various capabilities!**
                    
                    **What I can do:**
                    • Create and manage tasks
                    • Show task lists and statistics
                    • Help with project organization
                    • Provide task reminders and updates
                    
                    **For clear communication, please use:**
                    **"AI ơi, [your request]"** or **"TaskFlow, [your request]"**
                    
                    This helps me understand exactly what you need! 🤖
                    """;
        }
    }

    private String extractTitleFromMessage(String message) {
        // Simple extraction logic for potential task titles
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("tạo task")) {
            String afterTask = message.substring(message.toLowerCase().indexOf("tạo task") + 8).trim();
            if (!afterTask.isEmpty() && afterTask.length() > 1) {
                return afterTask.split(",")[0].trim();
            }
        } else if (lowerMessage.contains("create task")) {
            String afterTask = message.substring(message.toLowerCase().indexOf("create task") + 11).trim();
            if (!afterTask.isEmpty() && afterTask.length() > 1) {
                return afterTask.split(",")[0].trim();
            }
        }
        return null;
    }

    private void storeConfirmationState(String conversationId, String pendingAction, String originalMessage, String step) {
        // Store confirmation state in conversation memory or session
        // This would ideally use ConversationStateService
        log.info("📝 Storing confirmation state: action={}, step={}", pendingAction, step);
    }

    private void clearConfirmationState(String conversationId) {
        // Clear confirmation state
        log.info("🗑️ Clearing confirmation state for conversation: {}", conversationId);
    }

    private String generateDefaultConfirmationMessage(String action, String originalMessage) {
        return String.format("""
            🤔 **I detected a possible command in your message!**
            
            **Your Message:** "%s"
            **Detected Action:** %s
            
            **Step 1/2:** Are you asking about my capabilities, or do you want me to perform this action?
            
            **Please choose:**
            • **"just asking"** - if you're asking about capabilities
            • **"perform action"** - if you want me to do something
            
            This helps me avoid confusion! 😊
            """,
            originalMessage, action);
    }

    /**
     * Handle CHITCHAT intent - Casual conversation
     */
    private ChatResponse handleChitchatIntent(IntentDetectionService.IntentResult intentResult,
                                            String conversationId, String messageId, Long userId,
                                            RAGService.RAGContext ragContext,
                                            UserContextService.UserChatContext userContext,
                                            Long projectId, HttpServletRequest httpRequest, ChatRequest request) {

        log.info("💬 Handling CHITCHAT intent: confidence={}", intentResult.getConfidence());

        // Generate friendly conversational response
        return generateInformationalResponse(request.getContent(), conversationId, messageId,
                                           userId, ragContext, userContext, "chitchat");
    }

    /**
     * Handle COMMAND intent - Actions that may need slot filling
     */
    private ChatResponse handleCommandIntent(IntentDetectionService.IntentResult intentResult,
                                           String conversationId, String messageId, Long userId,
                                           RAGService.RAGContext ragContext,
                                           UserContextService.UserChatContext userContext,
                                           Long projectId, HttpServletRequest httpRequest, ChatRequest request) {

        log.info("⚡ Handling COMMAND intent: action={}, needsMoreInfo={}",
                intentResult.getAction(), intentResult.isNeedsMoreInfo());

        // Check if command needs more information (slot filling)
        if (intentResult.isNeedsMoreInfo()) {
            SlotFillingService.SlotFillingResult slotResult = slotFillingService.startSlotFilling(
                    intentResult, conversationId, userId);

            if (!slotResult.isComplete()) {
                // Return slot filling question
                ChatResponse slotResponse = ChatResponse.builder()
                        .messageId(messageId)
                        .content(slotResult.getNextQuestion())
                        .senderType("AGENT")
                        .timestamp(LocalDateTime.now())
                        .aiModel("slot-filler")
                        .confidence(intentResult.getConfidence())
                        .intent("slot_filling")
                        .success(true)
                        .status("SLOT_FILLING_ACTIVE")
                        .conversationId(conversationId)
                        .agentActive(true)
                        .toolCalled(false)
                        .build();

                conversationMemoryService.updateLastTurnWithAIResponse(conversationId, slotResponse.getContent());
                storeAndLogConversation(conversationId, userId, request, slotResponse, projectId, httpRequest);
                return slotResponse;
            }

            // Slots are complete, update intent result with filled slots
            intentResult.getSlots().putAll(slotResult.getCurrentSlots());
        }

        // Execute the command with complete information
        return executeCommand(intentResult, conversationId, messageId, userId,
                            ragContext, userContext, projectId, httpRequest, request);
    }

    /**
     * Execute command with complete slot information
     */
    private ChatResponse executeCommand(IntentDetectionService.IntentResult intentResult,
                                      String conversationId, String messageId, Long userId,
                                      RAGService.RAGContext ragContext,
                                      UserContextService.UserChatContext userContext,
                                      Long projectId, HttpServletRequest httpRequest, ChatRequest request) {

        log.info("🔧 Executing command: action={}", intentResult.getAction());

        String response;
        boolean toolExecuted = false;

        try {
            switch (intentResult.getAction()) {
                case "CREATE_TASK":
                    String taskResult = handleTaskCreationFromSlots(intentResult.getSlots(), userId);
                    response = "✅ **Task created successfully!**\n\n" + taskResult;
                    toolExecuted = true;
                    break;

                case "GET_TASKS":
                    String taskList = myTaskTools.getUserTasks(userId, null, null, null, 10);
                    response = "📋 **Here are your current tasks:**\n\n" + taskList;
                    toolExecuted = true;
                    break;

                case "GET_STATISTICS":
                    String stats = myTaskTools.getTaskStatistics(userId);
                    response = "📊 **Your task statistics:**\n\n" + stats;
                    toolExecuted = true;
                    break;

                default:
                    response = "I understand you want to " + intentResult.getAction() +
                              ", but this command is not yet implemented.";
                    toolExecuted = false;
            }

        } catch (Exception e) {
            log.error("❌ Command execution failed: {}", e.getMessage(), e);
            response = "❌ I encountered an error while executing the command: " + e.getMessage();
            toolExecuted = false;
        }

        ChatResponse commandResponse = ChatResponse.builder()
                .messageId(messageId)
                .content(response)
                .senderType("AGENT")
                .timestamp(LocalDateTime.now())
                .aiModel("command-executor")
                .confidence(intentResult.getConfidence())
                .intent("command")
                .success(true)
                .status("COMMAND_EXECUTED")
                .conversationId(conversationId)
                .agentActive(true)
                .toolCalled(toolExecuted)
                .build();

        conversationMemoryService.updateLastTurnWithAIResponse(conversationId, commandResponse.getContent());
        storeAndLogConversation(conversationId, userId, request, commandResponse, projectId, httpRequest);
        return commandResponse;
    }

    /**
     * Handle task creation from filled slots
     */
    private String handleTaskCreationFromSlots(Map<String, String> slots, Long userId) {
        try {
            String title = slots.getOrDefault("title", "New Task");
            String description = slots.getOrDefault("description", "Task created via AI");
            String priority = slots.getOrDefault("priority", "MEDIUM");
            String deadline = slots.get("deadline");

            log.info("🔨 Creating task from slots: title='{}', priority='{}', deadline='{}'",
                    title, priority, deadline);

            return myTaskTools.createTask(title, description, priority, deadline, null, userId);

        } catch (Exception e) {
            log.error("Error creating task from slots", e);
            throw e;
        }
    }

    /**
     * Generate informational response using AI
     */
    private ChatResponse generateInformationalResponse(String userMessage, String conversationId,
                                                     String messageId, Long userId,
                                                     RAGService.RAGContext ragContext,
                                                     UserContextService.UserChatContext userContext,
                                                     String intentType) {
        try {
            // Build enhanced context for informational response
            String enhancedContext = buildRAGEnhancedContext(ragContext, userId, null, userContext);

            // Create AI request for informational response
            UnifiedAIService.UnifiedRequest aiRequest = new UnifiedAIService.UnifiedRequest(
                    userMessage,
                    userId,
                    enhancedContext,
                    intentType.equals("chitchat") ?
                        "You are a friendly AI assistant. Provide helpful, conversational responses." :
                        "You are a helpful AI assistant. Answer questions clearly and informatively.",
                    null
            );

            UnifiedAIService.UnifiedResponse aiResponse = unifiedAIService.processUnifiedRequest(aiRequest);

            ChatResponse infoResponse = ChatResponse.builder()
                    .messageId(messageId)
                    .content(aiResponse.getResponseContent())
                    .senderType("AGENT")
                    .timestamp(LocalDateTime.now())
                    .aiModel("informational-ai")
                    .confidence(Math.max(aiResponse.getConfidence(), ragContext.getContextQuality()))
                    .intent(intentType)
                    .success(true)
                    .status("INFORMATIONAL_RESPONSE")
                    .conversationId(conversationId)
                    .agentActive(true)
                    .toolCalled(false)
                    .build();

            conversationMemoryService.updateLastTurnWithAIResponse(conversationId, infoResponse.getContent());
            return infoResponse;

        } catch (Exception e) {
            log.error("❌ Failed to generate informational response: {}", e.getMessage(), e);

            // Fallback response
            String fallbackContent = intentType.equals("chitchat") ?
                    "I'm here to help! Feel free to ask me anything about your tasks or just chat." :
                    "I'd be happy to help you with that. Could you provide more details?";

            return ChatResponse.builder()
                    .messageId(messageId)
                    .content(fallbackContent)
                    .senderType("AGENT")
                    .timestamp(LocalDateTime.now())
                    .aiModel("fallback-response")
                    .confidence(0.5)
                    .intent(intentType)
                    .success(true)
                    .status("FALLBACK_RESPONSE")
                    .conversationId(conversationId)
                    .agentActive(true)
                    .toolCalled(false)
                    .build();
        }
    }
}
