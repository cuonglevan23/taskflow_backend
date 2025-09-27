package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.FriendDto.FriendDto;
import com.example.taskmanagement_backend.dtos.FriendDto.FriendshipStatusDto;
import com.example.taskmanagement_backend.dtos.PostDto.PostResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.FullUserProfileDto;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.entities.Post;
import com.example.taskmanagement_backend.enums.PostPrivacy;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.repositories.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FullUserProfileService {

    private final UserJpaRepository userRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final FriendService friendService;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    /**
     * Get full profile information for a user (like Facebook profile page)
     * - User's posts (filtered by privacy and relationship)
     * - User's friends list (visible friends)
     * - Mutual friends
     * - Friendship status with current viewer
     * - Profile statistics
     */
    public FullUserProfileDto getFullUserProfile(Long userId) {
        User currentUser = getCurrentUser();
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile userProfile = targetUser.getUserProfile();

        log.info("👤 Getting full profile for user {} (viewed by {})",
                targetUser.getEmail(), currentUser.getEmail());

        // Determine relationship between users
        boolean isOwnProfile = currentUser.getId().equals(targetUser.getId());
        boolean areFriends = !isOwnProfile && friendService.areFriends(currentUser.getId(), targetUser.getId());

        // Get friendship status between current user and target user
        FriendshipStatusDto friendshipStatus = isOwnProfile ? null : friendService.getFriendshipStatus(userId);

        // Get user's posts (filtered by privacy and relationship)
        List<PostResponseDto> posts = getVisiblePosts(targetUser, currentUser, isOwnProfile, areFriends);

        // Get user's friends list (visible friends)
        List<FriendDto> friends = getVisibleFriends(targetUser, currentUser, isOwnProfile, areFriends);

        // Get mutual friends if viewing someone else's profile
        List<FriendDto> mutualFriends = isOwnProfile ? List.of() : getMutualFriends(targetUser, currentUser);

        // Calculate profile statistics
        FullUserProfileDto.ProfileStatsDto stats = calculateProfileStats(targetUser, posts, friends, mutualFriends, isOwnProfile);

        return FullUserProfileDto.builder()
                .id(targetUser.getId())
                .email(targetUser.getEmail())
                .firstName(userProfile != null ? userProfile.getFirstName() : "")
                .lastName(userProfile != null ? userProfile.getLastName() : "")
                .username(userProfile != null ? userProfile.getUsername() : targetUser.getEmail())
                .avatarUrl(userProfile != null ? userProfile.getAvtUrl() : null)
                .department(userProfile != null ? userProfile.getDepartment() : null)
                .jobTitle(userProfile != null ? userProfile.getJobTitle() : null)
                .aboutMe(userProfile != null ? userProfile.getAboutMe() : null)
                .joinedAt(targetUser.getCreatedAt())
                .friendshipStatus(friendshipStatus)
                .posts(posts)
                .friends(friends)
                .mutualFriends(mutualFriends)
                .stats(stats)
                .build();
    }

    /**
     * Get posts visible to current user based on privacy settings and relationship
     * Privacy rules:
     * - If A = B → see all posts (own profile)
     * - If A is friend of B → see PUBLIC and FRIENDS posts
     * - If A is not friend → see only PUBLIC posts
     */
    private List<PostResponseDto> getVisiblePosts(User targetUser, User currentUser, boolean isOwnProfile, boolean areFriends) {
        try {
            // Limit to 10 most recent posts for initial load
            PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            List<Post> posts;

            if (isOwnProfile) {
                // Own profile - see all posts
                Page<Post> postsPage = postRepository.findByAuthorOrderByCreatedAtDesc(targetUser, pageRequest);
                posts = postsPage.getContent();
            } else if (areFriends) {
                // Friend's profile - see PUBLIC and FRIENDS posts
                posts = postRepository.findByAuthorAndPrivacyInOrderByCreatedAtDesc(
                    targetUser,
                    List.of(PostPrivacy.PUBLIC, PostPrivacy.FRIENDS),
                    pageRequest
                );
            } else {
                // Non-friend - see only PUBLIC posts
                posts = postRepository.findByAuthorAndPrivacyOrderByCreatedAtDesc(
                    targetUser,
                    PostPrivacy.PUBLIC,
                    pageRequest
                );
            }

            // Convert to DTOs using the public method
            return posts.stream()
                    .map(post -> postService.convertToPostResponseDto(post, currentUser.getId()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("⚠️ Could not load posts for user {}: {}", targetUser.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Get friends visible to current user
     * Privacy rules similar to Facebook:
     * - Own profile: see all friends
     * - Friend's profile: see mutual friends + some other friends
     * - Non-friend: see only mutual friends (limited)
     */
    private List<FriendDto> getVisibleFriends(User targetUser, User currentUser, boolean isOwnProfile, boolean areFriends) {
        try {
            if (isOwnProfile) {
                // Own profile - see all friends
                return friendService.getFriends(targetUser.getId(), 0, 20).getContent();
            } else if (areFriends) {
                // Friend's profile - see some friends (limited for privacy)
                return friendService.getFriends(targetUser.getId(), 0, 10).getContent();
            } else {
                // Non-friend - see only mutual friends (very limited)
                List<FriendDto> mutualFriends = getMutualFriends(targetUser, currentUser);
                return mutualFriends.stream().limit(5).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not load friends for user {}: {}", targetUser.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Get mutual friends between target user and current user
     */
    private List<FriendDto> getMutualFriends(User targetUser, User currentUser) {
        try {
            return friendService.getMutualFriends(currentUser.getId(), targetUser.getId());
        } catch (Exception e) {
            log.warn("⚠️ Could not load mutual friends: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Calculate profile statistics
     */
    private FullUserProfileDto.ProfileStatsDto calculateProfileStats(
            User targetUser,
            List<PostResponseDto> visiblePosts,
            List<FriendDto> visibleFriends,
            List<FriendDto> mutualFriends,
            boolean isOwnProfile) {

        long totalPosts = isOwnProfile ?
            postRepository.countByAuthor(targetUser) :
            visiblePosts.size();

        long totalFriends = isOwnProfile ?
            friendService.getFriendsCount(targetUser.getId()) :
            visibleFriends.size();

        return FullUserProfileDto.ProfileStatsDto.builder()
                .totalPosts(totalPosts)
                .totalFriends(totalFriends)
                .mutualFriendsCount(mutualFriends.size())
                .isOwnProfile(isOwnProfile)
                .build();
    }
}
