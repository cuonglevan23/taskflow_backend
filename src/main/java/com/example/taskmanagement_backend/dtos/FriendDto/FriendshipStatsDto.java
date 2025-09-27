package com.example.taskmanagement_backend.dtos.FriendDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipStatsDto {
    private long totalFriends;
    private long pendingRequests;
    private long sentRequests;
    private long blockedUsers;
    private long mutualFriendsWithMostConnectedUser;
    private String mostConnectedFriendName;
}
