package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.FriendDto.*;
import com.example.taskmanagement_backend.entities.Friend;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.enums.FriendshipStatus;
import com.example.taskmanagement_backend.repositories.FriendRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserJpaRepository userRepository;
    private final OnlineStatusService onlineStatusService; // Thêm OnlineStatusService để đồng nhất trạng thái online
    private final S3Service s3Service; // Thêm S3Service để convert avatar URLs

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    /**
     * Send friend request
     */
    @Transactional
    public FriendRequestDto sendFriendRequest(SendFriendRequestDto requestDto) {
        User currentUser = getCurrentUser();

        if (currentUser.getId().equals(requestDto.getTargetUserId())) {
            throw new RuntimeException("Cannot send friend request to yourself");
        }

        User friend = userRepository.findById(requestDto.getTargetUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if relationship already exists
        Optional<Friend> existingFriendship = friendRepository.findFriendshipBetween(
                currentUser.getId(), friend.getId());

        if (existingFriendship.isPresent()) {
            Friend existing = existingFriendship.get();
            if (existing.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new RuntimeException("You are already friends");
            }
            if (existing.getStatus() == FriendshipStatus.PENDING) {
                throw new RuntimeException("Friend request already sent");
            }
            if (existing.getStatus() == FriendshipStatus.BLOCKED) {
                throw new RuntimeException("Cannot send friend request");
            }
        }

        // Create new friend request
        Friend friendRequest = Friend.builder()
                .user(currentUser)
                .friend(friend)
                .status(FriendshipStatus.PENDING)
                .build();

        Friend saved = friendRepository.save(friendRequest);

        log.info("🤝 User {} sent friend request to {}", currentUser.getEmail(), friend.getEmail());

        return mapToFriendRequestDto(saved, currentUser.getId());
    }

    /**
     * Accept friend request
     */
    @Transactional
    public FriendRequestDto acceptFriendRequest(Long requestId) {
        User currentUser = getCurrentUser();

        Friend friendRequest = friendRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        // Check permission to accept
        if (!friendRequest.getFriend().getId().equals(currentUser.getId())) {
            throw new RuntimeException("You don't have permission to accept this request");
        }

        if (friendRequest.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("Request is not in pending status");
        }

        // Accept the request
        friendRequest.accept();
        Friend saved = friendRepository.save(friendRequest);

        log.info("✅ User {} accepted friend request from {}",
                currentUser.getEmail(), friendRequest.getUser().getEmail());

        return mapToFriendRequestDto(saved, currentUser.getId());
    }

    /**
     * Reject friend request
     */
    @Transactional
    public void rejectFriendRequest(Long requestId) {
        User currentUser = getCurrentUser();

        Friend friendRequest = friendRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        // Check permission to reject
        if (!friendRequest.getFriend().getId().equals(currentUser.getId())) {
            throw new RuntimeException("You don't have permission to reject this request");
        }

        if (friendRequest.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("Request is not in pending status");
        }

        // Delete request instead of setting status to REJECTED
        friendRepository.delete(friendRequest);

        log.info("❌ User {} rejected friend request from {}",
                currentUser.getEmail(), friendRequest.getUser().getEmail());
    }

    /**
     * Cancel sent friend request
     */
    @Transactional
    public void cancelFriendRequest(Long requestId) {
        User currentUser = getCurrentUser();

        Friend friendRequest = friendRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        // Check permission to cancel
        if (!friendRequest.getUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("You don't have permission to cancel this request");
        }

        if (friendRequest.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("Request is not in pending status");
        }

        friendRepository.delete(friendRequest);

        log.info("🚫 User {} cancelled friend request to {}",
                currentUser.getEmail(), friendRequest.getFriend().getEmail());
    }

    /**
     * Remove friend
     */
    @Transactional
    public void unfriend(Long friendId) {
        User currentUser = getCurrentUser();

        Friend friendship = friendRepository.findFriendshipBetween(currentUser.getId(), friendId)
                .orElseThrow(() -> new RuntimeException("Friendship not found"));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new RuntimeException("You are not friends with this user");
        }

        friendRepository.delete(friendship);

        log.info("💔 User {} unfriended user {}", currentUser.getEmail(), friendId);
    }

    /**
     * Get friends list
     */
    public List<FriendDto> getFriends() {
        User currentUser = getCurrentUser();

        List<Friend> friendships = friendRepository.findAcceptedFriends(
                currentUser.getId(), FriendshipStatus.ACCEPTED);

        return friendships.stream()
                .map(friendship -> mapToFriendDto(friendship, currentUser.getId()))
                .collect(Collectors.toList());
    }

    /**
     * 🆕 Get friends list for a specific user with privacy filtering
     */
    public List<FriendDto> getUserFriends(Long userId) {
        User currentUser = getCurrentUser();

        // Check if the requested user exists
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // Privacy filtering based on relationship
        boolean isOwnProfile = currentUser.getId().equals(userId);
        boolean areFriends = !isOwnProfile && areFriends(currentUser.getId(), userId);

        List<Friend> friendships = friendRepository.findAcceptedFriends(
                userId, FriendshipStatus.ACCEPTED);

        List<FriendDto> friends = friendships.stream()
                .map(friendship -> mapToFriendDto(friendship, userId))
                .collect(Collectors.toList());

        // Apply privacy filtering
        if (isOwnProfile) {
            // Own profile - see all friends
            return friends;
        } else if (areFriends) {
            // Friend's profile - see limited friends (e.g., first 20) + mutual friends highlighted
            List<FriendDto> limitedFriends = friends.stream()
                    .limit(20)  // Show limited number of friends
                    .collect(Collectors.toList());

            // Mark mutual friends
            List<Long> mutualFriendIds = getMutualFriends(currentUser.getId(), userId)
                    .stream()
                    .map(FriendDto::getId)
                    .collect(Collectors.toList());

            limitedFriends.forEach(friend -> {
                if (mutualFriendIds.contains(friend.getId())) {
                    // You could add a field to mark mutual friends if needed
                    // For now, we'll just return the limited list
                }
            });

            return limitedFriends;
        } else {
            // Non-friend - see only mutual friends
            return getMutualFriends(currentUser.getId(), userId);
        }
    }

    /**
     * Get received friend requests
     */
    public List<FriendRequestDto> getReceivedRequests() {
        User currentUser = getCurrentUser();

        List<Friend> requests = friendRepository.findByFriendAndStatus(
                currentUser, FriendshipStatus.PENDING);

        return requests.stream()
                .map(request -> mapToFriendRequestDto(request, currentUser.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Get sent friend requests
     */
    public List<FriendRequestDto> getSentRequests() {
        User currentUser = getCurrentUser();

        List<Friend> requests = friendRepository.findByUserAndStatus(
                currentUser, FriendshipStatus.PENDING);

        return requests.stream()
                .map(request -> mapToFriendRequestDto(request, currentUser.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Get friendship status between current user and target user
     */
    public FriendshipStatusDto getFriendshipStatus(Long targetUserId) {
        User currentUser = getCurrentUser();

        if (currentUser.getId().equals(targetUserId)) {
            return FriendshipStatusDto.builder()
                    .status("SELF")
                    .canSendRequest(false)
                    .canAcceptRequest(false)
                    .canCancelRequest(false)
                    .build();
        }

        Optional<Friend> friendship = friendRepository.findFriendshipBetween(
                currentUser.getId(), targetUserId);

        if (friendship.isEmpty()) {
            return FriendshipStatusDto.builder()
                    .status("NONE")
                    .canSendRequest(true)
                    .canAcceptRequest(false)
                    .canCancelRequest(false)
                    .build();
        }

        Friend friend = friendship.get();
        boolean isSender = friend.getUser().getId().equals(currentUser.getId());

        switch (friend.getStatus()) {
            case ACCEPTED:
                return FriendshipStatusDto.builder()
                        .status("FRIENDS")
                        .canSendRequest(false)
                        .canAcceptRequest(false)
                        .canCancelRequest(false)
                        .build();
            case PENDING:
                if (isSender) {
                    return FriendshipStatusDto.builder()
                            .status("REQUEST_SENT")
                            .canSendRequest(false)
                            .canAcceptRequest(false)
                            .canCancelRequest(true)
                            .requestId(friend.getId())
                            .build();
                } else {
                    return FriendshipStatusDto.builder()
                            .status("REQUEST_RECEIVED")
                            .canSendRequest(false)
                            .canAcceptRequest(true)
                            .canCancelRequest(false)
                            .requestId(friend.getId())
                            .build();
                }
            case BLOCKED:
                return FriendshipStatusDto.builder()
                        .status("BLOCKED")
                        .canSendRequest(false)
                        .canAcceptRequest(false)
                        .canCancelRequest(false)
                        .build();
            default:
                return FriendshipStatusDto.builder()
                        .status("NONE")
                        .canSendRequest(true)
                        .canAcceptRequest(false)
                        .canCancelRequest(false)
                        .build();
        }
    }

    /**
     * Check if two users are friends
     */
    public boolean areFriends(Long userId1, Long userId2) {
        Optional<Friend> friendship = friendRepository.findFriendshipBetween(userId1, userId2);
        return friendship.isPresent() && friendship.get().getStatus() == FriendshipStatus.ACCEPTED;
    }

    /**
     * Get mutual friends between two users
     */
    public List<FriendDto> getMutualFriends(Long userId1, Long userId2) {
        List<Friend> user1Friends = friendRepository.findAcceptedFriends(userId1, FriendshipStatus.ACCEPTED);
        List<Friend> user2Friends = friendRepository.findAcceptedFriends(userId2, FriendshipStatus.ACCEPTED);

        // Get friend IDs for user1
        List<Long> user1FriendIds = user1Friends.stream()
                .map(f -> f.getUser().getId().equals(userId1) ? f.getFriend().getId() : f.getUser().getId())
                .collect(Collectors.toList());

        // Get friend IDs for user2
        List<Long> user2FriendIds = user2Friends.stream()
                .map(f -> f.getUser().getId().equals(userId2) ? f.getFriend().getId() : f.getUser().getId())
                .collect(Collectors.toList());

        // Find mutual friend IDs
        List<Long> mutualFriendIds = user1FriendIds.stream()
                .filter(user2FriendIds::contains)
                .collect(Collectors.toList());

        // Convert to DTOs
        return mutualFriendIds.stream()
                .map(friendId -> {
                    User friend = userRepository.findById(friendId).orElse(null);
                    if (friend != null) {
                        return mapUserToFriendDto(friend);
                    }
                    return null;
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    /**
     * Get friends count for a user
     */
    public long getFriendsCount(Long userId) {
        return friendRepository.countAcceptedFriends(userId, FriendshipStatus.ACCEPTED);
    }

    /**
     * Get friends list for a specific user (with pagination support)
     */
    public org.springframework.data.domain.Page<FriendDto> getFriends(Long userId, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<Friend> friendships = friendRepository.findAcceptedFriendsWithPaging(
                userId, FriendshipStatus.ACCEPTED, pageable);

        return friendships.map(friendship -> mapToFriendDto(friendship, userId));
    }

    /**
     * Get friendship statistics for the current user
     */
    public FriendshipStatsDto getFriendshipStats() {
        User currentUser = getCurrentUser();

        long totalFriends = friendRepository.countAcceptedFriends(currentUser.getId(), FriendshipStatus.ACCEPTED);
        long pendingRequests = friendRepository.findByFriendAndStatus(currentUser, FriendshipStatus.PENDING).size();
        long sentRequests = friendRepository.findByUserAndStatus(currentUser, FriendshipStatus.PENDING).size();

        // For blocked users, we'd need to implement BLOCKED status if not already done
        long blockedUsers = 0; // TODO: Implement if BLOCKED functionality is needed

        return FriendshipStatsDto.builder()
                .totalFriends(totalFriends)
                .pendingRequests(pendingRequests)
                .sentRequests(sentRequests)
                .blockedUsers(blockedUsers)
                .mutualFriendsWithMostConnectedUser(0) // TODO: Implement complex mutual friends calculation
                .mostConnectedFriendName("") // TODO: Implement if needed
                .build();
    }

    /**
     * Get friend IDs for a specific user (for WebSocket broadcasting)
     */
    public List<Long> getFriendIds(Long userId) {
        try {
            List<Friend> friendships = friendRepository.findAcceptedFriends(userId, FriendshipStatus.ACCEPTED);

            return friendships.stream()
                    .map(friendship -> {
                        // Return the other user's ID (not the current user's ID)
                        if (friendship.getUser().getId().equals(userId)) {
                            return friendship.getFriend().getId();
                        } else {
                            return friendship.getUser().getId();
                        }
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Error getting friend IDs for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // Helper methods
    private FriendRequestDto mapToFriendRequestDto(Friend friend, Long currentUserId) {
        boolean isSender = friend.getUser().getId().equals(currentUserId);
        User otherUser = isSender ? friend.getFriend() : friend.getUser();
        UserProfile profile = otherUser.getUserProfile();

        return FriendRequestDto.builder()
                .id(friend.getId())
                .userId(otherUser.getId())
                .email(otherUser.getEmail())
                .firstName(profile != null ? profile.getFirstName() : "")
                .lastName(profile != null ? profile.getLastName() : "")
                .username(profile != null ? profile.getUsername() : otherUser.getEmail())
                .avatarUrl(otherUser.getAvatarUrl()) // FIXED: Use user.getAvatarUrl() instead of profile.getAvtUrl()
                .department(profile != null ? profile.getDepartment() : null)
                .jobTitle(profile != null ? profile.getJobTitle() : null)
                .status(friend.getStatus())
                .isSender(isSender)
                .createdAt(friend.getCreatedAt())
                .build();
    }

    private FriendDto mapToFriendDto(Friend friendship, Long currentUserId) {
        User friend = friendship.getUser().getId().equals(currentUserId) ?
                friendship.getFriend() : friendship.getUser();
        UserProfile profile = friend.getUserProfile();

        // Sử dụng OnlineStatusService thay vì user.isOnline() để đồng nhất với UserProfileController
        boolean isOnline = onlineStatusService.isUserOnline(friend.getId());

        // Log để debug trạng thái online
        log.debug("Friend {} ({}): DB online={}, Service online={}, lastSeen={}",
                 friend.getId(), friend.getEmail(), friend.isOnline(), isOnline, friend.getLastSeen());

        return FriendDto.builder()
                .id(friend.getId())
                .userId(friend.getId())
                .email(friend.getEmail())
                .firstName(profile != null ? profile.getFirstName() : "")
                .lastName(profile != null ? profile.getLastName() : "")
                .username(profile != null ? profile.getUsername() : friend.getEmail())
                .avatarUrl(friend.getAvatarUrl()) // FIXED: Use friend.getAvatarUrl() instead of profile.getAvtUrl()
                .department(profile != null ? profile.getDepartment() : null)
                .jobTitle(profile != null ? profile.getJobTitle() : null)
                .friendsSince(friendship.getCreatedAt()) // Thêm ngày kết bạn
                .isOnline(isOnline) // Sử dụng OnlineStatusService để đồng nhất trạng thái
                .build();
    }

    private FriendDto mapUserToFriendDto(User user) {
        UserProfile profile = user.getUserProfile();

        // Sử dụng OnlineStatusService thay vì user.isOnline() để đồng nhất với UserProfileController
        boolean isOnline = onlineStatusService.isUserOnline(user.getId());

        return FriendDto.builder()
                .id(user.getId())
                .userId(user.getId()) // Thêm userId field bị thiếu
                .email(user.getEmail())
                .firstName(profile != null ? profile.getFirstName() : "")
                .lastName(profile != null ? profile.getLastName() : "")
                .username(profile != null ? profile.getUsername() : user.getEmail())
                .avatarUrl(user.getAvatarUrl()) // FIXED: Use user.getAvatarUrl() instead of profile.getAvtUrl()
                .department(profile != null ? profile.getDepartment() : null)
                .jobTitle(profile != null ? profile.getJobTitle() : null)
                .friendsSince(null) // Không có thông tin friendsSince khi map từ User trực tiếp
                .isOnline(isOnline) // Sử dụng OnlineStatusService để đồng nhất trạng thái
                .build();
    }
}
