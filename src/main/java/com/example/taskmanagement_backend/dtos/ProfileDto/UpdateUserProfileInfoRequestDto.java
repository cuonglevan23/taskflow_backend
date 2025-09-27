package com.example.taskmanagement_backend.dtos.ProfileDto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserProfileInfoRequestDto {

    @Size(max = 50, message = "First name must be less than 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name must be less than 50 characters")
    private String lastName;

    @Size(max = 100, message = "Job title must be less than 100 characters")
    private String jobTitle;

    @Size(max = 100, message = "Department must be less than 100 characters")
    private String department;

    @Size(max = 1000, message = "About me must be less than 1000 characters")
    private String aboutMe;
}
