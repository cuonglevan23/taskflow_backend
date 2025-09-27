package com.example.taskmanagement_backend.dtos.TeamMemberDto;

import com.example.taskmanagement_backend.enums.TeamRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberResponseDto {

    private Long id;
    private Long teamId;
    private Long userId;
    private String email;
    private TeamRole role;  // Thêm thông tin role
    private LocalDateTime joinedAt;
    private String aboutMe;
    private String department;
    private String jobTitle;
    private String avatarUrl;
    private String firstName;
    private String lastName;
}