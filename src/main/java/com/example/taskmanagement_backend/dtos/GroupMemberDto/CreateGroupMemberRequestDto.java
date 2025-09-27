package com.example.taskmanagement_backend.dtos.GroupMemberDto;


import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupMemberRequestDto {

    @NotNull(message = "Group ID is required")
    private Long groupId;

    @NotNull(message = "User ID is required")
    private Long userId;
}