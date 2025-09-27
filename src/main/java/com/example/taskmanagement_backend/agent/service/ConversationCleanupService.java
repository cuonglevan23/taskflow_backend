package com.example.taskmanagement_backend.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service thực hiện cleanup các conversation state cũ
 * Chạy định kỳ để giải phóng memory
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationCleanupService {

    private final ConversationStateService conversationStateService;

    /**
     * Cleanup conversation states cũ mỗi 30 phút
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void cleanupExpiredConversations() {
        try {
            log.debug("Starting conversation state cleanup...");
            conversationStateService.cleanupExpiredStates();
            log.debug("Conversation state cleanup completed");
        } catch (Exception e) {
            log.error("Error during conversation state cleanup", e);
        }
    }
}
