package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import com.example.taskmanagement_backend.dtos.UserDto.FullUserProfileDto;
import com.example.taskmanagement_backend.dtos.UserDto.ProfilePageDto;
import com.example.taskmanagement_backend.dtos.UserDto.ProfileTabContentDto;
import com.example.taskmanagement_backend.dtos.UserDto.UserLookupDto;
import com.example.taskmanagement_backend.dtos.UserProfileDto.UpdateUserProfileRequestDto;
import com.example.taskmanagement_backend.dtos.UserProfileDto.UserProfileResponseDto;
import com.example.taskmanagement_backend.services.UserProfileMapper;
import com.example.taskmanagement_backend.services.FullUserProfileService;
import com.example.taskmanagement_backend.services.ProfilePageService;
import com.example.taskmanagement_backend.services.OnlineStatusService;
import com.example.taskmanagement_backend.services.UserProfileService;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user-profiles")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Profile", description = "User profile management endpoints")
public class UserProfileController {

    private final UserJpaRepository userRepository;
    private final UserProfileMapper userProfileMapper;
    private final FullUserProfileService fullUserProfileService;
    private final ProfilePageService profilePageService;
    private final OnlineStatusService onlineStatusService;
    private final UserProfileService userProfileService;

    /**
     * 🔥 NEW: Update user profile
     * PUT /api/user-profiles/me
     */
    @PutMapping("/me")
    @Operation(summary = "Update current user profile", description = "Update profile information for the currently authenticated user")
    public ResponseEntity<UserProfileResponseDto> updateCurrentUserProfile(
            @Valid @RequestBody UpdateUserProfileRequestDto request,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            String userEmail = authentication.getName();
            log.info("🔄 Updating profile for current user: {}", userEmail);

            User currentUser = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Current user not found: " + userEmail));

            UserProfileResponseDto updatedProfile = userProfileService.updateUserProfile(currentUser.getId(), request);

            // Update online status to show activity
            onlineStatusService.setUserOnline(userEmail);

            log.info("✅ Successfully updated profile for user: {}", userEmail);
            return ResponseEntity.ok(updatedProfile);

        } catch (Exception e) {
            log.error("❌ Error updating current user profile: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 🔥 NEW: Update user profile by ID (admin/self only)
     * PUT /api/user-profiles/{userId}
     */
    @PutMapping("/{userId}")
    @Operation(summary = "Update user profile by ID", description = "Update profile information for a specific user (admin or self only)")
    public ResponseEntity<UserProfileResponseDto> updateUserProfile(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserProfileRequestDto request,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            String currentUserEmail = authentication.getName();
            User currentUser = userRepository.findByEmail(currentUserEmail)
                    .orElseThrow(() -> new RuntimeException("Current user not found: " + currentUserEmail));

            // Check if user is updating their own profile
            if (!currentUser.getId().equals(userId)) {
                log.warn("❌ User {} attempted to update profile of user {}", currentUser.getId(), userId);
                return ResponseEntity.status(403).build(); // Forbidden
            }

            log.info("🔄 Updating profile for userId: {} by user: {}", userId, currentUserEmail);

            UserProfileResponseDto updatedProfile = userProfileService.updateUserProfile(userId, request);

            // Update online status to show activity
            onlineStatusService.setUserOnline(currentUserEmail);

            log.info("✅ Successfully updated profile for userId: {}", userId);
            return ResponseEntity.ok(updatedProfile);

        } catch (RuntimeException e) {
            log.warn("❌ User not found with ID: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error updating user profile for ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 🔥 ENDPOINT CHÍNH: Lấy thông tin profile của user hiện tại
     * Phải đặt TRƯỚC endpoint /{userId} để tránh conflict routing
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user profile with online status", description = "Get profile information of the currently authenticated user including online status")
    public ResponseEntity<Map<String, Object>> getCurrentUserProfile(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            String userEmail = authentication.getName(); // Email từ JWT token
            log.info("Getting profile for current user: {}", userEmail);

            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Current user not found: " + userEmail));

            UserProfileDto userProfile = userProfileMapper.toUserProfileDto(user);

            // Update heartbeat when user accesses their profile
            onlineStatusService.heartbeat(user.getId());

            // Get online status information
            String onlineStatus = onlineStatusService.getOnlineStatus(user.getId());
            boolean isOnline = onlineStatusService.isUserOnline(user.getId());
            LocalDateTime lastSeen = onlineStatusService.getLastSeen(user.getId());

            // ✅ GET PREMIUM INFO FROM UserProfileService for accurate premium data
            UserProfileResponseDto detailedProfile = userProfileService.getUserProfile(user.getId());

            // Create enhanced response with online status AND premium info using HashMap (Map.of() limited to 10 pairs)
            Map<String, Object> response = new HashMap<>();
            response.put("userId", user.getId());
            response.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
            response.put("lastName", user.getLastName() != null ? user.getLastName() : "");
            response.put("email", user.getEmail());
            response.put("avatar", user.getAvatarUrl()); // SIMPLE: Just use user.getAvatarUrl() - it already handles S3 conversion
            response.put("onlineStatus", onlineStatus);  // "online", "away", "offline"
            response.put("isOnline", isOnline);
            response.put("lastSeen", lastSeen != null ? lastSeen : user.getCreatedAt());
            // ✅ ADD: Premium fields for icon display
            response.put("isPremium", detailedProfile != null ? detailedProfile.getIsPremium() : false);
            response.put("premiumExpiry", detailedProfile != null ? detailedProfile.getPremiumExpiry() : null);
            response.put("premiumPlanType", detailedProfile != null ? detailedProfile.getPremiumPlanType() : null);
            response.put("profile", userProfile);


            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error getting current user profile: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 🔥 NEW: Missing endpoint that frontend is calling
     * GET /api/user-profiles/me/profile
     */
    @GetMapping("/me/profile")
    @Operation(summary = "Get current user profile page", description = "Get profile page information for the currently authenticated user")
    public ResponseEntity<ProfilePageDto> getCurrentUserProfilePage(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            String userEmail = authentication.getName();
            log.info("🔍 Getting profile page for current user: {}", userEmail);

            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Current user not found: " + userEmail));

            // Use ProfilePageService to get complete profile page data
            ProfilePageDto profilePage = profilePageService.getProfilePageByUserId(user.getId());

            // Update heartbeat when user accesses their profile
            onlineStatusService.heartbeat(user.getId());

            log.info("✅ Successfully retrieved profile page for user: {}", userEmail);
            return ResponseEntity.ok(profilePage);

        } catch (Exception e) {
            log.error("❌ Error getting current user profile page: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 🔥 NEW: Missing endpoint that frontend is calling
     * GET /api/user-profiles/user/{userId}/profile-details
     */
    @GetMapping("/user/{userId}/profile-details")
    @Operation(summary = "Get user profile page by user ID", description = "Get profile page information for a specific user by their ID")
    public ResponseEntity<ProfilePageDto> getUserProfilePage(@PathVariable Long userId, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            log.info("🔍 Getting profile page for userId: {} (requested by: {})", userId, authentication.getName());

            // Use ProfilePageService to get complete profile page data
            ProfilePageDto profilePage = profilePageService.getProfilePageByUserId(userId);

            // Update heartbeat for current user (the one viewing the profile)
            String currentUserEmail = authentication.getName();
            User currentUser = userRepository.findByEmail(currentUserEmail)
                    .orElseThrow(() -> new RuntimeException("Current user not found: " + currentUserEmail));
            onlineStatusService.heartbeat(currentUser.getId());

            log.info("✅ Successfully retrieved profile page for userId: {}", userId);
            return ResponseEntity.ok(profilePage);

        } catch (RuntimeException e) {
            log.warn("❌ User not found with ID: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error getting profile page for userId {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 🔥 ENDPOINT CHÍNH: Lấy TOÀN BỘ thông tin profile (posts + friends + info)
     * Giống như Facebook profile page - một request trả về tất cả
     * Privacy filtering được áp dụng dựa trên mối quan hệ giữa users
     */
    @GetMapping("/{userId}/full")
    @Operation(
            summary = "Get complete user profile with posts and friends",
            description = "Get full profile information including posts (filtered by privacy), friends list, and mutual friends in a single optimized request. Privacy rules: Own profile = see all, Friends = see PUBLIC + FRIENDS posts, Non-friends = see only PUBLIC posts"
    )
    public ResponseEntity<FullUserProfileDto> getFullUserProfile(@PathVariable Long userId, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            log.info("���� Getting FULL profile for userId: {} (requested by: {})", userId, authentication.getName());

            FullUserProfileDto fullProfile = fullUserProfileService.getFullUserProfile(userId);

            log.info("✅ Successfully retrieved full profile for user: {} - Posts: {}, Friends: {}, Mutual Friends: {}",
                    userId,
                    fullProfile.getPosts().size(),
                    fullProfile.getFriends().size(),
                    fullProfile.getMutualFriends().size());

            return ResponseEntity.ok(fullProfile);

        } catch (RuntimeException e) {
            log.warn("❌ User not found with ID: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error getting full user profile for ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Lấy thông tin user profile với online status để hiển thị khi click vào avatar
     * Đặt endpoint này CUỐI CÙNG vì nó có thể match mọi path
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Get user profile by ID with online status", description = "Get user profile information by user ID including online status")
    public ResponseEntity<Map<String, Object>> getUserProfile(@PathVariable Long userId) {
        try {
            log.info("🔍 Getting user profile for ID: {} (NOT current user)", userId);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

            log.info("🎯 Found user: {} (email: {})", user.getId(), user.getEmail());

            UserProfileDto userProfile = userProfileMapper.toUserProfileDto(user);

            // Debug log to verify avatar mapping - FIX: Get avatar from user.getAvatarUrl()
            String avatarUrl = user.getAvatarUrl();
            log.info("📸 Avatar URL for user {}: {}", userId, avatarUrl != null ? avatarUrl : "NULL");

            // Get current user để update heartbeat cho người đang xem
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String currentUserEmail = authentication.getName();
            User currentUser = userRepository.findByEmail(currentUserEmail)
                    .orElseThrow(() -> new RuntimeException("Current user not found: " + currentUserEmail));

            // Update heartbeat cho current user (người đang xem profile)
            onlineStatusService.heartbeat(currentUser.getId());

            // Get online status information cho user được xem
            String onlineStatus = onlineStatusService.getOnlineStatus(userId);
            boolean isOnline = onlineStatusService.isUserOnline(userId);
            LocalDateTime lastSeen = onlineStatusService.getLastSeen(userId);

            // Create enhanced response with online status
            Map<String, Object> response = Map.of(
                    "userId", user.getId(),
                    "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                    "lastName", user.getLastName() != null ? user.getLastName() : "",
                    "email", user.getEmail(),
                    "avatar", user.getAvatarUrl(), // SIMPLE: Just use user.getAvatarUrl() - it already handles S3 conversion
                    "onlineStatus", onlineStatus,
                    "isOnline", isOnline,
                    "lastSeen", lastSeen != null ? lastSeen : user.getCreatedAt(),
                    "profile", userProfile
            );

            log.info("✅ Response for user {}: avatar={}, status={}",
                    userId,
                    avatarUrl != null ? "present" : "null",
                    onlineStatus);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.warn("User not found with ID: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error getting user profile for ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 🔥 NEW: LinkedIn/Facebook style profile page endpoint using userId (STABLE)
     * GET /api/user-profiles/user/{userId}/profile
     * Returns complete profile with header, basic info, and tab counts
     * userId is more stable than username which can be changed
     */
    @GetMapping("/user/{userId}/profile")
    @Operation(
            summary = "Get LinkedIn/Facebook style profile page by userId",
            description = "Get complete profile page with header (cover + avatar + name + premium badge), basic info (job title, department, about me), and tab counts for Posts/Friends/Tasks. Using userId for stability."
    )
    public ResponseEntity<ProfilePageDto> getProfilePageByUserId(@PathVariable Long userId, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            log.info("🎭 Getting profile page for userId: {} (requested by: {})", userId, authentication.getName());

            ProfilePageDto profilePage = profilePageService.getProfilePageByUserId(userId);

            log.info("✅ Successfully retrieved profile page for userId: {} - Premium: {}, Posts: {}, Friends: {}, Tasks: {}",
                    userId,
                    profilePage.isPremium(),
                    profilePage.getTabCounts().getPostsCount(),
                    profilePage.getTabCounts().getFriendsCount(),
                    profilePage.getTabCounts().getTasksCount());

            return ResponseEntity.ok(profilePage);

        } catch (RuntimeException e) {
            log.warn("❌ User not found with userId: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error getting profile page for userId {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 🔥 NEW: Get tab content by userId (Posts, Friends, or Tasks)
     * GET /api/user-profiles/user/{userId}/tabs/{tabType}?page=0&size=10
     */
    @GetMapping("/user/{userId}/tabs/{tabType}")
    @Operation(
            summary = "Get tab content for profile page by userId",
            description = "Get paginated content for a specific tab (posts, friends, tasks) with proper privacy filtering using stable userId"
    )
    public ResponseEntity<ProfileTabContentDto> getProfileTabContentByUserId(
            @PathVariable Long userId,
            @PathVariable String tabType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            // Validate tab type
            if (!List.of("posts", "friends", "tasks").contains(tabType.toLowerCase())) {
                log.warn("Invalid tab type: {}", tabType);
                return ResponseEntity.badRequest().build();
            }

            log.info("📂 Getting {} tab content for userId: {} (page: {}, size: {}) requested by {}",
                    tabType, userId, page, size, authentication.getName());

            ProfileTabContentDto tabContent = profilePageService.getTabContentByUserId(userId, tabType, page, size);

            log.info("✅ Successfully retrieved {} tab content for userId: {}", tabType, userId);

            return ResponseEntity.ok(tabContent);

        } catch (RuntimeException e) {
            log.warn("❌ User not found with userId: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error getting {} tab content for userId {}: {}", tabType, userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }


    /**
     * 🔥 NEW: Get current user's profile page with online status
     * GET /api/user-profiles/me/profile-with-status
     */
    @GetMapping("/me/profile-with-status")
    @Operation(
            summary = "Get current user's profile page with online status",
            description = "Get complete profile page for the current authenticated user including online status information"
    )
    public ResponseEntity<Map<String, Object>> getCurrentUserProfilePageWithStatus(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            String userEmail = authentication.getName();
            log.info("🎭 Getting profile page with online status for current user: {}", userEmail);

            User currentUser = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Current user not found: " + userEmail));

            // Update heartbeat when user accesses their profile
            onlineStatusService.heartbeat(currentUser.getId());

            // Get profile page data
            ProfilePageDto profilePage = profilePageService.getProfilePageByUserId(currentUser.getId());

            // Get online status information
            String onlineStatus = onlineStatusService.getOnlineStatus(currentUser.getId());
            boolean isOnline = onlineStatusService.isUserOnline(currentUser.getId());
            LocalDateTime lastSeen = onlineStatusService.getLastSeen(currentUser.getId());

            // Create enhanced response with both profile page and online status
            Map<String, Object> response = Map.of(
                    "userId", currentUser.getId(),
                    "email", currentUser.getEmail(),
                    "onlineStatus", onlineStatus,  // "online", "away", "offline"
                    "isOnline", isOnline,
                    "lastSeen", lastSeen != null ? lastSeen : currentUser.getCreatedAt(),
                    "profilePage", profilePage  // Complete profile page data
            );

            log.info("✅ Successfully retrieved profile page with online status for current user: {} - Premium: {}, Status: {}",
                    userEmail, profilePage.isPremium(), onlineStatus);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error getting current user profile page with online status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 🔥 NEW: Get current user's tab content
     * GET /api/user-profiles/me/tabs/{tabType}?page=0&size=10
     */
    @GetMapping("/me/tabs/{tabType}")
    @Operation(
            summary = "Get current user's tab content",
            description = "Get paginated tab content for the current authenticated user"
    )
    public ResponseEntity<ProfileTabContentDto> getCurrentUserTabContent(
            @PathVariable String tabType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                return ResponseEntity.status(401).build();
            }

            // Validate tab type
            if (!List.of("posts", "friends", "tasks").contains(tabType.toLowerCase())) {
                log.warn("Invalid tab type: {}", tabType);
                return ResponseEntity.badRequest().build();
            }

            String userEmail = authentication.getName();
            log.info("📂 Getting {} tab content for current user: {} (page: {}, size: {})",
                    tabType, userEmail, page, size);

            User currentUser = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Current user not found: " + userEmail));

            // Use userId-based service method for consistency
            ProfileTabContentDto tabContent = profilePageService.getTabContentByUserId(currentUser.getId(), tabType, page, size);

            log.info("✅ Successfully retrieved {} tab content for current user: {}", tabType, userEmail);

            return ResponseEntity.ok(tabContent);

        } catch (Exception e) {
            log.error("❌ Error getting {} tab content for current user: {}", tabType, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 🔍 USER LOOKUP APIs - PHẢI ĐẶT TRƯỚC /{userId} ĐỂ TRÁNH CONFLICT
     */

    /**
     * 1. Single Email Lookup
     * GET /api/user-profiles/lookup?email={email}
     */
    @GetMapping("/lookup")
    @Operation(summary = "Lookup user by email", description = "Find user by email address for invitations and sharing")
    public ResponseEntity<UserLookupDto> lookupUser(@RequestParam String email, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated for lookup");
                return ResponseEntity.status(401).build();
            }

            log.info("🔍 Looking up user with email: {}", email);

            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                log.info("❌ User not found with email: {}", email);
                return ResponseEntity.notFound().build();
            }

            // Get online status
            boolean isOnline = onlineStatusService.isUserOnline(user.getId());

            UserLookupDto userLookup = UserLookupDto.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .username(user.getUsername())
                    .avatarUrl(user.getAvatarUrl())
                    .exists(true)
                    .isOnline(isOnline)
                    .build();

            log.info("✅ Found user: {} {} ({})", user.getFirstName(), user.getLastName(), user.getEmail());
            return ResponseEntity.ok(userLookup);

        } catch (Exception e) {
            log.error("❌ Error looking up user with email {}: {}", email, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 2. User Search Autocomplete
     * GET /api/user-profiles/search?q={query}&limit={limit}
     */
    @GetMapping("/search")
    @Operation(summary = "Search users for autocomplete", description = "Search users by name, email or username")
    public ResponseEntity<List<UserLookupDto>> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated for search");
                return ResponseEntity.status(401).build();
            }

            if (q == null || q.length() < 2) {
                return ResponseEntity.ok(new ArrayList<>());
            }

            log.info("🔍 Searching users with query: '{}', limit: {}", q, limit);

            // Search in firstName, lastName, email, username
            List<User> users = userRepository.findUsersForSearch(q.toLowerCase(), limit);

            List<UserLookupDto> results = users.stream()
                    .map(user -> {
                        boolean isOnline = onlineStatusService.isUserOnline(user.getId());
                        return UserLookupDto.builder()
                                .userId(user.getId())
                                .email(user.getEmail())
                                .firstName(user.getFirstName())
                                .lastName(user.getLastName())
                                .username(user.getUsername())
                                .avatarUrl(user.getAvatarUrl())
                                .exists(true)
                                .isOnline(isOnline)
                                .build();
                    })
                    .collect(Collectors.toList());

            log.info("✅ Found {} users for query: '{}'", results.size(), q);
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            log.error("❌ Error searching users with query '{}': {}", q, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 3. Bulk Email Lookup
     * POST /api/user-profiles/lookup/bulk
     */
    @PostMapping("/lookup/bulk")
    @Operation(summary = "Bulk lookup users by emails", description = "Lookup multiple users by email addresses")
    public ResponseEntity<Map<String, Object>> bulkLookupUsers(
            @RequestBody List<String> emails,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated for bulk lookup");
                return ResponseEntity.status(401).build();
            }

            log.info("🔍 Bulk lookup for {} emails", emails.size());

            List<UserLookupDto> existingUsers = new ArrayList<>();
            List<String> nonExistentEmails = new ArrayList<>();

            for (String email : emails) {
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null) {
                    boolean isOnline = onlineStatusService.isUserOnline(user.getId());
                    UserLookupDto userLookup = UserLookupDto.builder()
                            .userId(user.getId())
                            .email(user.getEmail())
                            .firstName(user.getFirstName())
                            .lastName(user.getLastName())
                            .username(user.getUsername())
                            .avatarUrl(user.getAvatarUrl())
                            .exists(true)
                            .isOnline(isOnline)
                            .build();
                    existingUsers.add(userLookup);
                } else {
                    nonExistentEmails.add(email);
                }
            }

            Map<String, Object> result = Map.of(
                    "existingUsers", existingUsers,
                    "nonExistentEmails", nonExistentEmails,
                    "totalRequested", emails.size(),
                    "foundCount", existingUsers.size(),
                    "notFoundCount", nonExistentEmails.size()
            );

            log.info("✅ Bulk lookup complete: {} found, {} not found", existingUsers.size(), nonExistentEmails.size());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ Error in bulk lookup: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 4. Email Validation (No auth required)
     * GET /api/user-profiles/validate/email?email={email}
     */
    @GetMapping("/validate/email")
    @Operation(summary = "Validate email availability", description = "Check if email is available for registration")
    public ResponseEntity<Map<String, Object>> validateEmail(@RequestParam String email) {
        try {
            log.info("🔍 Validating email: {}", email);

            boolean exists = userRepository.findByEmail(email).isPresent();

            Map<String, Object> result = Map.of(
                    "email", email,
                    "exists", exists,
                    "available", !exists,
                    "message", exists ? "Email đã được sử dụng" : "Email có thể sử dụng"
            );

            log.info("✅ Email validation: {} - exists: {}", email, exists);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ Error validating email {}: {}", email, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
