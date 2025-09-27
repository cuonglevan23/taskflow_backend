package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.repositories.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {

    private final UserJpaRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    /**
     * Kiểm tra xem user có cần UI tour không
     */
    public OnboardingInfo checkOnboardingStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile profile = user.getUserProfile();

        // Xác định user có cần UI tour dựa trên nhiều yếu tố
        boolean needsUITour = determineIfNeedsUITour(user, profile);
        boolean isFirstLogin = user.isFirstLogin();

        OnboardingStep currentStep = determineCurrentOnboardingStep(user, profile);

        return OnboardingInfo.builder()
                .userId(userId)
                .isFirstLogin(isFirstLogin)
                .needsUITour(needsUITour)
                .currentStep(currentStep)
                .completedSteps(getCompletedSteps(user, profile))
                .onboardingProgress(calculateOnboardingProgress(user, profile))
                .shouldShowTour(needsUITour && isFirstLogin)
                .tourType(determineTourType(user, profile))
                .build();
    }

    /**
     * Xác định user có cần UI tour không
     */
    private boolean determineIfNeedsUITour(User user, UserProfile profile) {
        // Điều kiện để show UI tour:
        // 1. First login = true (lần đầu đăng nhập)
        // 2. Profile chưa hoàn thiện
        // 3. Chưa có tasks/projects nào
        // 4. Trial còn active (để giới thiệu premium features)

        if (!user.isFirstLogin()) {
            log.info("👤 User {} đã đăng nhập trước đó - Skip UI tour", user.getEmail());
            return false;
        }

        if (profile == null) {
            log.info("👤 User {} chưa có profile - Cần UI tour", user.getEmail());
            return true;
        }

        // Kiểm tra profile có đầy đủ thông tin không
        boolean profileIncomplete = isProfileIncomplete(profile);

        // Kiểm tra user có trial active không
        boolean hasActiveTrial = profile.getIsPremium() != null &&
                                profile.getIsPremium() &&
                                "trial".equals(profile.getPremiumPlanType()) &&
                                profile.getPremiumExpiry() != null &&
                                profile.getPremiumExpiry().isAfter(LocalDateTime.now());

        boolean needsTour = profileIncomplete || hasActiveTrial;

        log.info("👤 User {} - Profile incomplete: {}, Has active trial: {}, Needs tour: {}",
                user.getEmail(), profileIncomplete, hasActiveTrial, needsTour);

        return needsTour;
    }

    /**
     * Kiểm tra profile có thiếu thông tin không
     */
    private boolean isProfileIncomplete(UserProfile profile) {
        return (profile.getFirstName() == null || profile.getFirstName().trim().isEmpty()) ||
               (profile.getLastName() == null || profile.getLastName().trim().isEmpty()) ||
               (profile.getJobTitle() == null || profile.getJobTitle().trim().isEmpty());
    }

    /**
     * Xác định bước onboarding hiện tại
     */
    private OnboardingStep determineCurrentOnboardingStep(User user, UserProfile profile) {
        if (profile == null) {
            return OnboardingStep.PROFILE_SETUP;
        }

        if (isProfileIncomplete(profile)) {
            return OnboardingStep.PROFILE_COMPLETION;
        }

        // Kiểm tra trial status
        if (profile.getIsPremium() != null && profile.getIsPremium() &&
            "trial".equals(profile.getPremiumPlanType())) {
            return OnboardingStep.TRIAL_EXPLORATION;
        }

        return OnboardingStep.DASHBOARD_TOUR;
    }

    /**
     * Lấy danh sách các bước đã hoàn thành
     */
    private String[] getCompletedSteps(User user, UserProfile profile) {
        java.util.List<String> completed = new java.util.ArrayList<>();

        if (user.getCreatedAt() != null) {
            completed.add("ACCOUNT_CREATED");
        }

        if (profile != null && !isProfileIncomplete(profile)) {
            completed.add("PROFILE_COMPLETED");
        }

        if (profile != null && profile.getIsPremium() != null && profile.getIsPremium()) {
            completed.add("TRIAL_ACTIVATED");
        }

        // TODO: Thêm logic kiểm tra tasks, projects đã tạo

        return completed.toArray(new String[0]);
    }

    /**
     * Tính toán progress onboarding (%)
     */
    private int calculateOnboardingProgress(User user, UserProfile profile) {
        int totalSteps = 5; // Total onboarding steps
        int completedSteps = 0;

        if (user.getCreatedAt() != null) completedSteps++; // Account created
        if (profile != null) completedSteps++; // Profile exists
        if (profile != null && !isProfileIncomplete(profile)) completedSteps++; // Profile complete
        if (profile != null && profile.getIsPremium() != null && profile.getIsPremium()) completedSteps++; // Trial active
        if (!user.isFirstLogin()) completedSteps++; // Has logged in before

        return (int) ((double) completedSteps / totalSteps * 100);
    }

    /**
     * Xác định loại tour phù hợp
     */
    private TourType determineTourType(User user, UserProfile profile) {
        if (profile == null || isProfileIncomplete(profile)) {
            return TourType.FULL_ONBOARDING;
        }

        if (profile.getIsPremium() != null && profile.getIsPremium() &&
            "trial".equals(profile.getPremiumPlanType())) {
            return TourType.TRIAL_FEATURES;
        }

        return TourType.BASIC_DASHBOARD;
    }

    /**
     * Hoàn thành UI tour và cập nhật trạng thái user
     */
    @Transactional
    public OnboardingCompletionResult completeTour(Long userId, String tourType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Set firstLogin = false khi hoàn thành tour
        boolean wasFirstLogin = user.isFirstLogin();
        user.setFirstLogin(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("✅ UI tour completed for user: {} - Tour type: {} - Was first login: {}",
                user.getEmail(), tourType, wasFirstLogin);

        return OnboardingCompletionResult.builder()
                .userId(userId)
                .tourCompleted(true)
                .tourType(tourType)
                .wasFirstLogin(wasFirstLogin)
                .completedAt(LocalDateTime.now())
                .nextSteps(getNextStepsAfterTour(user))
                .build();
    }

    /**
     * Bỏ qua UI tour
     */
    @Transactional
    public OnboardingCompletionResult skipTour(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFirstLogin(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("⏭️ UI tour skipped for user: {}", user.getEmail());

        return OnboardingCompletionResult.builder()
                .userId(userId)
                .tourCompleted(false)
                .tourType("SKIPPED")
                .wasFirstLogin(true)
                .completedAt(LocalDateTime.now())
                .nextSteps(getNextStepsAfterTour(user))
                .build();
    }

    /**
     * Lấy các bước tiếp theo sau khi hoàn thành tour
     */
    private String[] getNextStepsAfterTour(User user) {
        UserProfile profile = user.getUserProfile();
        java.util.List<String> nextSteps = new java.util.ArrayList<>();

        if (profile != null && isProfileIncomplete(profile)) {
            nextSteps.add("Complete your profile information");
        }

        nextSteps.add("Create your first project");
        nextSteps.add("Add team members to collaborate");
        nextSteps.add("Set up Google Calendar integration");

        if (profile != null && profile.getIsPremium() != null && profile.getIsPremium()) {
            nextSteps.add("Explore premium features");
        } else {
            nextSteps.add("Consider upgrading to Premium");
        }

        return nextSteps.toArray(new String[0]);
    }

    /**
     * Reset onboarding status (admin only)
     */
    @Transactional
    public void resetOnboardingStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFirstLogin(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("🔄 Onboarding status reset for user: {} - firstLogin set to true", user.getEmail());
    }

    // ===== ENUMS & DTOs =====

    public enum OnboardingStep {
        ACCOUNT_CREATED,     // Tài khoản đã tạo
        PROFILE_SETUP,       // Cần setup profile
        PROFILE_COMPLETION,  // Hoàn thiện profile
        TRIAL_EXPLORATION,   // Khám phá trial features
        DASHBOARD_TOUR,      // Tour dashboard cơ bản
        ONBOARDING_COMPLETE  // Hoàn thành onboarding
    }

    public enum TourType {
        FULL_ONBOARDING,     // Tour đầy đủ cho user hoàn toàn mới
        TRIAL_FEATURES,      // Tour tập trung vào premium features
        BASIC_DASHBOARD,     // Tour cơ bản dashboard
        QUICK_OVERVIEW       // Tour nhanh cho user có kinh nghiệm
    }

    @lombok.Builder
    @lombok.Data
    public static class OnboardingInfo {
        private Long userId;
        private boolean isFirstLogin;
        private boolean needsUITour;
        private OnboardingStep currentStep;
        private String[] completedSteps;
        private int onboardingProgress;
        private boolean shouldShowTour;
        private TourType tourType;
    }

    @lombok.Builder
    @lombok.Data
    public static class OnboardingCompletionResult {
        private Long userId;
        private boolean tourCompleted;
        private String tourType;
        private boolean wasFirstLogin;
        private LocalDateTime completedAt;
        private String[] nextSteps;
    }
}
