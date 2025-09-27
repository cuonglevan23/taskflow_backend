package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ProfileDto.AccountSettingsDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.UpdateAccountSettingsRequestDto;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.enums.Language;
import com.example.taskmanagement_backend.enums.Theme;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.repositories.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountSettingsService {

    private final UserJpaRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public AccountSettingsDto getUserAccountSettings(Long userId) {
        log.info("🔍 [AccountSettingsService] Getting account settings for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        UserProfile userProfile = user.getUserProfile();
        if (userProfile == null) {
            // Create default profile if not exists
            userProfile = createDefaultUserProfile(user);
        }

        // Build language options
        AccountSettingsDto.LanguageOption[] languageOptions = new AccountSettingsDto.LanguageOption[]{
            AccountSettingsDto.LanguageOption.builder()
                .code(Language.EN.getCode())
                .displayName(Language.EN.getDisplayName())
                .isSelected(userProfile.getPreferredLanguage() == Language.EN)
                .build(),
            AccountSettingsDto.LanguageOption.builder()
                .code(Language.KO.getCode())
                .displayName(Language.KO.getDisplayName())
                .isSelected(userProfile.getPreferredLanguage() == Language.KO)
                .build(),
            AccountSettingsDto.LanguageOption.builder()
                .code(Language.VI.getCode())
                .displayName(Language.VI.getDisplayName())
                .isSelected(userProfile.getPreferredLanguage() == Language.VI)
                .build()
        };

        // Build theme options
        AccountSettingsDto.ThemeOption[] themeOptions = new AccountSettingsDto.ThemeOption[]{
            AccountSettingsDto.ThemeOption.builder()
                .code(Theme.DARK.getCode())
                .displayName(Theme.DARK.getDisplayName())
                .isSelected(userProfile.getPreferredTheme() == Theme.DARK)
                .build(),
            AccountSettingsDto.ThemeOption.builder()
                .code(Theme.LIGHT.getCode())
                .displayName(Theme.LIGHT.getDisplayName())
                .isSelected(userProfile.getPreferredTheme() == Theme.LIGHT)
                .build()
        };

        return AccountSettingsDto.builder()
                .preferredLanguage(userProfile.getPreferredLanguage())
                .preferredTheme(userProfile.getPreferredTheme())
                .availableLanguages(languageOptions)
                .availableThemes(themeOptions)
                .build();
    }

    @Transactional
    public AccountSettingsDto updateUserAccountSettings(Long userId, UpdateAccountSettingsRequestDto request) {
        log.info("🔄 [AccountSettingsService] Updating account settings for user: {} - Language: {}, Theme: {}",
                userId, request.getPreferredLanguage(), request.getPreferredTheme());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        UserProfile userProfile = user.getUserProfile();
        if (userProfile == null) {
            userProfile = createDefaultUserProfile(user);
        }

        // Update settings
        userProfile.setPreferredLanguage(request.getPreferredLanguage());
        userProfile.setPreferredTheme(request.getPreferredTheme());

        userProfileRepository.save(userProfile);

        log.info("✅ [AccountSettingsService] Successfully updated account settings for user: {} - Language: {}, Theme: {}",
                userId, request.getPreferredLanguage(), request.getPreferredTheme());

        // Return updated settings
        return getUserAccountSettings(userId);
    }

    @Transactional
    public AccountSettingsDto resetUserAccountSettings(Long userId) {
        log.info("🔄 [AccountSettingsService] Resetting account settings to defaults for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        UserProfile userProfile = user.getUserProfile();
        if (userProfile == null) {
            userProfile = createDefaultUserProfile(user);
        }

        // Reset to defaults
        userProfile.setPreferredLanguage(Language.EN);
        userProfile.setPreferredTheme(Theme.DARK);

        userProfileRepository.save(userProfile);

        log.info("✅ [AccountSettingsService] Successfully reset account settings for user: {} to defaults (EN, DARK)", userId);

        return getUserAccountSettings(userId);
    }

    private UserProfile createDefaultUserProfile(User user) {
        log.info("🆕 [AccountSettingsService] Creating default user profile for user: {}", user.getId());

        UserProfile defaultProfile = UserProfile.builder()
                .user(user)
                .preferredLanguage(Language.EN)
                .preferredTheme(Theme.DARK)
                .isPremium(false)
                .showEmail(true)
                .showDepartment(true)
                .build();

        return userProfileRepository.save(defaultProfile);
    }
}
