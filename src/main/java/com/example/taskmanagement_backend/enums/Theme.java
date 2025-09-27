package com.example.taskmanagement_backend.enums;

public enum Theme {
    DARK("dark", "Dark Theme"),
    LIGHT("light", "Light Theme");

    private final String code;
    private final String displayName;

    Theme(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Theme fromCode(String code) {
        for (Theme theme : values()) {
            if (theme.code.equals(code)) {
                return theme;
            }
        }
        return DARK; // Default fallback
    }
}
