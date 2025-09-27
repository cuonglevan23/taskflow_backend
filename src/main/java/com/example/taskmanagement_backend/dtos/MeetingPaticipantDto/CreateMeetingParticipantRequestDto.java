package com.example.taskmanagement_backend.dtos.MeetingPaticipantDto;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMeetingParticipantRequestDto {

    @NotNull(message = "Meeting ID is required")
    private Long meetingId;

    @NotNull(message = "User ID is required")
    private Long userId;

    @Size(max = 50, message = "Status must be at most 50 characters")
    private String status;
}
