package com.example.taskmanagement_backend.dtos.TaskAttachmentDto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskAttachmentRequestDto {

    @NotBlank(message = "File URL is required")
    private String fileUrl;
}
