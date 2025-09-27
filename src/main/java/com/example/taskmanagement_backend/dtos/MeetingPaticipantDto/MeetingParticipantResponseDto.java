package com.example.taskmanagement_backend.dtos.MeetingPaticipantDto;


import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingParticipantResponseDto {

    private Long id;

    private Long meetingId;

    private Long userId;

    private String status;
}