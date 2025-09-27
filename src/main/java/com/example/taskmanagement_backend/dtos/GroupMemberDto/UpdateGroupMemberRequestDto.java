package com.example.taskmanagement_backend.dtos.GroupMemberDto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupMemberRequestDto {

    @NotNull(message = "User ID is required")
    private Long userId;
}
