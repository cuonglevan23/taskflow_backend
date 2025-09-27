package com.example.taskmanagement_backend.dtos.NoteDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNoteRequestDto {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String content; // JSON content

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    // Either userId OR projectId should be provided, not both
    private Long userId; // For personal note

    private Long projectId; // For project note

    @Builder.Default
    private Boolean isPublic = false; // Only for project notes
}
