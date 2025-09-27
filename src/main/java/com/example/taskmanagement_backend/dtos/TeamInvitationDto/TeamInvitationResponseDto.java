package com.example.taskmanagement_backend.dtos.TeamInvitationDto;

import com.example.taskmanagement_backend.enums.InvitationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@Builder
public class TeamInvitationResponseDto {
        private Long id;
        private String email;
        private Long teamId;
        private String teamName;
        private Long invitedById;
        private String invitedByName;
        private InvitationStatus status;
        private String token;
        private LocalDateTime createdAt;

}
