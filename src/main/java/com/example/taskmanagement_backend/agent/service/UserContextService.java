package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * User Context Service - Tận dụng thông tin user từ hệ thống để cá nhân hóa AI Agent
 * Tích hợp với UserJpaRepository để lấy thông tin user, profile, projects, teams
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserContextService {

    private final UserJpaRepository userRepository;

    /**
     * Lấy thông tin user đầy đủ cho AI Agent
     */
    public UserChatContext getUserChatContext(Long userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("User not found: {}", userId);
                return UserChatContext.createDefault(userId);
            }

            User user = userOpt.get();
            UserProfile profile = user.getUserProfile();

            return UserChatContext.builder()
                .userId(userId)
                .email(user.getEmail())
                .systemRole(user.getSystemRole().name())
                .isOnline(user.isOnline())
                .status(user.getStatus().name())
                .lastSeen(user.getLastSeen())
                .firstName(profile != null ? profile.getFirstName() : null)
                .lastName(profile != null ? profile.getLastName() : null)
                .username(profile != null ? profile.getUsername() : null)
                .jobTitle(profile != null ? profile.getJobTitle() : null)
                .department(profile != null ? profile.getDepartment() : null)
                .isPremium(profile != null ? profile.getIsPremium() : false)
                .premiumPlanType(profile != null ? profile.getPremiumPlanType() : null)
                .organizationId(user.getOrganization() != null ? user.getOrganization().getId() : null)
                .organizationName(user.getOrganization() != null ? user.getOrganization().getName() : null)
                .defaultWorkspaceId(user.getDefaultWorkspace() != null ? user.getDefaultWorkspace().getId() : null)
                .defaultWorkspaceName(user.getDefaultWorkspace() != null ? user.getDefaultWorkspace().getName() : null)
                .isFirstLogin(user.isFirstLogin())
                .build();

        } catch (Exception e) {
            log.error("Error getting user context for userId: {}", userId, e);
            return UserChatContext.createDefault(userId);
        }
    }

    /**
     * Tạo context string cho AI prompt dựa trên thông tin user
     */
    public String buildUserContextPrompt(Long userId) {
        UserChatContext context = getUserChatContext(userId);

        StringBuilder promptContext = new StringBuilder();
        promptContext.append("=== THÔNG TIN USER ===\n");

        // Basic info
        if (context.getFirstName() != null || context.getLastName() != null) {
            String fullName = String.format("%s %s",
                context.getFirstName() != null ? context.getFirstName() : "",
                context.getLastName() != null ? context.getLastName() : "").trim();
            promptContext.append("Tên: ").append(fullName).append("\n");
        }

        if (context.getUsername() != null) {
            promptContext.append("Username: ").append(context.getUsername()).append("\n");
        }

        // Role & Department
        promptContext.append("Vai trò hệ thống: ").append(context.getSystemRole()).append("\n");

        if (context.getJobTitle() != null) {
            promptContext.append("Chức vụ: ").append(context.getJobTitle()).append("\n");
        }

        if (context.getDepartment() != null) {
            promptContext.append("Phòng ban: ").append(context.getDepartment()).append("\n");
        }

        // Organization info
        if (context.getOrganizationName() != null) {
            promptContext.append("Tổ chức: ").append(context.getOrganizationName()).append("\n");
        }

        if (context.getDefaultWorkspaceName() != null) {
            promptContext.append("Workspace mặc định: ").append(context.getDefaultWorkspaceName()).append("\n");
        }

        // Premium status
        if (context.getIsPremium()) {
            promptContext.append("Tài khoản Premium: Có (").append(context.getPremiumPlanType()).append(")\n");
        } else {
            promptContext.append("Tài khoản Premium: Không\n");
        }

        // Special cases
        if (context.getIsFirstLogin()) {
            promptContext.append("Lưu ý: Đây là lần đầu user đăng nhập - cần hướng dẫn cơ bản\n");
        }

        if ("ADMIN".equals(context.getSystemRole()) || "SUPER_ADMIN".equals(context.getSystemRole())) {
            promptContext.append("Lưu ý: User có quyền quản trị - có thể truy cập các tính năng admin\n");
        }

        promptContext.append("=== KẾT THÚC THÔNG TIN USER ===\n\n");

        return promptContext.toString();
    }

    /**
     * Cập nhật last seen cho user khi chat
     */
    public void updateUserLastSeen(Long userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setLastSeen(LocalDateTime.now());
                user.setOnline(true);
                userRepository.save(user);
                log.debug("Updated last seen for user: {}", userId);
            }
        } catch (Exception e) {
            log.error("Error updating last seen for user: {}", userId, e);
        }
    }

    /**
     * Kiểm tra user có quyền truy cập tính năng premium không
     */
    public boolean hasFeatureAccess(Long userId, String feature) {
        UserChatContext context = getUserChatContext(userId);

        // Admin luôn có quyền truy cập
        if ("ADMIN".equals(context.getSystemRole()) || "SUPER_ADMIN".equals(context.getSystemRole())) {
            return true;
        }

        // Premium features
        if ("ADVANCED_AI_FEATURES".equals(feature) ||
            "UNLIMITED_CONTEXT".equals(feature) ||
            "PRIORITY_SUPPORT".equals(feature)) {
            return context.getIsPremium();
        }

        // Basic features cho tất cả users
        return true;
    }

    /**
     * Tạo personalized greeting message
     */
    public String createPersonalizedGreeting(Long userId) {
        UserChatContext context = getUserChatContext(userId);

        StringBuilder greeting = new StringBuilder();

        if (context.getFirstName() != null) {
            greeting.append("Xin chào ").append(context.getFirstName());
        } else if (context.getUsername() != null) {
            greeting.append("Xin chào ").append(context.getUsername());
        } else {
            greeting.append("Xin chào");
        }

        if (context.getIsFirstLogin()) {
            greeting.append("! 🎉 Chào mừng bạn đến với Taskflow! Tôi là AI Assistant sẽ hỗ trợ bạn quản lý dự án và task.");
        } else {
            greeting.append("! Tôi có thể hỗ trợ gì cho bạn hôm nay?");
        }

        if (context.getIsPremium()) {
            greeting.append(" (Premium User - bạn có quyền truy cập đầy đủ tính năng)");
        }

        return greeting.toString();
    }

    /**
     * DTO chứa thông tin user cho chat context
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserChatContext {
        private Long userId;
        private String email;
        private String systemRole;
        private boolean isOnline;
        private String status;
        private LocalDateTime lastSeen;
        private String firstName;
        private String lastName;
        private String username;
        private String jobTitle;
        private String department;
        private Boolean isPremium;
        private String premiumPlanType;
        private Long organizationId;
        private String organizationName;
        private Long defaultWorkspaceId;
        private String defaultWorkspaceName;
        private Boolean isFirstLogin;

        public static UserChatContext createDefault(Long userId) {
            return UserChatContext.builder()
                .userId(userId)
                .systemRole("MEMBER")
                .isOnline(true)
                .status("ACTIVE")
                .isPremium(false)
                .isFirstLogin(false)
                .build();
        }
    }
}
