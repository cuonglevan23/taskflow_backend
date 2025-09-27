package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.dto.ChatResponse;
import com.example.taskmanagement_backend.agent.tools.MyTaskTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler xử lý hội thoại multi-turn cho AI Agent
 * Thu thập thông tin từng bước và thực hiện các tác vụ khi đủ dữ liệu
 * FIXED: Use rule-based intent detection to avoid additional API calls
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiTurnConversationHandler {

    private final ConversationStateService stateService;
    private final MyTaskTools taskTools;
    // REMOVED IntentDetectionService to prevent additional API calls

    /**
     * Xử lý tin nhắn trong context multi-turn conversation
     * FIXED: COMPLETELY disabled hard-coded logic - let Gemini AI classify ALL intents first
     * MultiTurnConversationHandler only assists when explicitly called by CoreAgentService
     */
    public ChatResponse handleMultiTurnMessage(String conversationId, Long userId, String userMessage) {
        ConversationStateService.ConversationState state = stateService.getConversationState(conversationId);

        // FIXED: ALWAYS let Gemini AI handle first - no hard-coded interruption
        if (state == null) {
            // No existing state, always let CoreAgentService + Gemini AI handle
            return null;
        }

        // If there's existing state, check if user wants to exit/decline
        String lowerMessage = userMessage.toLowerCase().trim();

        // Only handle explicit exit commands to break out of multi-turn
        if (lowerMessage.equals("thoát") || lowerMessage.equals("exit") ||
            lowerMessage.equals("hủy") || lowerMessage.equals("cancel") ||
            lowerMessage.equals("dừng") || lowerMessage.equals("stop")) {

            stateService.clearState(conversationId);
            log.info("User explicitly exited multi-turn conversation with command: {}", lowerMessage);
            return null; // Let Gemini AI handle the exit response
        }

        // CRITICAL FIX: For ALL other messages in multi-turn state,
        // let Gemini AI classify intent first instead of continuing multi-turn
        // This prevents hard-coded declining detection from overriding AI classification
        stateService.clearState(conversationId); // Clear state to let AI reclassify
        log.info("Clearing multi-turn state to let Gemini AI reclassify intent for message: {}", userMessage);

        return null; // Always let CoreAgentService + Gemini AI handle
    }

    /**
     * Rule-based detection for multi-turn conversation (no API calls)
     */
    private boolean shouldStartMultiTurnConversation(String userMessage) {
        String lowerMessage = userMessage.toLowerCase().trim();

        // Check for incomplete task creation requests that need more info
        if (lowerMessage.contains("tạo task") || lowerMessage.contains("create task")) {
            // If message is very short or lacks detail, start multi-turn
            String[] words = userMessage.trim().split("\\s+");
            if (words.length <= 3) {
                return true; // Very short, likely needs more info
            }

            // Check if missing important details
            boolean hasTitle = extractTaskTitle(userMessage) != null;
            boolean hasDeadline = extractDeadline(userMessage) != null;
            boolean hasDescription = userMessage.length() > 20;

            // Start multi-turn if missing critical info
            return !hasTitle || (!hasDeadline && !hasDescription);
        }

        // Check for incomplete update requests
        if (lowerMessage.contains("cập nhật") || lowerMessage.contains("update") || lowerMessage.contains("sửa")) {
            return !lowerMessage.contains("task") && !containsTaskId(userMessage);
        }

        // Check for incomplete delete requests
        if (lowerMessage.contains("xóa") || lowerMessage.contains("delete") || lowerMessage.contains("remove")) {
            return !containsTaskId(userMessage);
        }

        return false;
    }

    /**
     * Check if message contains task ID
     */
    private boolean containsTaskId(String message) {
        // Simple check for task ID patterns
        return message.matches(".*\\b\\d+\\b.*") || // Contains numbers
               message.toLowerCase().contains("task") ||
               message.toLowerCase().contains("công việc");
    }

    /**
     * Bắt đầu flow tạo task
     */
    private ChatResponse startTaskCreationFlow(String conversationId, Long userId, String userMessage) {
        ConversationStateService.ConversationState state = stateService.createOrUpdateState(
            conversationId, ConversationStateService.ConversationType.CREATE_TASK, "COLLECTING_TITLE");

        // Thử extract title từ message ban đầu
        String extractedTitle = extractTaskTitle(userMessage);

        if (extractedTitle != null && !extractedTitle.trim().isEmpty()) {
            stateService.updateCollectedData(conversationId, "title", extractedTitle);

            // Nếu có title rồi, hỏi thêm thông tin khác
            String deadline = extractDeadline(userMessage);
            if (deadline != null) {
                stateService.updateCollectedData(conversationId, "deadline", deadline);
            }

            return askForAdditionalInfo(conversationId, extractedTitle);
        } else {
            // Chưa có title, hỏi title
            return buildResponse(conversationId,
                "💡 Tôi sẽ giúp bạn tạo task mới! Bạn muốn tạo task có tiêu đề gì?\n\n" +
                "Ví dụ: 'Hoàn thành báo cáo tháng 9' hoặc 'Họp team review dự án'");
        }
    }

    /**
     * Xử lý flow tạo task
     */
    private ChatResponse handleTaskCreationFlow(String conversationId, Long userId, String userMessage,
                                              ConversationStateService.ConversationState state) {

        switch (state.getCurrentStep()) {
            case "COLLECTING_TITLE":
                return handleTitleCollection(conversationId, userId, userMessage, state);

            case "COLLECTING_ADDITIONAL_INFO":
                return handleAdditionalInfoCollection(conversationId, userId, userMessage, state);

            case "CONFIRMING_CREATION":
                return handleCreationConfirmation(conversationId, userId, userMessage, state);

            default:
                return startTaskCreationFlow(conversationId, userId, userMessage);
        }
    }

    /**
     * Thu thập tiêu đề task
     */
    private ChatResponse handleTitleCollection(String conversationId, Long userId, String userMessage,
                                             ConversationStateService.ConversationState state) {

        // Extract title từ user message
        String title = extractTaskTitleFromPlainText(userMessage);

        if (title == null || title.trim().isEmpty()) {
            return buildResponse(conversationId,
                "❓ Tôi chưa hiểu rõ tiêu đề task. Bạn có thể nói rõ hơn không?\n\n" +
                "Ví dụ: 'Tạo task làm báo cáo' hoặc chỉ cần 'Làm báo cáo tháng 9'");
        }

        stateService.updateCollectedData(conversationId, "title", title);
        return askForAdditionalInfo(conversationId, title);
    }

    /**
     * Hỏi thông tin bổ sung
     */
    private ChatResponse askForAdditionalInfo(String conversationId, String title) {
        stateService.createOrUpdateState(conversationId,
            ConversationStateService.ConversationType.CREATE_TASK, "COLLECTING_ADDITIONAL_INFO");

        return buildResponse(conversationId,
            String.format("✅ Tôi sẽ tạo task: \"%s\"\n\n" +
                "🔹 Bạn có muốn thêm thông tin nào khác không?\n" +
                "- Mô tả chi tiết\n" +
                "- Thời hạn (deadline)\n" +
                "- Độ ưu tiên (cao/trung bình/thấp)\n\n" +
                "Hoặc nhập 'tạo luôn' để tạo task ngay! 🚀", title));
    }

    /**
     * Thu thập thông tin bổ sung
     */
    private ChatResponse handleAdditionalInfoCollection(String conversationId, Long userId, String userMessage,
                                                      ConversationStateService.ConversationState state) {

        String lowerMessage = userMessage.toLowerCase().trim();

        // CRITICAL: Check for declining intent FIRST
        if (lowerMessage.contains("không muốn") || lowerMessage.contains("không cần") ||
            (lowerMessage.contains("không") && lowerMessage.contains("tạo")) ||
            lowerMessage.contains("thôi") || lowerMessage.contains("hủy") ||
            lowerMessage.contains("cancel") || lowerMessage.contains("dừng")) {

            // Clear state and exit multi-turn conversation
            stateService.clearState(conversationId);

            log.info("User declined task creation in multi-turn flow, clearing state and exiting");

            // Return null to let CoreAgentService handle with Gemini AI
            return null; // Let normal flow handle declining properly
        }

        // Nếu user muốn tạo ngay
        if (lowerMessage.contains("tạo luôn") || lowerMessage.contains("tạo ngay") ||
            lowerMessage.equals("ok") || lowerMessage.equals("xong")) {
            return executeTaskCreation(conversationId, userId, state);
        }

        // Thu thập thông tin bổ sung
        collectAdditionalInfo(conversationId, userMessage);

        return buildResponse(conversationId,
            "📝 Đã ghi nhận thông tin bổ sung!\n\n" +
            "Bạn có muốn thêm gì nữa không? Hoặc nhập 'tạo luôn' để hoàn thành! ✨");
    }

    /**
     * Xử lý xác nhận tạo task
     */
    private ChatResponse handleCreationConfirmation(String conversationId, Long userId, String userMessage,
                                                  ConversationStateService.ConversationState state) {
        String lowerMessage = userMessage.toLowerCase().trim();

        // Xác nhận tạo task
        if (lowerMessage.contains("có") || lowerMessage.contains("yes") ||
            lowerMessage.contains("ok") || lowerMessage.contains("đồng ý") ||
            lowerMessage.contains("xác nhận") || lowerMessage.contains("tạo")) {
            return executeTaskCreation(conversationId, userId, state);
        }

        // Hủy tạo task
        if (lowerMessage.contains("không") || lowerMessage.contains("no") ||
            lowerMessage.contains("hủy") || lowerMessage.contains("cancel")) {
            stateService.clearState(conversationId);
            return buildResponse(conversationId, "❌ Đã hủy tạo task. Bạn có thể bắt đầu lại bất cứ lúc nào!");
        }

        // Yêu cầu xác nhận rõ ràng hơn
        return buildResponse(conversationId,
            "🤔 Tôi chưa hiểu ý bạn. Bạn có muốn tạo task này không?\n\n" +
            "Trả lời 'có' để tạo hoặc 'không' để hủy.");
    }

    /**
     * Thu thập thông tin bổ sung từ message
     */
    private void collectAdditionalInfo(String conversationId, String userMessage) {
        // Mô tả
        if (userMessage.toLowerCase().contains("mô tả") || userMessage.toLowerCase().contains("chi tiết")) {
            String description = extractDescription(userMessage);
            if (description != null) {
                stateService.updateCollectedData(conversationId, "description", description);
            }
        }

        // Deadline
        String deadline = extractDeadline(userMessage);
        if (deadline != null) {
            stateService.updateCollectedData(conversationId, "deadline", deadline);
        }

        // Priority
        String priority = extractPriority(userMessage);
        if (priority != null) {
            stateService.updateCollectedData(conversationId, "priority", priority);
        }

        // Nếu không có từ khóa cụ thể, coi như mô tả
        if (!userMessage.toLowerCase().contains("mô tả") &&
            !userMessage.toLowerCase().contains("deadline") &&
            !userMessage.toLowerCase().contains("ưu tiên") &&
            deadline == null && priority == null) {
            stateService.updateCollectedData(conversationId, "description", userMessage);
        }
    }

    /**
     * Thực hiện tạo task - COMPLETELY DISABLED
     * All task creation should go through CoreAgentService only
     */
    private ChatResponse executeTaskCreation(String conversationId, Long userId,
                                           ConversationStateService.ConversationState state) {

        // CRITICAL FIX: Never create tasks here - always delegate to CoreAgentService
        log.info("DISABLED: MultiTurnConversationHandler task creation - delegating to CoreAgentService");

        // Clear any multi-turn state since we're not handling it
        stateService.clearState(conversationId);

        // Return null to let CoreAgentService handle via normal AI flow
        return null;

        /* COMPLETELY COMMENTED OUT TO PREVENT DUPLICATE TASK CREATION
        String title = (String) stateService.getCollectedData(conversationId, "title");
        String description = (String) stateService.getCollectedData(conversationId, "description");
        String deadline = (String) stateService.getCollectedData(conversationId, "deadline");
        String priority = (String) stateService.getCollectedData(conversationId, "priority");

        // Sử dụng title làm description nếu không có description
        if (description == null || description.trim().isEmpty()) {
            description = title;
        }

        try {
            String result = taskTools.createTask(title, description, priority, deadline, null, userId);

            // Clear state sau khi hoàn thành
            stateService.clearState(conversationId);

            return buildResponse(conversationId, result + "\n\n🎉 Task đã được tạo thành công!");

        } catch (Exception e) {
            log.error("Error creating task in multi-turn conversation", e);
            stateService.clearState(conversationId);

            return buildResponse(conversationId,
                "❌ Có lỗi xảy ra khi tạo task: " + e.getMessage() +
                "\n\nBạn có thể thử lại không?");
        }
        */
    }

    // =============================================================================
    // HELPER METHODS
    // =============================================================================

    /**
     * Kiểm tra xem có phải intent tạo task không
     */
    private boolean isTaskCreationIntent(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();

        return lowerMessage.contains("tạo task") ||
               lowerMessage.contains("tạo công việc") ||
               lowerMessage.contains("muốn tạo task") ||
               lowerMessage.contains("tạo nhiệm vụ") ||
               (lowerMessage.startsWith("task ") && !lowerMessage.contains("xóa") && !lowerMessage.contains("cập nhật")) ||
               lowerMessage.equals("tạo task");
    }

    /**
     * Extract task title từ các pattern thông thường
     */
    private String extractTaskTitle(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();

        // Patterns for task creation
        String[] patterns = {
            "tạo task (.+)",
            "task (.+)",
            "tạo nhiệm vụ (.+)",
            "tạo công việc (.+)"
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(userMessage);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }

        return null;
    }

    /**
     * Extract title từ plain text (khi user chỉ trả lời title)
     */
    private String extractTaskTitleFromPlainText(String userMessage) {
        String cleaned = userMessage.trim();

        // Loại bỏ các từ không cần thiết
        String[] removeWords = {"tạo task", "task", "tạo", "làm", "thực hiện"};

        for (String word : removeWords) {
            if (cleaned.toLowerCase().startsWith(word.toLowerCase())) {
                cleaned = cleaned.substring(word.length()).trim();
            }
        }

        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Extract mô tả
     */
    private String extractDescription(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();

        if (lowerMessage.contains("mô tả:") || lowerMessage.contains("chi tiết:")) {
            String[] parts = userMessage.split(":|mô tả|chi tiết", 2);
            return parts.length > 1 ? parts[1].trim() : null;
        }

        return null;
    }

    /**
     * Extract deadline
     */
    private String extractDeadline(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();

        if (lowerMessage.contains("ngày mai") || lowerMessage.contains("tomorrow")) {
            return LocalDateTime.now().plusDays(1).toString();
        }
        if (lowerMessage.contains("tuần sau") || lowerMessage.contains("next week")) {
            return LocalDateTime.now().plusWeeks(1).toString();
        }
        if (lowerMessage.contains("cuối tuần") || lowerMessage.contains("weekend")) {
            return LocalDateTime.now().plusDays(7 - LocalDateTime.now().getDayOfWeek().getValue()).toString();
        }

        return null;
    }

    /**
     * Extract priority
     */
    private String extractPriority(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();

        if (lowerMessage.contains("cao") || lowerMessage.contains("high") || lowerMessage.contains("urgent")) {
            return "HIGH";
        }
        if (lowerMessage.contains("thấp") || lowerMessage.contains("low")) {
            return "LOW";
        }
        if (lowerMessage.contains("trung bình") || lowerMessage.contains("medium") || lowerMessage.contains("normal")) {
            return "MEDIUM";
        }

        return null;
    }

    /**
     * Xử lý các flow khác (placeholder)
     */
    private ChatResponse handleTaskUpdateFlow(String conversationId, Long userId, String userMessage,
                                            ConversationStateService.ConversationState state) {
        // TODO: Implement task update flow
        return null;
    }

    private ChatResponse handleTaskDeletionFlow(String conversationId, Long userId, String userMessage,
                                              ConversationStateService.ConversationState state) {
        // TODO: Implement task deletion flow
        return null;
    }

    /**
     * Build response
     */
    private ChatResponse buildResponse(String conversationId, String content) {
        return ChatResponse.builder()
            .messageId(UUID.randomUUID().toString())
            .content(content)
            .senderType("AGENT")
            .timestamp(LocalDateTime.now())
            .aiModel("multi-turn-handler")
            .confidence(0.95)
            .success(true)
            .status("MULTI_TURN_ACTIVE")
            .conversationId(conversationId)
            .agentActive(true)
            .build();
    }
}
