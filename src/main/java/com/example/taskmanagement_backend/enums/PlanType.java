package com.example.taskmanagement_backend.enums;

/**
 * Plan types for subscription management
 */
public enum PlanType {
    TRIAL("trial", "14-day Trial", 0.00, 14),
    MONTHLY("monthly", "Monthly Plan", 9.99, 30),
    QUARTERLY("quarterly", "Quarterly Plan", 24.99, 90),
    YEARLY("yearly", "Yearly Plan", 99.99, 365);

    private final String code;
    private final String displayName;
    private final double price;
    private final int durationDays;

    PlanType(String code, String displayName, double price, int durationDays) {
        this.code = code;
        this.displayName = displayName;
        this.price = price;
        this.durationDays = durationDays;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getPrice() {
        return price;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public static PlanType fromCode(String code) {
        if (code == null) return null;
        for (PlanType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown plan type: " + code);
    }
}
