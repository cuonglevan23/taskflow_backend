package com.example.taskmanagement_backend.dtos.OrganizationInvitationDto;


import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrganizationInvitationRequestDto {

    @NotBlank
    private String status;
}