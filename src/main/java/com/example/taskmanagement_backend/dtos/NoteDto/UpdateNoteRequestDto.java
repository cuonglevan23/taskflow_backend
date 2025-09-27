package com.example.taskmanagement_backend.dtos.NoteDto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNoteRequestDto {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String content; // JSON content

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private Boolean isPublic; // Only for project notes

    private Boolean isArchived;
}
