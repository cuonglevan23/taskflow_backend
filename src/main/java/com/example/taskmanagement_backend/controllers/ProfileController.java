package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.ProfileDto.AccountSettingsDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.TaskPriorityDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.TaskStatusDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.UpdateAccountSettingsRequestDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.UpdateTaskPriorityRequestDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.UpdateTaskStatusRequestDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.UpdateUserProfileInfoRequestDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.UserProfileInfoDto;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.services.AccountSettingsService;
import com.example.taskmanagement_backend.services.ProfileInfoService;
import com.example.taskmanagement_backend.services.UserTaskConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Profile", description = "User profile customization APIs")
public class ProfileController {

    private final UserTaskConfigService userTaskConfigService;
    private final AccountSettingsService accountSettingsService;
    private final ProfileInfoService profileInfoService;
    private final UserJpaRepository userRepository;

    // ✅ NEW: Profile Info Endpoints
    @GetMapping("/info")
    @Operation(summary = "Get user's profile information",
               description = "Get user's personal information including name, job title, department and about me")
    public ResponseEntity<UserProfileInfoDto> getUserProfileInfo(Authentication authentication) {
        try {
            Long userId = getUserIdFromAuth(authentication);
            log.info("🔍 [ProfileController] Getting profile info for user: {}", userId);

            UserProfileInfoDto profileInfo = profileInfoService.getUserProfileInfo(userId);
            return ResponseEntity.ok(profileInfo);
        } catch (Exception e) {
            log.error("❌ [ProfileController] Error getting profile info: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/info")
    @Operation(summary = "Update user's profile information",
               description = "Update user's personal information including name, job title, department and about me")
    public ResponseEntity<UserProfileInfoDto> updateUserProfileInfo(
            Authentication authentication,
            @Valid @RequestBody UpdateUserProfileInfoRequestDto request) {
        try {
            Long userId = getUserIdFromAuth(authentication);
            log.info("🔄 [ProfileController] Updating profile info for user: {} - Name: {}, Job Title: {}",
                    userId, request.getFirstName(), request.getJobTitle());

            UserProfileInfoDto updatedInfo = profileInfoService.updateUserProfileInfo(userId, request);

            log.info("✅ [ProfileController] Successfully updated profile info for user: {}", userId);
            return ResponseEntity.ok(updatedInfo);
        } catch (Exception e) {
            log.error("❌ [ProfileController] Error updating profile info: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update user's avatar",
               description = "Upload and update user's profile picture")
    public ResponseEntity<UserProfileInfoDto> updateUserAvatar(
            Authentication authentication,
            @RequestParam("avatar") MultipartFile avatarFile) {
        try {
            Long userId = getUserIdFromAuth(authentication);
            log.info("🔄 [ProfileController] Updating avatar for user: {} - File size: {} bytes",
                    userId, avatarFile.getSize());

            if (avatarFile.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Kiểm tra loại file
            String contentType = avatarFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                log.warn("Invalid file type: {}", contentType);
                return ResponseEntity.badRequest().build();
            }

            UserProfileInfoDto updatedInfo = profileInfoService.updateUserAvatar(userId, avatarFile);

            log.info("✅ [ProfileController] Successfully updated avatar for user: {}", userId);
            return ResponseEntity.ok(updatedInfo);
        } catch (Exception e) {
            log.error("❌ [ProfileController] Error updating avatar: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    // ✅ NEW: Account Settings Endpoints
    @GetMapping("/account-settings")
    @Operation(summary = "Get user's account settings",
               description = "Get user's language and theme preferences with available options")
    public ResponseEntity<AccountSettingsDto> getUserAccountSettings(Authentication authentication) {
        try {
            Long userId = getUserIdFromAuth(authentication);
            log.info("🔍 [ProfileController] Getting account settings for user: {}", userId);

            AccountSettingsDto settings = accountSettingsService.getUserAccountSettings(userId);
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            log.error("❌ [ProfileController] Error getting account settings: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/account-settings")
    @Operation(summary = "Update user's account settings",
               description = "Update user's language and theme preferences")
    public ResponseEntity<AccountSettingsDto> updateUserAccountSettings(
            Authentication authentication,
            @Valid @RequestBody UpdateAccountSettingsRequestDto request) {
        try {
            Long userId = getUserIdFromAuth(authentication);
            log.info("🔄 [ProfileController] Updating account settings for user: {} - Language: {}, Theme: {}",
                    userId, request.getPreferredLanguage(), request.getPreferredTheme());

            AccountSettingsDto updatedSettings = accountSettingsService.updateUserAccountSettings(userId, request);

            log.info("✅ [ProfileController] Successfully updated account settings for user: {}", userId);
            return ResponseEntity.ok(updatedSettings);
        } catch (Exception e) {
            log.error("❌ [ProfileController] Error updating account settings: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/account-settings")
    @Operation(summary = "Reset user's account settings to defaults",
               description = "Reset user's language to EN and theme to DARK")
    public ResponseEntity<AccountSettingsDto> resetUserAccountSettings(Authentication authentication) {
        try {
            Long userId = getUserIdFromAuth(authentication);
            log.info("🔄 [ProfileController] Resetting account settings for user: {}", userId);

            AccountSettingsDto defaultSettings = accountSettingsService.resetUserAccountSettings(userId);

            log.info("✅ [ProfileController] Successfully reset account settings for user: {}", userId);
            return ResponseEntity.ok(defaultSettings);
        } catch (Exception e) {
            log.error("❌ [ProfileController] Error resetting account settings: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/task-status")
    @Operation(summary = "Get user's task statuses", 
               description = "Get user's customized task statuses. If not customized, returns default statuses.")
    public ResponseEntity<List<TaskStatusDto>> getUserTaskStatuses(Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        List<TaskStatusDto> statuses = userTaskConfigService.getUserTaskStatuses(userId);
        return ResponseEntity.ok(statuses);
    }

    @PutMapping("/task-status")
    @Operation(summary = "Update user's task statuses", 
               description = "Update user's task status configuration (add, remove, change colors, etc.)")
    public ResponseEntity<List<TaskStatusDto>> updateUserTaskStatuses(
            Authentication authentication,
            @Valid @RequestBody UpdateTaskStatusRequestDto request) {
        Long userId = getUserIdFromAuth(authentication);
        List<TaskStatusDto> updatedStatuses = userTaskConfigService.updateUserTaskStatuses(userId, request);
        return ResponseEntity.ok(updatedStatuses);
    }

    @DeleteMapping("/task-status")
    @Operation(summary = "Reset user's task statuses to defaults", 
               description = "Reset user's task status configuration back to system defaults")
    public ResponseEntity<List<TaskStatusDto>> resetUserTaskStatuses(Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        List<TaskStatusDto> defaultStatuses = userTaskConfigService.resetUserTaskStatuses(userId);
        return ResponseEntity.ok(defaultStatuses);
    }

    @GetMapping("/task-priority")
    @Operation(summary = "Get user's task priorities", 
               description = "Get user's customized task priorities. If not customized, returns default priorities.")
    public ResponseEntity<List<TaskPriorityDto>> getUserTaskPriorities(Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        List<TaskPriorityDto> priorities = userTaskConfigService.getUserTaskPriorities(userId);
        return ResponseEntity.ok(priorities);
    }

    @PutMapping("/task-priority")
    @Operation(summary = "Update user's task priorities", 
               description = "Update user's task priority configuration (add, remove, change colors, etc.)")
    public ResponseEntity<List<TaskPriorityDto>> updateUserTaskPriorities(
            Authentication authentication,
            @Valid @RequestBody UpdateTaskPriorityRequestDto request) {
        Long userId = getUserIdFromAuth(authentication);
        List<TaskPriorityDto> updatedPriorities = userTaskConfigService.updateUserTaskPriorities(userId, request);
        return ResponseEntity.ok(updatedPriorities);
    }

    @DeleteMapping("/task-priority")
    @Operation(summary = "Reset user's task priorities to defaults", 
               description = "Reset user's task priority configuration back to system defaults")
    public ResponseEntity<List<TaskPriorityDto>> resetUserTaskPriorities(Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        List<TaskPriorityDto> defaultPriorities = userTaskConfigService.resetUserTaskPriorities(userId);
        return ResponseEntity.ok(defaultPriorities);
    }

    private Long getUserIdFromAuth(Authentication authentication) {
        try {
            // Proper authentication handling - extract user from JWT token
            if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                // Get email from authentication (stored as username in UserDetails)
                String email = userDetails.getUsername();

                // Find user by email and return the user ID
                return userRepository.findByEmail(email)
                        .map(user -> user.getId())
                        .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
            }
            throw new RuntimeException("Invalid authentication object");
        } catch (Exception e) {
            log.error("Error extracting user ID from authentication: {}", e.getMessage());
            throw new RuntimeException("Could not extract user ID from authentication", e);
        }
    }
}
