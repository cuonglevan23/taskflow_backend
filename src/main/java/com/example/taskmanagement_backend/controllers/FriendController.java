package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.FriendDto.*;
import com.example.taskmanagement_backend.services.FriendService;
import com.example.taskmanagement_backend.services.OnlineStatusService;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Friend Management", description = "APIs for managing friendships")
public class FriendController {

    private final FriendService friendService;
    private final OnlineStatusService onlineStatusService; // Thêm để update heartbeat
    private final UserJpaRepository userRepository; // Thêm để lấy current user

    /**
     * Gửi lời mời kết bạn
     * POST /api/friends/request
     */
    @PostMapping("/request")
    @Operation(summary = "Send friend request", description = "Send a friend request to another user")
    public ResponseEntity<Map<String, Object>> sendFriendRequest(@Valid @RequestBody SendFriendRequestDto requestDto) {
        try {
            log.info("🤝 Sending friend request to user: {}", requestDto.getTargetUserId());

            FriendRequestDto result = friendService.sendFriendRequest(requestDto);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Lời mời kết bạn đã được gửi");
            response.put("data", result);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error sending friend request: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Chấp nhận lời mời kết bạn
     * POST /api/friends/accept/{id}
     */
    @PostMapping("/accept/{id}")
    @Operation(summary = "Accept friend request", description = "Accept a pending friend request")
    public ResponseEntity<Map<String, Object>> acceptFriendRequest(@PathVariable Long id) {
        try {
            log.info("✅ Accepting friend request: {}", id);

            FriendRequestDto result = friendService.acceptFriendRequest(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Đã chấp nhận lời mời kết bạn");
            response.put("data", result);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error accepting friend request: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Từ chối lời mời kết bạn
     * DELETE /api/friends/reject/{id}
     */
    @DeleteMapping("/reject/{id}")
    @Operation(summary = "Reject friend request", description = "Reject a pending friend request")
    public ResponseEntity<Map<String, Object>> rejectFriendRequest(@PathVariable Long id) {
        try {
            log.info("❌ Rejecting friend request: {}", id);

            friendService.rejectFriendRequest(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Đã từ chối lời mời kết bạn");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error rejecting friend request: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Hủy lời mời đã gửi
     * DELETE /api/friends/cancel/{id}
     */
    @DeleteMapping("/cancel/{id}")
    @Operation(summary = "Cancel friend request", description = "Cancel a sent friend request")
    public ResponseEntity<Map<String, Object>> cancelFriendRequest(@PathVariable Long id) {
        try {
            log.info("🚫 Cancelling friend request: {}", id);

            friendService.cancelFriendRequest(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Đã hủy lời mời kết bạn");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error cancelling friend request: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Xóa bạn bè
     * DELETE /api/friends/unfriend/{friendId}
     */
    @DeleteMapping("/unfriend/{friendId}")
    @Operation(summary = "Unfriend user", description = "Remove friendship with another user")
    public ResponseEntity<Map<String, Object>> unfriend(@PathVariable Long friendId) {
        try {
            log.info("💔 Unfriending user: {}", friendId);

            friendService.unfriend(friendId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Đã xóa bạn bè");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error unfriending user: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Lấy danh sách bạn bè
     * GET /api/friends
     */
    @GetMapping
    @Operation(summary = "Get friends list", description = "Get list of all friends")
    public ResponseEntity<Map<String, Object>> getFriends() {
        try {
            log.info("👥 Getting friends list");

            // Update heartbeat cho current user để maintain online status
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                String userEmail = authentication.getName();
                User currentUser = userRepository.findByEmail(userEmail)
                        .orElseThrow(() -> new RuntimeException("Current user not found: " + userEmail));
                onlineStatusService.heartbeat(currentUser.getId());
                log.debug("🔄 Updated heartbeat for user: {}", userEmail);
            }

            List<FriendDto> friends = friendService.getFriends();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Lấy danh sách bạn bè thành công");
            response.put("data", friends);
            response.put("total", friends.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting friends list: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 🆕 Lấy danh sách bạn bè của người khác
     * GET /api/friends/{userId}
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Get user's friends list", description = "Get list of friends for a specific user with privacy filtering")
    public ResponseEntity<Map<String, Object>> getUserFriends(@PathVariable Long userId, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            log.info("👥 Getting friends list for userId: {} (requested by: {})", userId, authentication.getName());

            List<FriendDto> friends = friendService.getUserFriends(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Lấy danh sách bạn bè thành công");
            response.put("data", friends);
            response.put("total", friends.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting friends list for userId {}: {}", userId, e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Lấy lời mời đã nhận
     * GET /api/friends/requests/received
     */
    @GetMapping("/requests/received")
    @Operation(summary = "Get received requests", description = "Get list of received friend requests")
    public ResponseEntity<Map<String, Object>> getReceivedRequests() {
        try {
            log.info("📨 Getting received friend requests");

            List<FriendRequestDto> requests = friendService.getReceivedRequests();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Lấy danh sách lời mời nhận được thành công");
            response.put("data", requests);
            response.put("total", requests.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting received requests: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Lấy lời mời đã gửi
     * GET /api/friends/requests/sent
     */
    @GetMapping("/requests/sent")
    @Operation(summary = "Get sent requests", description = "Get list of sent friend requests")
    public ResponseEntity<Map<String, Object>> getSentRequests() {
        try {
            log.info("📤 Getting sent friend requests");

            List<FriendRequestDto> requests = friendService.getSentRequests();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Lấy danh sách lời mời đã gửi thành công");
            response.put("data", requests);
            response.put("total", requests.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting sent requests: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Kiểm tra trạng thái kết bạn
     * GET /api/friends/status/{friendId}
     */
    @GetMapping("/status/{friendId}")
    @Operation(summary = "Get friendship status", description = "Get friendship status with specific user")
    public ResponseEntity<Map<String, Object>> getFriendshipStatus(@PathVariable Long friendId) {
        try {
            log.info("🔍 Checking friendship status with user: {}", friendId);

            FriendshipStatusDto status = friendService.getFriendshipStatus(friendId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Lấy trạng thái kết bạn thành công");
            response.put("data", status);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting friendship status: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Lấy thống kê bạn bè
     * GET /api/friends/stats
     */
    @GetMapping("/stats")
    @Operation(summary = "Get friendship statistics", description = "Get friendship statistics for current user")
    public ResponseEntity<Map<String, Object>> getFriendshipStats() {
        try {
            log.info("📊 Getting friendship statistics");

            FriendshipStatsDto stats = friendService.getFriendshipStats();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Lấy thống kê bạn bè thành công");
            response.put("data", stats);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error getting friendship stats: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }
}
