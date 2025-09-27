package com.example.taskmanagement_backend.enums;

public enum Language {
    EN("en", "English"),
    KO("ko", "한국어"),
    VI("vi", "Tiếng Việt");

    private final String code;
    private final String displayName;

    Language(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Language fromCode(String code) {
        for (Language language : values()) {
            if (language.code.equals(code)) {
                return language;
            }
        }
        return EN; // Default fallback
    }
}
