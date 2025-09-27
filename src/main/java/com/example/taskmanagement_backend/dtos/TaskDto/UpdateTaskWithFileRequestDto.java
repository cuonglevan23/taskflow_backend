package com.example.taskmanagement_backend.dtos.TaskDto;

import jakarta.validation.constraints.Email;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Enhanced UpdateTaskRequestDto with file upload support
 * Supports both multipart file uploads and S3 file keys
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskWithFileRequestDto {

    private String title;
    private String description;
    private String status;
    private String priority;
    private LocalDate startDate;
    private LocalDate deadline;
    private String comment;

    // File handling options (use either fileKeys or files, not both)
    private List<String> fileKeys;           // S3 file keys from presigned uploads
    private List<MultipartFile> files;       // Direct file uploads
    private List<String> filesToDelete;      // File keys to delete

    // Existing assignee management
    private List<Long> assignedToIds;
    private List<@Email(message = "Invalid email format") String> assignedToEmails;
    private List<Long> addAssigneeIds;
    private List<@Email(message = "Invalid email format") String> addAssigneeEmails;
    private List<Long> removeAssigneeIds;
    private List<@Email(message = "Invalid email format") String> removeAssigneeEmails;
    private Long groupId;
}
