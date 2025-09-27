package com.example.taskmanagement_backend.dtos.TaskDto;

import jakarta.validation.constraints.Email;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskRequestDto {

    private String title;

    private String description;

    private String status;

    private String priority;

    private LocalDate startDate;

    private LocalDate deadline;

    // ✅ NEW: Comment field for additional task notes
    private String comment;

    // ✅ NEW: URL file field for file attachments
    private String urlFile;

    // Hỗ trợ update assignees bằng User ID
    private List<Long> assignedToIds;

    // Hỗ trợ update assignees bằng Email
    private List<@Email(message = "Invalid email format") String> assignedToEmails;

    // Các operations cho assignees
    private List<Long> addAssigneeIds;        // Thêm assignees bằng User ID
    private List<@Email(message = "Invalid email format") String> addAssigneeEmails;  // Thêm assignees bằng Email
    private List<Long> removeAssigneeIds;     // Xóa assignees bằng User ID
    private List<@Email(message = "Invalid email format") String> removeAssigneeEmails; // Xóa assignees bằng Email

    private Long groupId;
}
