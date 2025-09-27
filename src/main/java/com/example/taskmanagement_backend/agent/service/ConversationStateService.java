package com.example.taskmanagement_backend.agent.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service quản lý trạng thái hội thoại multi-turn cho AI Agent
 * Hỗ trợ thu thập thông tin từng bước cho các tác vụ phức tạp
 */
@Slf4j
@Service
public class ConversationStateService {

    private final Map<String, ConversationState> conversationStates = new ConcurrentHashMap<>();

    /**
     * Lấy trạng thái hội thoại hiện tại
     */
    public ConversationState getConversationState(String conversationId) {
        return conversationStates.get(conversationId);
    }

    /**
     * Tạo hoặc cập nhật trạng thái hội thoại
     */
    public ConversationState createOrUpdateState(String conversationId, ConversationType type, String currentStep) {
        ConversationState state = conversationStates.computeIfAbsent(conversationId,
            k -> new ConversationState(conversationId, type));
        state.setCurrentStep(currentStep);
        state.setLastUpdated(LocalDateTime.now());

        log.debug("Updated conversation state: conversationId={}, type={}, step={}",
            conversationId, type, currentStep);

        return state;
    }

    /**
     * Cập nhật dữ liệu thu thập được
     */
    public void updateCollectedData(String conversationId, String key, Object value) {
        ConversationState state = conversationStates.get(conversationId);
        if (state != null) {
            state.getCollectedData().put(key, value);
            state.setLastUpdated(LocalDateTime.now());
            log.debug("Updated collected data: conversationId={}, key={}, value={}",
                conversationId, key, value);
        }
    }

    /**
     * Lấy dữ liệu đã thu thập
     */
    public Object getCollectedData(String conversationId, String key) {
        ConversationState state = conversationStates.get(conversationId);
        return state != null ? state.getCollectedData().get(key) : null;
    }

    /**
     * Kiểm tra xem đã thu thập đủ thông tin chưa
     */
    public boolean isDataComplete(String conversationId, ConversationType type) {
        ConversationState state = conversationStates.get(conversationId);
        if (state == null) return false;

        switch (type) {
            case CREATE_TASK:
                return state.getCollectedData().containsKey("title") &&
                       !((String) state.getCollectedData().get("title")).trim().isEmpty();

            case UPDATE_TASK:
                return state.getCollectedData().containsKey("taskId") &&
                       state.getCollectedData().containsKey("field") &&
                       state.getCollectedData().containsKey("value");

            case DELETE_TASK:
                return state.getCollectedData().containsKey("taskId");

            default:
                return false;
        }
    }

    /**
     * Xóa trạng thái hội thoại sau khi hoàn thành
     */
    public void clearState(String conversationId) {
        conversationStates.remove(conversationId);
        log.debug("Cleared conversation state for: {}", conversationId);
    }

    /**
     * Xóa các trạng thái hội thoại cũ (cleanup)
     */
    public void cleanupExpiredStates() {
        LocalDateTime expiredTime = LocalDateTime.now().minusHours(1);
        conversationStates.entrySet().removeIf(entry ->
            entry.getValue().getLastUpdated().isBefore(expiredTime));
    }

    /**
     * Enum định nghĩa các loại hội thoại
     */
    public enum ConversationType {
        CREATE_TASK,
        UPDATE_TASK,
        DELETE_TASK,
        GET_TASKS,
        GENERAL_CHAT
    }

    /**
     * Class lưu trữ trạng thái hội thoại
     */
    @Data
    public static class ConversationState {
        private final String conversationId;
        private final ConversationType type;
        private String currentStep;
        private LocalDateTime createdAt;
        private LocalDateTime lastUpdated;
        private Map<String, Object> collectedData;
        private int stepCount;

        public ConversationState(String conversationId, ConversationType type) {
            this.conversationId = conversationId;
            this.type = type;
            this.createdAt = LocalDateTime.now();
            this.lastUpdated = LocalDateTime.now();
            this.collectedData = new ConcurrentHashMap<>();
            this.stepCount = 0;
            this.currentStep = "INIT";
        }

        public void incrementStep() {
            this.stepCount++;
            this.lastUpdated = LocalDateTime.now();
        }
    }
}
