package com.example.taskmanagement_backend.dtos.GoogleCalendarDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventResponseDto {
    private String eventId;
    private String eventUrl;
    private String meetLink;
    private String message;

    // ✅ NEW: Enhanced response fields
    private String title;
    private String description;
    private String startTime; // ISO format: "2025-08-30T14:00:00+07:00"
    private String endTime;   // ISO format: "2025-08-30T16:00:00+07:00"
    private String timezone;

    // ✅ Meeting & collaboration info
    private Boolean hasMeet;
    private Integer attendeeCount;
    private List<String> attendeeEmails;
    private String meetingType; // "quick_meeting", "formal_meeting", "reminder"

    // ✅ Event metadata
    private Boolean hasReminders;
    private List<Integer> reminderMinutes;
    private Boolean isRecurring;
    private String eventColor;
    private String location;

    // ✅ Task integration info
    private Long taskId;
    private String taskTitle;
    private String taskStatus;
    private String taskPriority;

    // ✅ Success/error handling
    private Boolean success;
    private String errorCode;
    private String technicalDetails;

    // ✅ Additional useful info
    private String calendarId;
    private String createdAt;
    private String updatedAt;
    private Boolean isAllDay;
    private Integer durationMinutes;
}
