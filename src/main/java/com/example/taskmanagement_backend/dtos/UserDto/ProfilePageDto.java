package com.example.taskmanagement_backend.dtos.UserDto;

import com.example.taskmanagement_backend.dtos.FriendDto.FriendDto;
import com.example.taskmanagement_backend.dtos.FriendDto.FriendshipStatusDto;
import com.example.taskmanagement_backend.dtos.PostDto.PostResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
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
public class ProfilePageDto {
    // Basic profile information
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    private String avatarUrl;
    private String coverImageUrl;
    private String department;
    private String jobTitle;
    private String aboutMe;
    private LocalDateTime joinedAt;

    // Premium status
    private boolean isPremium;
    private LocalDateTime premiumExpiry;
    private String premiumBadgeUrl;

    // Online status - NEW
    private String onlineStatus;        // "online", "offline", "away", "busy"
    private LocalDateTime lastSeen;     // Last time user was active
    private Boolean isOnline;           // Simple boolean for quick checks

    // Relationship info (when viewing someone else's profile)
    private FriendshipStatusDto friendshipStatus;
    private boolean isOwnProfile;

    // Tab content counts (for tab badges)
    private TabCountsDto tabCounts;

    // Profile statistics
    private ProfileStatsDto stats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TabCountsDto {
        private long postsCount;
        private long friendsCount;
        private long tasksCount;
        private long publicTasksCount;
        private long sharedTasksCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileStatsDto {
        private long totalPosts;
        private long totalFriends;
        private long totalTasks;
        private long completedTasks;
        private long pendingTasks;
        private long mutualFriendsCount;
        private double taskCompletionRate;
        private boolean isOwnProfile;
    }
}
