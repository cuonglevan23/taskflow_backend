package com.example.taskmanagement_backend.dtos.ProjectInvitatinDto;

import com.example.taskmanagement_backend.enums.InvitationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProjectInvitationResponseDto {
    private Long id;
    private String email;
    private Long projectId;
    private String projectName;
    private Long invitedById;
    private String invitedByName;
    private InvitationStatus status;
//    private Long roleId;
    private String token;
    private LocalDateTime createdAt;
}
