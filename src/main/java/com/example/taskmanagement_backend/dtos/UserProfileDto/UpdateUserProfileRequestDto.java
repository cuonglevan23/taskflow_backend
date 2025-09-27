package com.example.taskmanagement_backend.dtos.UserProfileDto;


import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserProfileRequestDto {

    private String firstName;

    private String lastName;

    private String username;

    private String jobTitle;

    private String department;

    private String aboutMe;

    private String avtUrl;

}
