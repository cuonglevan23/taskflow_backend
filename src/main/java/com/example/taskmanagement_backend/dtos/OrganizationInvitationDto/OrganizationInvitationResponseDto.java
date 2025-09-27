package com.example.taskmanagement_backend.dtos.OrganizationInvitationDto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationInvitationResponseDto {

    private Long id;
    private String email;
    private Long organizationId;
    private Long invitedBy;
    private String status;
    private String token;
    private LocalDateTime createdAt;
}