package com.example.taskmanagement_backend.dtos.EmailDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendEmailRequestDto {

    @NotEmpty(message = "At least one recipient email is required")
    @Valid
    private List<@Email(message = "Invalid email format") String> to;

    private List<@Email(message = "Invalid CC email format") String> cc;
    private List<@Email(message = "Invalid BCC email format") String> bcc;

    @NotBlank(message = "Subject is required")
    @Size(max = 255, message = "Subject cannot exceed 255 characters")
    private String subject;

    @NotBlank(message = "Email body is required")
    private String body;

    @Builder.Default
    private Boolean isHtml = false;

    private List<String> attachmentPaths; // S3 paths or local file paths

    private String replyToMessageId; // For reply functionality
}
