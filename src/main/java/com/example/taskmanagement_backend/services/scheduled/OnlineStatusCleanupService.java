package com.example.taskmanagement_backend.services.scheduled;

import com.example.taskmanagement_backend.services.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlineStatusCleanupService {

    private final OnlineStatusService onlineStatusService;

    /**
     * Dọn dẹp users offline mỗi 10 phút
     * Tự động set offline các users không hoạt động quá 5 phút
     */
    @Scheduled(fixedRate = 10 * 60 * 1000) // 10 phút
    public void cleanupOfflineUsers() {
        log.debug("🧹 Starting offline users cleanup task...");
        try {
            onlineStatusService.cleanupOfflineUsers();
            log.debug("✅ Offline users cleanup completed");
        } catch (Exception e) {
            log.error("❌ Error during offline users cleanup: {}", e.getMessage());
        }
    }
}
