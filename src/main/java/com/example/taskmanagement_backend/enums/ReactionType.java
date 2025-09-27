package com.example.taskmanagement_backend.enums;

/**
 * Enum for different types of message reactions
 * Supports common social media reaction types
 */
public enum ReactionType {
    LIKE("👍", "Like"),
    LOVE("❤️", "Love"),
    LAUGH("😂", "Laugh"),
    WOW("😮", "Wow"),
    SAD("😢", "Sad"),
    ANGRY("😠", "Angry");

    private final String emoji;
    private final String displayName;

    ReactionType(String emoji, String displayName) {
        this.emoji = emoji;
        this.displayName = displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getDisplayName() {
        return displayName;
    }
}
