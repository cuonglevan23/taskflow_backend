package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.FriendDto.FriendDto;
import com.example.taskmanagement_backend.dtos.FriendDto.FriendshipStatusDto;
import com.example.taskmanagement_backend.dtos.PostDto.PostResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.ProfilePageDto;
import com.example.taskmanagement_backend.dtos.UserDto.ProfileTabContentDto;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.entities.Post;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.enums.PostPrivacy;
import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.repositories.PostRepository;
import com.example.taskmanagement_backend.repositories.TaskJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfilePageService {

    private final UserJpaRepository userRepository;
    private final PostRepository postRepository;
    private final TaskJpaRepository taskRepository;
    private final PostService postService;
    private final FriendService friendService;
    private final TaskService taskService;
    private final OnlineStatusService onlineStatusService; // NEW

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    /**
     * Get complete profile page information by userId (more stable than username)
     * This is the main endpoint for /profile/{userId}
     */
    public ProfilePageDto getProfilePageByUserId(Long userId) {
        User currentUser = getCurrentUser();
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        UserProfile userProfile = targetUser.getUserProfile();
        boolean isOwnProfile = currentUser.getId().equals(targetUser.getId());
        boolean areFriends = !isOwnProfile && friendService.areFriends(currentUser.getId(), targetUser.getId());

        log.info("👤 Getting profile page for userId: {} (viewed by {})", userId, currentUser.getEmail());

        // Get friendship status
        FriendshipStatusDto friendshipStatus = isOwnProfile ? null :
            friendService.getFriendshipStatus(targetUser.getId());

        // Calculate tab counts
        ProfilePageDto.TabCountsDto tabCounts = calculateTabCounts(targetUser, currentUser, isOwnProfile, areFriends);

        // Calculate profile statistics
        ProfilePageDto.ProfileStatsDto stats = calculateProfileStats(targetUser, currentUser, isOwnProfile);

        return ProfilePageDto.builder()
                .id(targetUser.getId())
                .email(targetUser.getEmail())
                .firstName(userProfile != null ? userProfile.getFirstName() : "")
                .lastName(userProfile != null ? userProfile.getLastName() : "")
                .username(userProfile != null ? userProfile.getUsername() : targetUser.getEmail())
                .avatarUrl(targetUser.getAvatarUrl()) // FIX: Use User.getAvatarUrl() directly like PostService does
                .coverImageUrl(userProfile != null ? userProfile.getCoverImageUrl() : null)
                .department(userProfile != null ? userProfile.getDepartment() : null)
                .jobTitle(userProfile != null ? userProfile.getJobTitle() : null)
                .aboutMe(userProfile != null ? userProfile.getAboutMe() : null)
                .joinedAt(targetUser.getCreatedAt())
                .isPremium(isPremiumUser(targetUser))
                .premiumExpiry(getPremiumExpiry(targetUser))
                .premiumBadgeUrl(getPremiumBadgeUrl(targetUser))
                // Online status fields - NEW
                .onlineStatus(onlineStatusService.getOnlineStatus(targetUser.getId()))
                .lastSeen(onlineStatusService.getLastSeen(targetUser.getId()))
                .isOnline(onlineStatusService.isUserOnline(targetUser.getId()))
                .friendshipStatus(friendshipStatus)
                .isOwnProfile(isOwnProfile)
                .tabCounts(tabCounts)
                .stats(stats)
                .build();
    }

    /**
     * Get content for a specific tab by userId (Posts, Friends, or Tasks)
     */
    public ProfileTabContentDto getTabContentByUserId(Long userId, String tabType, int page, int size) {
        User currentUser = getCurrentUser();
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        boolean isOwnProfile = currentUser.getId().equals(targetUser.getId());
        boolean areFriends = !isOwnProfile && friendService.areFriends(currentUser.getId(), targetUser.getId());

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        switch (tabType.toLowerCase()) {
            case "posts":
                return getPostsTabContent(targetUser, currentUser, isOwnProfile, areFriends, pageRequest);
            case "friends":
                return getFriendsTabContent(targetUser, currentUser, isOwnProfile, areFriends, pageRequest);
            case "tasks":
                return getTasksTabContent(targetUser, currentUser, isOwnProfile, areFriends, pageRequest);
            default:
                throw new RuntimeException("Invalid tab type: " + tabType);
        }
    }

    /**
     * Get complete profile page information (header + basic info + tab counts)
     * This is the main endpoint for /profile/{username} or /me
     */
    public ProfilePageDto getProfilePage(String username) {
        User currentUser = getCurrentUser();
        User targetUser = findUserByUsername(username);

        UserProfile userProfile = targetUser.getUserProfile();
        boolean isOwnProfile = currentUser.getId().equals(targetUser.getId());
        boolean areFriends = !isOwnProfile && friendService.areFriends(currentUser.getId(), targetUser.getId());

        log.info("👤 Getting profile page for {} (viewed by {})", username, currentUser.getEmail());

        // Get friendship status
        FriendshipStatusDto friendshipStatus = isOwnProfile ? null :
            friendService.getFriendshipStatus(targetUser.getId());

        // Calculate tab counts
        ProfilePageDto.TabCountsDto tabCounts = calculateTabCounts(targetUser, currentUser, isOwnProfile, areFriends);

        // Calculate profile statistics
        ProfilePageDto.ProfileStatsDto stats = calculateProfileStats(targetUser, currentUser, isOwnProfile);

        return ProfilePageDto.builder()
                .id(targetUser.getId())
                .email(targetUser.getEmail())
                .firstName(userProfile != null ? userProfile.getFirstName() : "")
                .lastName(userProfile != null ? userProfile.getLastName() : "")
                .username(userProfile != null ? userProfile.getUsername() : targetUser.getEmail())
                .avatarUrl(targetUser.getAvatarUrl()) // FIX: Use User.getAvatarUrl() directly like PostService does
                .coverImageUrl(userProfile != null ? userProfile.getCoverImageUrl() : null) // New field
                .department(userProfile != null ? userProfile.getDepartment() : null)
                .jobTitle(userProfile != null ? userProfile.getJobTitle() : null)
                .aboutMe(userProfile != null ? userProfile.getAboutMe() : null)
                .joinedAt(targetUser.getCreatedAt())
                .isPremium(isPremiumUser(targetUser))
                .premiumExpiry(getPremiumExpiry(targetUser))
                .premiumBadgeUrl(getPremiumBadgeUrl(targetUser))
                .friendshipStatus(friendshipStatus)
                .isOwnProfile(isOwnProfile)
                .tabCounts(tabCounts)
                .stats(stats)
                .onlineStatus(onlineStatusService.getOnlineStatus(targetUser.getId())) // NEW
                .lastSeen(onlineStatusService.getLastSeen(targetUser.getId())) // NEW
                .isOnline(onlineStatusService.isUserOnline(targetUser.getId())) // NEW
                .build();
    }

    /**
     * Get content for a specific tab (Posts, Friends, or Tasks)
     */
    public ProfileTabContentDto getTabContent(String username, String tabType, int page, int size) {
        User currentUser = getCurrentUser();
        User targetUser = findUserByUsername(username);

        boolean isOwnProfile = currentUser.getId().equals(targetUser.getId());
        boolean areFriends = !isOwnProfile && friendService.areFriends(currentUser.getId(), targetUser.getId());

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        switch (tabType.toLowerCase()) {
            case "posts":
                return getPostsTabContent(targetUser, currentUser, isOwnProfile, areFriends, pageRequest);
            case "friends":
                return getFriendsTabContent(targetUser, currentUser, isOwnProfile, areFriends, pageRequest);
            case "tasks":
                return getTasksTabContent(targetUser, currentUser, isOwnProfile, areFriends, pageRequest);
            default:
                throw new RuntimeException("Invalid tab type: " + tabType);
        }
    }

    private ProfileTabContentDto getPostsTabContent(User targetUser, User currentUser,
                                                   boolean isOwnProfile, boolean areFriends,
                                                   PageRequest pageRequest) {
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

        List<PostResponseDto> postDtos = posts.stream()
                .map(post -> postService.convertToPostResponseDto(post, currentUser.getId()))
                .collect(Collectors.toList());

        // Check if there are more posts
        boolean hasMore = posts.size() == pageRequest.getPageSize();

        return ProfileTabContentDto.builder()
                .posts(postDtos)
                .hasMorePosts(hasMore)
                .tabType("posts")
                .currentPage(pageRequest.getPageNumber())
                .pageSize(pageRequest.getPageSize())
                .build();
    }

    private ProfileTabContentDto getFriendsTabContent(User targetUser, User currentUser,
                                                     boolean isOwnProfile, boolean areFriends,
                                                     PageRequest pageRequest) {
        List<FriendDto> friends;
        List<FriendDto> mutualFriends = List.of();

        if (isOwnProfile) {
            // Own profile - see all friends
            Page<FriendDto> friendsPage = friendService.getFriends(targetUser.getId(),
                pageRequest.getPageNumber(), pageRequest.getPageSize());
            friends = friendsPage.getContent();
        } else if (areFriends) {
            // Friend's profile - see some friends + mutual friends
            Page<FriendDto> friendsPage = friendService.getFriends(targetUser.getId(),
                pageRequest.getPageNumber(), Math.min(pageRequest.getPageSize(), 10));
            friends = friendsPage.getContent();
            mutualFriends = friendService.getMutualFriends(currentUser.getId(), targetUser.getId());
        } else {
            // Non-friend - see only mutual friends (limited)
            mutualFriends = friendService.getMutualFriends(currentUser.getId(), targetUser.getId());
            friends = mutualFriends.stream().limit(pageRequest.getPageSize()).collect(Collectors.toList());
        }

        boolean hasMore = friends.size() == pageRequest.getPageSize();

        return ProfileTabContentDto.builder()
                .friends(friends)
                .mutualFriends(mutualFriends)
                .hasMoreFriends(hasMore)
                .tabType("friends")
                .currentPage(pageRequest.getPageNumber())
                .pageSize(pageRequest.getPageSize())
                .build();
    }

    private ProfileTabContentDto getTasksTabContent(User targetUser, User currentUser,
                                                   boolean isOwnProfile, boolean areFriends,
                                                   PageRequest pageRequest) {
        List<TaskResponseDto> publicTasks;
        List<TaskResponseDto> sharedTasks;

        if (isOwnProfile) {
            // Own profile - see all tasks
            Page<Task> allTasks = taskRepository.findByAssigneeOrderByCreatedAtDesc(targetUser, pageRequest);
            publicTasks = allTasks.getContent().stream()
                    .filter(task -> task.getIsPublic() != null && task.getIsPublic())
                    .map(taskService::convertToTaskResponseDto)
                    .collect(Collectors.toList());

            sharedTasks = allTasks.getContent().stream()
                    .filter(task -> (task.getIsPublic() == null || !task.getIsPublic()) && isTaskSharedWithUser(task, currentUser))
                    .map(taskService::convertToTaskResponseDto)
                    .collect(Collectors.toList());
        } else if (areFriends) {
            // Friend's profile - see public tasks and tasks shared with current user
            publicTasks = getPublicTasksForUser(targetUser, pageRequest);
            sharedTasks = getSharedTasksForUser(targetUser, currentUser, pageRequest);
        } else {
            // Non-friend - see only public tasks
            publicTasks = getPublicTasksForUser(targetUser, pageRequest);
            sharedTasks = List.of();
        }

        boolean hasMore = (publicTasks.size() + sharedTasks.size()) == pageRequest.getPageSize();

        return ProfileTabContentDto.builder()
                .publicTasks(publicTasks)
                .sharedTasks(sharedTasks)
                .hasMoreTasks(hasMore)
                .tabType("tasks")
                .currentPage(pageRequest.getPageNumber())
                .pageSize(pageRequest.getPageSize())
                .build();
    }

    private ProfilePageDto.TabCountsDto calculateTabCounts(User targetUser, User currentUser,
                                                          boolean isOwnProfile, boolean areFriends) {
        long postsCount;
        long friendsCount;
        long tasksCount;
        long publicTasksCount;
        long sharedTasksCount;

        if (isOwnProfile) {
            postsCount = postRepository.countByAuthor(targetUser);
            friendsCount = friendService.getFriendsCount(targetUser.getId());
            tasksCount = taskRepository.countByAssignee(targetUser);
            publicTasksCount = taskRepository.countByAssigneeAndIsPublic(targetUser, true);
            sharedTasksCount = 0; // Calculate if needed
        } else if (areFriends) {
            postsCount = postRepository.findByAuthorAndPrivacyInOrderByCreatedAtDesc(
                targetUser, List.of(PostPrivacy.PUBLIC, PostPrivacy.FRIENDS),
                PageRequest.of(0, 1000)).size();
            friendsCount = Math.min(friendService.getFriendsCount(targetUser.getId()), 10);
            publicTasksCount = taskRepository.countByAssigneeAndIsPublic(targetUser, true);
            tasksCount = publicTasksCount;
            sharedTasksCount = 0; // Calculate if needed
        } else {
            postsCount = postRepository.findByAuthorAndPrivacyOrderByCreatedAtDesc(
                targetUser, PostPrivacy.PUBLIC, PageRequest.of(0, 1000)).size();
            friendsCount = friendService.getMutualFriends(currentUser.getId(), targetUser.getId()).size();
            publicTasksCount = taskRepository.countByAssigneeAndIsPublic(targetUser, true);
            tasksCount = publicTasksCount;
            sharedTasksCount = 0;
        }

        return ProfilePageDto.TabCountsDto.builder()
                .postsCount(postsCount)
                .friendsCount(friendsCount)
                .tasksCount(tasksCount)
                .publicTasksCount(publicTasksCount)
                .sharedTasksCount(sharedTasksCount)
                .build();
    }

    private ProfilePageDto.ProfileStatsDto calculateProfileStats(User targetUser, User currentUser, boolean isOwnProfile) {
        long totalPosts = postRepository.countByAuthor(targetUser);
        long totalFriends = friendService.getFriendsCount(targetUser.getId());
        long totalTasks = isOwnProfile ? taskRepository.countByAssignee(targetUser) :
                         taskRepository.countByAssigneeAndIsPublic(targetUser, true);

        long completedTasks = isOwnProfile ?
            taskRepository.countByAssigneeAndStatus(targetUser, TaskStatus.COMPLETED) :
            taskRepository.countByAssigneeAndStatusAndIsPublic(targetUser, TaskStatus.COMPLETED, true);

        long pendingTasks = totalTasks - completedTasks;
        double completionRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

        long mutualFriendsCount = isOwnProfile ? 0 :
            friendService.getMutualFriends(currentUser.getId(), targetUser.getId()).size();

        return ProfilePageDto.ProfileStatsDto.builder()
                .totalPosts(totalPosts)
                .totalFriends(totalFriends)
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .pendingTasks(pendingTasks)
                .mutualFriendsCount(mutualFriendsCount)
                .taskCompletionRate(Math.round(completionRate * 100.0) / 100.0)
                .isOwnProfile(isOwnProfile)
                .build();
    }

    // Helper methods
    private User findUserByUsername(String usernameOrEmail) {
        // First try to find by username
        Optional<User> userByUsername = userRepository.findByUserProfile_Username(usernameOrEmail);
        if (userByUsername.isPresent()) {
            return userByUsername.get();
        }

        // If not found by username, try to find by email
        return userRepository.findByEmail(usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + usernameOrEmail));
    }

    private boolean isPremiumUser(User user) {
        UserProfile profile = user.getUserProfile();
        return profile != null && profile.getIsPremium() != null && profile.getIsPremium()
               && (profile.getPremiumExpiry() == null || profile.getPremiumExpiry().isAfter(LocalDateTime.now()));
    }

    private LocalDateTime getPremiumExpiry(User user) {
        UserProfile profile = user.getUserProfile();
        return profile != null ? profile.getPremiumExpiry() : null;
    }

    private String getPremiumBadgeUrl(User user) {
        if (!isPremiumUser(user)) return null;

        UserProfile profile = user.getUserProfile();
        String planType = profile != null ? profile.getPremiumPlanType() : null;

        // Return different badge based on plan type
        switch (planType != null ? planType.toLowerCase() : "monthly") {
            case "lifetime":
                return "/images/premium-badge-gold.svg";
            case "yearly":
                return "/images/premium-badge.svg";
            case "monthly":
            default:
                return "/images/premium-badge-small.svg";
        }
    }

    private List<TaskResponseDto> getPublicTasksForUser(User user, PageRequest pageRequest) {
        Page<Task> tasks = taskRepository.findByAssigneeAndIsPublicOrderByCreatedAtDesc(user, true, pageRequest);
        return tasks.getContent().stream()
                .map(taskService::convertToTaskResponseDto)
                .collect(Collectors.toList());
    }

    private List<TaskResponseDto> getSharedTasksForUser(User targetUser, User currentUser, PageRequest pageRequest) {
        // TODO: Implement task sharing logic based on your requirements
        return List.of();
    }

    private boolean isTaskSharedWithUser(Task task, User user) {
        // TODO: Implement task sharing logic
        return false;
    }
}
