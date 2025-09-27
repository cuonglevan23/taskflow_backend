package com.example.taskmanagement_backend.dtos.MeetingDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMeetingRequestDto {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Meeting time is required")
    private LocalDateTime meetingTime;

    private String location;

    @NotNull(message = "Creator ID is required")
    private Long createdById;
}
