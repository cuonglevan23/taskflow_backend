package com.example.taskmanagement_backend.dtos.UserDto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequestDto {
    @NotBlank
    @Email
    private String email;

    private String firstName;

    private String lastName;

    private String status;

    private String avtUrl;

    private LocalDateTime createdAt;
    private boolean firstLogin;


    private LocalDateTime updatedAt;

    private Long organizationId;

    private String organizationName;

    private Set<String> roleNames;
}
