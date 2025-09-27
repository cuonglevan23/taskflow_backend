package com.example.taskmanagement_backend.dtos.OrganizationInvitationDto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizationInvitationRequestDto {

    @Email
    @NotBlank
    private String email;

    @NotNull
    private Long organizationId;

    @NotNull
    private Long invitedBy;

    @NotBlank
    private String token;

    @NotBlank
    private String status;
}