package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlineStatusService {

    private final UserJpaRepository userRepository;

    private static final int ONLINE_TIMEOUT_MINUTES = 5; // User offline nếu không hoạt động > 5 phút

    /**
     * Đánh dấu user online khi login
     */
    public void setUserOnline(String userEmail) {
        try {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

            user.setOnline(true);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);

            log.info("✅ User {} (ID: {}) set to ONLINE", userEmail, user.getId());
        } catch (Exception e) {
            log.error("❌ Failed to set user online for email {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Đánh dấu user online khi login (by userId)
     */
    public void setUserOnline(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            user.setOnline(true);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);

            log.debug("User {} set to ONLINE", userId);
        } catch (Exception e) {
            log.error("❌ Failed to set user {} online: {}", userId, e.getMessage());
        }
    }

    /**
     * Đánh dấu user offline khi logout
     */
    public void setUserOffline(String userEmail) {
        try {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

            user.setOnline(false);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);

            log.info("✅ User {} (ID: {}) set to OFFLINE", userEmail, user.getId());
        } catch (Exception e) {
            log.error("❌ Failed to set user offline for email {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Đánh dấu user offline khi logout (by userId)
     */
    public void setUserOffline(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            user.setOnline(false);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);

            log.debug("User {} set to OFFLINE", userId);
        } catch (Exception e) {
            log.error("❌ Failed to set user {} offline: {}", userId, e.getMessage());
        }
    }

    /**
     * Cập nhật thời gian hoạt động cuối by email
     */
    public void updateLastSeen(String userEmail) {
        try {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);

            log.debug("Updated last seen for user: {}", userEmail);
        } catch (Exception e) {
            log.error("❌ Failed to update last seen for user {}: {}", userEmail, e.getMessage());
        }
    }

    /**
     * Cập nhật thời gian hoạt động cuối by userId
     */
    public void updateLastSeen(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);

            log.debug("Updated last seen for user: {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to update last seen for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Kiểm tra trạng thái online (offline nếu không hoạt động >5 phút)
     * KHÔNG tự động set offline - chỉ kiểm tra trạng thái
     */
    public boolean isUserOnline(Long userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return false;
            }

            // Nếu user chưa được set online (chưa login) thì return false
            if (!user.isOnline()) {
                return false;
            }

            // Check last seen time - nếu null thì coi như vừa online
            LocalDateTime lastSeen = user.getLastSeen();
            if (lastSeen == null) {
                // Nếu user được set online nhưng chưa có lastSeen, coi như đang online
                log.debug("User {} is online but no lastSeen time, considering as online", userId);
                return true;
            }

            LocalDateTime now = LocalDateTime.now();
            long minutesSinceLastSeen = ChronoUnit.MINUTES.between(lastSeen, now);

            boolean isOnline = minutesSinceLastSeen <= ONLINE_TIMEOUT_MINUTES;

            // Log để debug
            log.debug("User {} - Minutes since last seen: {}, Timeout: {}, Is online: {}",
                     userId, minutesSinceLastSeen, ONLINE_TIMEOUT_MINUTES, isOnline);

            // KHÔNG tự động set offline ở đây nữa - để heartbeat mechanism handle
            // if (!isOnline && user.isOnline()) {
            //     log.info("⏰ Auto setting user {} offline due to inactivity ({}m)", userId, minutesSinceLastSeen);
            //     setUserOffline(userId);
            // }

            return isOnline;

        } catch (Exception e) {
            log.error("❌ Failed to check online status for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Trả về: "online", "away" (vừa offline), "offline"
     */
    public String getOnlineStatus(Long userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return "offline";
            }

            // Check if currently online
            if (isUserOnline(userId)) {
                return "online";
            }

            // Check last seen for "away" status
            LocalDateTime lastSeen = user.getLastSeen();
            if (lastSeen == null) {
                return "offline";
            }

            LocalDateTime now = LocalDateTime.now();
            long minutesSinceLastSeen = ChronoUnit.MINUTES.between(lastSeen, now);

            // Away if offline for less than 30 minutes
            if (minutesSinceLastSeen <= 30) {
                return "away";
            }

            return "offline";

        } catch (Exception e) {
            log.error("❌ Failed to get online status for user {}: {}", userId, e.getMessage());
            return "offline";
        }
    }

    /**
     * Get last seen time
     */
    public LocalDateTime getLastSeen(Long userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            return user != null ? user.getLastSeen() : null;
        } catch (Exception e) {
            log.error("❌ Failed to get last seen for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Get all online user IDs
     */
    public List<Long> getOnlineUserIds() {
        try {
            return userRepository.findByOnlineTrue().stream()
                    .map(User::getId)
                    .toList();
        } catch (Exception e) {
            log.error("❌ Failed to get online users: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Tự động set offline users không hoạt động
     * Chạy mỗi 5 phút
     * TẠMTHỜI TẮT ĐỂ DEBUG VẤN ĐỀ ONLINE STATUS
     */
    // @Scheduled(fixedRate = 5 * 60 * 1000) // 5 minutes - COMMENTED OUT FOR DEBUGGING
    public void cleanupOfflineUsers() {
        try {
            log.debug("🧹 Starting cleanup of offline users...");

            List<User> onlineUsers = userRepository.findByOnlineTrue();
            if (onlineUsers.isEmpty()) {
                log.debug("No online users to cleanup");
                return;
            }

            int cleanedUp = 0;
            LocalDateTime now = LocalDateTime.now();

            for (User user : onlineUsers) {
                try {
                    LocalDateTime lastSeen = user.getLastSeen();
                    if (lastSeen == null) {
                        setUserOffline(user.getId());
                        cleanedUp++;
                        continue;
                    }

                    long minutesSinceLastSeen = ChronoUnit.MINUTES.between(lastSeen, now);

                    if (minutesSinceLastSeen > ONLINE_TIMEOUT_MINUTES) {
                        setUserOffline(user.getId());
                        cleanedUp++;
                        log.debug("Cleaned up offline user: {} (last seen {} minutes ago)", user.getId(), minutesSinceLastSeen);
                    }

                } catch (Exception e) {
                    log.warn("Failed to cleanup user {}: {}", user.getId(), e.getMessage());
                }
            }

            if (cleanedUp > 0) {
                log.info("🧹 Cleaned up {} offline users", cleanedUp);
            } else {
                log.debug("🧹 No users needed cleanup");
            }

        } catch (Exception e) {
            log.error("❌ Failed to cleanup offline users: {}", e.getMessage());
        }
    }

    /**
     * Heartbeat - update user activity VÀ đảm bảo user online
     */
    public void heartbeat(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            // Cập nhật cả lastSeen VÀ online status
            user.setLastSeen(LocalDateTime.now());

            // Đảm bảo user được set online khi có hoạt động
            if (!user.isOnline()) {
                user.setOnline(true);
                log.info("🔄 Heartbeat: Setting user {} to ONLINE due to activity", userId);
            }

            userRepository.save(user);
            log.debug("💓 Heartbeat updated for user: {} (online: {}, lastSeen: {})",
                     userId, user.isOnline(), user.getLastSeen());

        } catch (Exception e) {
            log.error("❌ Failed heartbeat for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Heartbeat by email - update user activity VÀ đảm bảo user online
     */
    public void heartbeat(String userEmail) {
        try {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
            heartbeat(user.getId());
        } catch (Exception e) {
            log.error("❌ Failed heartbeat for user {}: {}", userEmail, e.getMessage());
        }
    }
}
