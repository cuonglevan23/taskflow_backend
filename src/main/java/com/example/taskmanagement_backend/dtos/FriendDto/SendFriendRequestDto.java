package com.example.taskmanagement_backend.dtos.FriendDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendFriendRequestDto {
    @NotNull(message = "Target user ID is required")
    private Long targetUserId;
}
