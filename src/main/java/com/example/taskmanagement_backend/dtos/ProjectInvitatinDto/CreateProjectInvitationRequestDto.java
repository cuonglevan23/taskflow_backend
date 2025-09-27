package com.example.taskmanagement_backend.dtos.ProjectInvitatinDto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateProjectInvitationRequestDto {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotNull(message = "projectId không được để trống")
    private Long projectId;

//    @NotNull(message = "role_id không được để trống")
//    private Long roleId;

    @NotNull(message = "invitedById không được để trống")
    private Long invitedById;
}