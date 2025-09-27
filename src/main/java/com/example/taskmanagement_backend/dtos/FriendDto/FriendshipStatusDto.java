package com.example.taskmanagement_backend.dtos.FriendDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipStatusDto {
    private String status; // SELF, NONE, FRIENDS, REQUEST_SENT, REQUEST_RECEIVED, BLOCKED
    private boolean canSendRequest;
    private boolean canAcceptRequest;
    private boolean canCancelRequest;
    private Long requestId;
}
