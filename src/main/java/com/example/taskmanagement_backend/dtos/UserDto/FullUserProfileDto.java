package com.example.taskmanagement_backend.dtos.UserDto;

import com.example.taskmanagement_backend.dtos.FriendDto.FriendDto;
import com.example.taskmanagement_backend.dtos.FriendDto.FriendshipStatusDto;
import com.example.taskmanagement_backend.dtos.PostDto.PostResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullUserProfileDto {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    private String avatarUrl;
    private String department;
    private String jobTitle;
    private String aboutMe;
    private LocalDateTime joinedAt;

    // Relationship info
    private FriendshipStatusDto friendshipStatus;

    // Profile content
    private List<PostResponseDto> posts;
    private List<FriendDto> friends;
    private List<FriendDto> mutualFriends;

    // Statistics
    private ProfileStatsDto stats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileStatsDto {
        private long totalPosts;
        private long totalFriends;
        private long mutualFriendsCount;
        private boolean isOwnProfile;
    }
}
