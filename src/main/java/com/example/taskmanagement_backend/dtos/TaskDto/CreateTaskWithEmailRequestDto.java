package com.example.taskmanagement_backend.dtos.TaskDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskWithEmailRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Status is required")
    private String status;

    @NotBlank(message = "Priority is required")
    private String priority;

    private LocalDate startDate;

    private LocalDate deadline;

    // Không bắt buộc nữa
    private Long projectId;

    private Long groupId;

    // Danh sách email của người được assign task với validation
    @NotEmpty(message = "At least one email is required for task assignment")
    private List<@Email(message = "Invalid email format") @NotBlank(message = "Email cannot be blank") String> assignedToEmails;

    @NotNull(message = "Creator ID is required")
    private Long creatorId;
}
