package com.example.taskmanagement_backend.dtos.ProjectInvitatinDto;


import com.example.taskmanagement_backend.enums.InvitationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateProjectInvitationStatusRequestDto {

    @NotNull(message = "status không được để trống")
    private InvitationStatus status;
}
