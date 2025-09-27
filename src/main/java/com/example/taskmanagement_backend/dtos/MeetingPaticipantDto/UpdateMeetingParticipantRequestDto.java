package com.example.taskmanagement_backend.dtos.MeetingPaticipantDto;


import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMeetingParticipantRequestDto {

    @Size(max = 50, message = "Status must be at most 50 characters")
    private String status;
}
