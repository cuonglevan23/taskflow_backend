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
public class CreateCalendarEventRequestDto {
    private Long taskId;
    private String accessToken; // Google OAuth2 access token

    // ✅ NEW: Enhanced fields for advanced calendar features
    private String customTitle; // Custom title thay vì dùng task title
    private String customDescription; // Custom description thay vì dùng task description
    private String customStartTime; // Custom start time (ISO format: "2025-08-30T14:00:00")
    private String customEndTime; // Custom end time (ISO format: "2025-08-30T16:00:00")
    private Integer durationMinutes; // Duration in minutes (alternative to customEndTime)

    // ✅ Meeting & collaboration features
    private Boolean createMeet; // Có tạo Google Meet link không
    private List<String> attendeeEmails; // Danh sách email người tham gia
    private String meetingType; // "quick_meeting", "formal_meeting", "reminder"

    // ✅ Reminder & notification settings
    private Boolean isReminder; // Có phải là reminder không
    private List<Integer> reminderMinutes; // Nhắc nhở trước bao nhiêu phút [15, 30, 60]
    private String reminderType; // "popup", "email"

    // ✅ Calendar customization
    private String calendarId; // Calendar ID (default: "primary")
    private String eventColor; // Màu sự kiện (1-11)
    private String visibility; // "default", "public", "private"
    private String transparency; // "opaque", "transparent"

    // ✅ Recurring events
    private Boolean isRecurring; // Có lặp lại không
    private String recurrenceRule; // RRULE theo RFC 5545
    private String recurrenceEnd; // Ngày kết thúc lặp lại

    // ✅ Location & additional info
    private String location; // Địa điểm
    private String timezone; // Timezone (default: "Asia/Ho_Chi_Minh")
    private Boolean allDay; // Sự kiện cả ngày

    // ✅ Integration metadata
    private String sourceApp; // "TaskFlow" - để track nguồn tạo
    private String taskUrl; // URL đến task trong app
    private Boolean autoSync; // Tự động sync khi task thay đổi
}
