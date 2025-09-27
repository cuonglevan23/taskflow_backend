package com.example.taskmanagement_backend.dtos.TeamInvitationDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTeamInvitationRequestDto {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotNull(message = "teamId không được để trống")
    private Long teamId;

    @NotNull(message = "invitedById không được để trống")
    private Long invitedById;
}
