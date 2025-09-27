package com.example.taskmanagement_backend.dtos.MeetingDto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMeetingRequestDto {

    private String title;

    private String description;

    private LocalDateTime meetingTime;

    private String location;
}
