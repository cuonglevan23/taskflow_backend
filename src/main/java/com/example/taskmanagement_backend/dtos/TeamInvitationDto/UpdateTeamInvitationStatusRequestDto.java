package com.example.taskmanagement_backend.dtos.TeamInvitationDto;

import com.example.taskmanagement_backend.enums.InvitationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateTeamInvitationStatusRequestDto {

    @NotNull(message = "status không được để trống")
    private InvitationStatus status;
}
