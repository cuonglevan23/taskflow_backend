package com.example.taskmanagement_backend.dtos.TaskDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Status is required")
    private String status;

    @NotBlank(message = "Priority is required")
    private String priority;

    private LocalDate startDate;

    private LocalDate deadline;

    // ✅ NEW: Comment field for additional task notes
    private String comment;

    // ✅ NEW: URL file field for file attachments
    private String urlFile;

    // Không bắt buộc nữa
    private Long projectId;

    private Long groupId;

    // Hỗ trợ assign bằng User ID (cách cũ)
    private List<Long> assignedToIds;

    // Hỗ trợ assign bằng Email (cách mới)
    private List<@Email(message = "Invalid email format") String> assignedToEmails;

    @NotNull(message = "Creator ID is required")
    private Long creatorId;

    // ✅ NEW: Google Calendar integration options
    @Builder.Default
    private Boolean createCalendarEvent = false; // Option tạo calendar event khi tạo task
    private String googleAccessToken; // Google OAuth2 access token (optional)
}
