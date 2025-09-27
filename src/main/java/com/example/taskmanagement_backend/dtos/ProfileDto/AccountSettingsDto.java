package com.example.taskmanagement_backend.dtos.ProfileDto;

import com.example.taskmanagement_backend.enums.Language;
import com.example.taskmanagement_backend.enums.Theme;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSettingsDto {
    private Language preferredLanguage;
    private Theme preferredTheme;

    // Language options for frontend
    private LanguageOption[] availableLanguages;

    // Theme options for frontend
    private ThemeOption[] availableThemes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LanguageOption {
        private String code;
        private String displayName;
        private boolean isSelected;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThemeOption {
        private String code;
        private String displayName;
        private boolean isSelected;
    }
}
