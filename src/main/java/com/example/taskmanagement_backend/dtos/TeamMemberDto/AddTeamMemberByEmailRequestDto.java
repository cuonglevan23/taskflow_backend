package com.example.taskmanagement_backend.dtos.TeamMemberDto;

import com.example.taskmanagement_backend.enums.TeamRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddTeamMemberByEmailRequestDto {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Builder.Default
    private TeamRole role = TeamRole.MEMBER; // Đặt giá trị mặc định là MEMBER
}
