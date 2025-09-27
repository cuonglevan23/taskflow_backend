package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.services.infrastructure.AutomatedEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrialManagementService {

    private final UserJpaRepository userRepository;
    private final AutomatedEmailService emailService;

    /**
     * Khởi tạo trial 14 ngày cho user mới
     */
    @Transactional
    public TrialInfo initializeTrial(User user) {
        UserProfile profile = user.getUserProfile();
        if (profile == null) {
            throw new RuntimeException("User profile not found");
        }

        // Kiểm tra xem user đã từng có trial chưa
        if (hasUsedTrial(profile)) {
            log.warn("⚠️ User {} đã từng sử dụng trial", user.getEmail());
            return TrialInfo.builder()
                    .hasAccess(false)
                    .status(TrialStatus.EXPIRED)
                    .message("Trial đã được sử dụng trước đó")
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime trialEnd = now.plusDays(14);

        profile.setIsPremium(true);
        profile.setPremiumPlanType("trial");
        profile.setPremiumExpiry(trialEnd);

        userRepository.save(user);

        log.info("✅ Trial khởi tạo thành công cho user: {} - Bắt đầu: {} - Kết thúc: {}",
                user.getEmail(), now, trialEnd);

        // Gửi email chào mừng trial
        sendTrialWelcomeEmail(user, trialEnd);

        return TrialInfo.builder()
                .hasAccess(true)
                .status(TrialStatus.ACTIVE)
                .startDate(now)
                .endDate(trialEnd)
                .daysRemaining(14)
                .message("Trial 14 ngày đã được kích hoạt")
                .build();
    }

    /**
     * Kiểm tra trạng thái trial của user
     */
    public TrialInfo checkTrialStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile profile = user.getUserProfile();
        if (profile == null || !Boolean.TRUE.equals(profile.getIsPremium())) {
            return TrialInfo.builder()
                    .hasAccess(false)
                    .status(TrialStatus.EXPIRED)
                    .message("Không có subscription active")
                    .build();
        }

        // Nếu không phải trial thì là paid subscription
        if (!"trial".equals(profile.getPremiumPlanType())) {
            return TrialInfo.builder()
                    .hasAccess(true)
                    .status(TrialStatus.PAID_SUBSCRIPTION)
                    .message("Đang sử dụng gói trả phí")
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = profile.getPremiumExpiry();

        if (endDate == null) {
            return TrialInfo.builder()
                    .hasAccess(false)
                    .status(TrialStatus.EXPIRED)
                    .message("Trial không có ngày hết hạn")
                    .build();
        }

        // Tính toán số ngày còn lại
        long daysRemaining = ChronoUnit.DAYS.between(now, endDate);
        long hoursRemaining = ChronoUnit.HOURS.between(now, endDate);

        if (endDate.isAfter(now)) {
            // Trial còn hiệu lực
            TrialStatus status = daysRemaining <= 3 ? TrialStatus.EXPIRING_SOON : TrialStatus.ACTIVE;

            return TrialInfo.builder()
                    .hasAccess(true)
                    .status(status)
                    .startDate(endDate.minusDays(14)) // Tính ngược lại start date
                    .endDate(endDate)
                    .daysRemaining((int) daysRemaining)
                    .hoursRemaining((int) hoursRemaining)
                    .message(String.format("Trial còn %d ngày (%d giờ)", daysRemaining, hoursRemaining))
                    .build();
        } else {
            // Trial đã hết hạn
            return TrialInfo.builder()
                    .hasAccess(false)
                    .status(TrialStatus.EXPIRED)
                    .startDate(endDate.minusDays(14))
                    .endDate(endDate)
                    .daysRemaining(0)
                    .hoursRemaining(0)
                    .daysOverdue((int) Math.abs(daysRemaining))
                    .message(String.format("Trial đã hết hạn %d ngày trước", Math.abs(daysRemaining)))
                    .build();
        }
    }

    /**
     * Cron job chạy mỗi giờ để kiểm tra và cập nhật trạng thái trial
     */
    @Scheduled(cron = "0 0 * * * ?") // Chạy mỗi giờ
    @Transactional
    public void processTrialStatus() {
        log.info("🕐 Bắt đầu kiểm tra trạng thái trial cho tất cả users...");

        LocalDateTime now = LocalDateTime.now();

        // Lấy tất cả users có trial active
        List<User> trialUsers = userRepository.findTrialUsers(now);

        int expiredCount = 0;
        int warningCount = 0;
        int finalWarningCount = 0;

        for (User user : trialUsers) {
            try {
                processUserTrial(user, now);

                TrialInfo trialInfo = checkTrialStatus(user.getId());

                switch (trialInfo.getStatus()) {
                    case EXPIRED:
                        expiredCount++;
                        break;
                    case EXPIRING_SOON:
                        if (trialInfo.getDaysRemaining() == 1) {
                            finalWarningCount++;
                        } else {
                            warningCount++;
                        }
                        break;
                }

            } catch (Exception e) {
                log.error("❌ Lỗi xử lý trial cho user {}: {}", user.getEmail(), e.getMessage());
            }
        }

        log.info("✅ Hoàn thành kiểm tra trial - Hết hạn: {}, Cảnh báo: {}, Cảnh báo cuối: {}",
                expiredCount, warningCount, finalWarningCount);
    }

    /**
     * Cron job chạy hàng ngày để gửi cảnh báo trial
     */
    @Scheduled(cron = "0 0 9 * * ?") // 9:00 AM mỗi ngày
    @Transactional
    public void sendTrialWarnings() {
        log.info("📧 Gửi cảnh báo trial hàng ngày...");

        LocalDateTime now = LocalDateTime.now();

        // Users có trial sẽ hết hạn trong 3 ngày
        List<User> expiringSoonUsers = userRepository.findTrialExpiringSoon(now, now.plusDays(3));

        for (User user : expiringSoonUsers) {
            try {
                TrialInfo trialInfo = checkTrialStatus(user.getId());

                if (trialInfo.getStatus() == TrialStatus.EXPIRING_SOON) {
                    sendTrialWarningEmail(user, trialInfo);
                }

            } catch (Exception e) {
                log.error("❌ Lỗi gửi cảnh báo trial cho user {}: {}", user.getEmail(), e.getMessage());
            }
        }

        log.info("✅ Hoàn thành gửi cảnh báo trial");
    }

    /**
     * Xử lý trial của một user cụ thể
     */
    private void processUserTrial(User user, LocalDateTime now) {
        UserProfile profile = user.getUserProfile();
        if (profile == null || !"trial".equals(profile.getPremiumPlanType())) {
            return;
        }

        LocalDateTime endDate = profile.getPremiumExpiry();
        if (endDate == null) {
            return;
        }

        // Nếu trial đã hết hạn
        if (endDate.isBefore(now)) {
            log.info("⏰ Trial hết hạn cho user: {} - Expired at: {}", user.getEmail(), endDate);

            // Cập nhật trạng thái
            profile.setIsPremium(false);
            profile.setPremiumPlanType(null); // Có thể giữ lại để track history
            userRepository.save(user);

            // Gửi email thông báo hết hạn
            sendTrialExpiredEmail(user, endDate);
        }
    }

    /**
     * Kiểm tra user đã từng sử dụng trial chưa
     */
    private boolean hasUsedTrial(UserProfile profile) {
        // Logic có thể phức tạp hơn, ví dụ check history table
        // Hiện tại đơn giản: nếu có premiumExpiry và premiumPlanType từng là "trial"
        return profile.getPremiumExpiry() != null &&
               ("trial".equals(profile.getPremiumPlanType()) || profile.getIsPremium() != null);
    }

    /**
     * Gửi email chào mừng trial
     */
    private void sendTrialWelcomeEmail(User user, LocalDateTime trialEnd) {
        try {
            String userName = getUserDisplayName(user);

            emailService.sendTrialWelcomeEmail(
                user.getEmail(),
                userName,
                trialEnd,
                14
            );

            log.info("📧 Đã gửi email chào mừng trial cho: {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Lỗi gửi email chào mừng trial: {}", e.getMessage());
        }
    }

    /**
     * Gửi email cảnh báo trial sắp hết hạn
     */
    private void sendTrialWarningEmail(User user, TrialInfo trialInfo) {
        try {
            String userName = getUserDisplayName(user);

            emailService.sendTrialWarningEmail(
                user.getEmail(),
                userName,
                trialInfo.getEndDate(),
                trialInfo.getDaysRemaining()
            );

            log.info("⚠️ Đã gửi cảnh báo trial cho: {} (còn {} ngày)",
                    user.getEmail(), trialInfo.getDaysRemaining());
        } catch (Exception e) {
            log.error("❌ Lỗi gửi cảnh báo trial: {}", e.getMessage());
        }
    }

    /**
     * Gửi email thông báo trial đã hết hạn
     */
    private void sendTrialExpiredEmail(User user, LocalDateTime expiredDate) {
        try {
            String userName = getUserDisplayName(user);

            emailService.sendTrialExpiredEmail(
                user.getEmail(),
                userName,
                expiredDate
            );

            log.info("💔 Đã gửi thông báo hết hạn trial cho: {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Lỗi gửi thông báo hết hạn trial: {}", e.getMessage());
        }
    }

    private String getUserDisplayName(User user) {
        if (user.getUserProfile() != null) {
            String firstName = user.getUserProfile().getFirstName();
            String lastName = user.getUserProfile().getLastName();

            if (firstName != null && !firstName.trim().isEmpty()) {
                return lastName != null && !lastName.trim().isEmpty()
                    ? firstName + " " + lastName
                    : firstName;
            }
        }
        return user.getEmail().split("@")[0];
    }

    // ===== DTOs =====

    public enum TrialStatus {
        ACTIVE,              // Trial đang hoạt động
        EXPIRING_SOON,       // Trial sắp hết hạn (≤ 3 ngày)
        EXPIRED,             // Trial đã hết hạn
        PAID_SUBSCRIPTION    // Đã upgrade lên gói trả phí
    }

    @lombok.Builder
    @lombok.Data
    public static class TrialInfo {
        private boolean hasAccess;
        private TrialStatus status;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private int daysRemaining;
        private int hoursRemaining;
        private int daysOverdue;
        private String message;
    }
}
