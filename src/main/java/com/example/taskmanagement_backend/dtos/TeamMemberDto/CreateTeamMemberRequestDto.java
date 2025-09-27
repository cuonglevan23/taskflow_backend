package com.example.taskmanagement_backend.dtos.TeamMemberDto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTeamMemberRequestDto {

    @NotNull(message = "Team ID is required")
    private Long teamId;

    @NotBlank(message = "User email is required")
    @Email(message = "Email should be valid")
    @JsonProperty(value = "userEmail", access = JsonProperty.Access.WRITE_ONLY)
    private String userEmail;

    // Setter để hỗ trợ cả "email" field từ frontend
    @JsonProperty("email")
    public void setEmail(String email) {
        this.userEmail = email;
    }

}