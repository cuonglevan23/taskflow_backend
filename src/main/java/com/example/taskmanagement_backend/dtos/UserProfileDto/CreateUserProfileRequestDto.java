package com.example.taskmanagement_backend.dtos.UserProfileDto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserProfileRequestDto {

    private String firstName;

    private String lastName;

    private String avtUrl;

    private String status;

    @NotNull
    private Long userId;
}
