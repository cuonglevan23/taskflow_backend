package com.example.taskmanagement_backend.dtos.TaskDto;

import com.example.taskmanagement_backend.dtos.TaskChecklistDto.TaskChecklistResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponseDto {

    private Long id;

    private String title;

    private String description;

    private String status;

    private String priority;

    private LocalDate startDate;

    private LocalDate deadline;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<Long> assignedToIds;

    private List<String> assignedToEmails;

    // ✅ NEW: Comment field for task notes
    private String comment;

    // ✅ NEW: URL file field for file attachments
    private String urlFile;

    // ✅ NEW: Public visibility flag
    private Boolean isPublic;

    // ✅ NEW: Google Calendar Event ID
    private String googleCalendarEventId;

    // ✅ ENHANCED: Google Calendar information for UI display
    private String googleCalendarEventUrl;  // Link để user click vào
    private String googleMeetLink;          // Google Meet link nếu có
    private Boolean isSyncedToCalendar;     // Trạng thái đã sync
    private LocalDateTime calendarSyncedAt; // Thời gian sync lần cuối

    // ✅ NEW: Google Calendar integration status
    @Builder.Default
    private Boolean hasGoogleCalendarPermissions = false;
    @Builder.Default
    private Boolean googleCalendarTokenExpired = false;

    // ✅ NEW: Comment count và latest comment info
    private Long commentCount;

    private String latestCommentContent;

    private String latestCommentUserName;

    private LocalDateTime latestCommentTime;

    private Long groupId;

    private Long projectId;

    private Long creatorId;
    private List<TaskChecklistResponseDto> checklists;

    private UserProfileDto creatorProfile;
    private List<UserProfileDto> assigneeProfiles;

}
