package com.example.taskmanagement_backend.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 🕐 Utility class for formatting time display in chat messages
 * Provides various time formatting options for read receipts and timestamps
 */
@Component
public class ChatTimeFormatter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Format time as HH:mm (24-hour format)
     * Example: "14:30", "09:15"
     */
    public String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(TIME_FORMATTER);
    }

    /**
     * Format date as dd/MM/yyyy
     * Example: "10/09/2025"
     */
    public String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(DATE_FORMATTER);
    }

    /**
     * Format full datetime as dd/MM/yyyy HH:mm
     * Example: "10/09/2025 14:30"
     */
    public String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(DATETIME_FORMATTER);
    }

    /**
     * Format relative time for better UX
     * Examples: "Vừa xem", "5 phút trước", "2 giờ trước", "Hôm qua 14:30"
     */
    public String formatRelativeTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;

        LocalDateTime now = LocalDateTime.now();
        long minutesAgo = ChronoUnit.MINUTES.between(dateTime, now);
        long hoursAgo = ChronoUnit.HOURS.between(dateTime, now);
        long daysAgo = ChronoUnit.DAYS.between(dateTime, now);

        if (minutesAgo < 1) {
            return "Vừa xem";
        } else if (minutesAgo < 60) {
            return minutesAgo + " phút trước";
        } else if (hoursAgo < 24) {
            return hoursAgo + " giờ trước";
        } else if (daysAgo == 1) {
            return "Hôm qua " + formatTime(dateTime);
        } else if (daysAgo < 7) {
            return daysAgo + " ngày trước";
        } else {
            return formatDate(dateTime);
        }
    }

    /**
     * Check if datetime is today
     */
    public boolean isToday(LocalDateTime dateTime) {
        if (dateTime == null) return false;
        LocalDateTime now = LocalDateTime.now();
        return dateTime.toLocalDate().equals(now.toLocalDate());
    }

    /**
     * Check if datetime is recent (within last 5 minutes)
     */
    public boolean isRecent(LocalDateTime dateTime) {
        if (dateTime == null) return false;
        LocalDateTime now = LocalDateTime.now();
        return ChronoUnit.MINUTES.between(dateTime, now) <= 5;
    }

    /**
     * Calculate minutes between two datetimes
     */
    public Long calculateMinutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return null;
        return ChronoUnit.MINUTES.between(start, end);
    }

    /**
     * Smart format for message timestamps
     * - If today: "14:30"
     * - If yesterday: "Hôm qua 14:30"
     * - If this week: "Thứ 2 14:30"
     * - If older: "10/09/2025"
     */
    public String formatMessageTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;

        LocalDateTime now = LocalDateTime.now();
        long daysAgo = ChronoUnit.DAYS.between(dateTime, now);

        if (isToday(dateTime)) {
            return formatTime(dateTime);
        } else if (daysAgo == 1) {
            return "Hôm qua " + formatTime(dateTime);
        } else if (daysAgo < 7) {
            String dayOfWeek = getDayOfWeek(dateTime);
            return dayOfWeek + " " + formatTime(dateTime);
        } else {
            return formatDate(dateTime);
        }
    }

    /**
     * Get Vietnamese day of week
     */
    private String getDayOfWeek(LocalDateTime dateTime) {
        return switch (dateTime.getDayOfWeek()) {
            case MONDAY -> "Thứ 2";
            case TUESDAY -> "Thứ 3";
            case WEDNESDAY -> "Thứ 4";
            case THURSDAY -> "Thứ 5";
            case FRIDAY -> "Thứ 6";
            case SATURDAY -> "Thứ 7";
            case SUNDAY -> "Chủ nhật";
        };
    }

    /**
     * Format read status display
     * Example: "Đã xem 14:30", "Đã xem vừa xong"
     */
    public String formatReadStatus(LocalDateTime readAt) {
        if (readAt == null) return "Chưa xem";

        if (isRecent(readAt)) {
            return "Đã xem vừa xong";
        } else if (isToday(readAt)) {
            return "Đã xem " + formatTime(readAt);
        } else {
            return "Đã xem " + formatMessageTime(readAt);
        }
    }

    /**
     * Format delivery status display
     */
    public String formatDeliveryStatus(LocalDateTime deliveredAt) {
        if (deliveredAt == null) return "Đang gửi...";

        if (isRecent(deliveredAt)) {
            return "Đã gửi";
        } else if (isToday(deliveredAt)) {
            return "Đã gửi " + formatTime(deliveredAt);
        } else {
            return "Đã gửi " + formatMessageTime(deliveredAt);
        }
    }
}
