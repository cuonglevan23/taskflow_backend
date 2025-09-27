package com.example.taskmanagement_backend.dtos.TaskAttachmentDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskAttachmentRequestDto {

    @NotNull(message = "Task ID is required")
    private Long taskId;

    @NotBlank(message = "File URL is required")
    private String fileUrl;

    @NotNull(message = "Uploader ID is required")
    private Long uploadedById;
}
