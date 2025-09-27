package com.example.taskmanagement_backend.dtos.UserDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequestDto implements Serializable {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    @NotNull
    private List<Long> roleIds;


    private Long organizationId;
}
