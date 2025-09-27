package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.UserDto.*;
import com.example.taskmanagement_backend.dtos.UserProfileDto.UserProfileResponseDto;
import com.example.taskmanagement_backend.entities.Organization;
import com.example.taskmanagement_backend.entities.Role;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.exceptions.DuplicateEmailException;
import com.example.taskmanagement_backend.exceptions.HttpException;
import com.example.taskmanagement_backend.enums.UserStatus;
import com.example.taskmanagement_backend.enums.SystemRole;
import jakarta.persistence.EntityNotFoundException;
import com.example.taskmanagement_backend.repositories.OrganizationJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import com.example.taskmanagement_backend.repositories.UserProfileRepository;
import com.example.taskmanagement_backend.services.infrastructure.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {
    private final UserJpaRepository userRepository;
    private final OrganizationJpaRepository organizationRepository;
    private final JwtService jwtService;
    private final com.example.taskmanagement_backend.search.services.SearchEventPublisher searchEventPublisher;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileService userProfileService;

    @Autowired
    @Lazy
    @Qualifier("teamService")
    private TeamService teamService;

    @Autowired
    @Lazy
    @Qualifier("projectService")
    private ProjectService projectService;

    public LoginResponseDto login(LoginRequestDto request) throws Exception {
        // Find the user by email (username)
        User user = this.userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new HttpException("Invalid username or password", HttpStatus.UNAUTHORIZED));

        // Verify password
        if (!request.getPassword().equals(user.getPassword())) {
            throw new HttpException("Invalid username or password", HttpStatus.UNAUTHORIZED);
        }

        // ✅ FIX: Update lastLoginAt when user successfully logs in
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("✅ [UserService] Updated lastLoginAt for user: {} at {}", user.getEmail(), user.getLastLoginAt());

        UserProfileResponseDto userProfileResponseDto = userProfileService.getUserProfile(user.getId());

        // ✅ UPDATED: Use systemRole instead of roles relationship
        List<Role> roles = new ArrayList<>(); // Empty list for backward compatibility

        // Generate a new access token (with full data + systemRole)
        String accessToken = jwtService.generateAccessToken(user, userProfileResponseDto);

        return LoginResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .roles(roles) // Keep empty for backward compatibility
                .profile(userProfileResponseDto)
                .accessToken(accessToken)
                .build();
    }


    @CachePut(value = "users", key = "#result.id")
    /**
     * Get current user data for NextAuth integration
     * Consolidates multiple auth calls into single endpoint
     */
    public UserResponseDto getCurrentUserData(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));

        return convertToDto(user);
    }

    public UserResponseDto createUser(CreateUserRequestDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateEmailException("Email already exists: " + dto.getEmail());
        }

        // ✅ UPDATED: No longer use roles relationship, use systemRole instead
        // Set default systemRole to MEMBER if not specified
        SystemRole systemRole = SystemRole.MEMBER; // Default role

        // Get organization if specified
        Organization organization = null;
        if (dto.getOrganizationId() != null) {
            organization = organizationRepository.findById(dto.getOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
        }

        // Create user with systemRole
        User user = User.builder()
                .email(dto.getEmail())
                .password(dto.getPassword())
                .systemRole(systemRole) // ✅ NEW: Set systemRole directly
                .firstLogin(true)
                .deleted(false)
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Save user first to get ID
        User savedUser = userRepository.save(user);

        // ✅ Publish Kafka event for search indexing
        try {
            searchEventPublisher.publishUserCreated(savedUser.getId(), savedUser.getId());
            System.out.println("📤 Published USER_CREATED event to Kafka for user: " + savedUser.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to publish USER_CREATED event for user " + savedUser.getId() + ": " + e.getMessage());
        }

        // Create empty UserProfile
        UserProfile profile = UserProfile.builder()
                .user(savedUser)
                .firstName("")
                .lastName("")
                .status("active")
                .avtUrl("")
                .build();

        savedUser.setUserProfile(profile);
        userProfileRepository.save(profile);

        return convertToDto(savedUser);
    }

    @CachePut(value = "users", key = "#result.id")
    public UserResponseDto updateUser(Long id, UpdateUserRequestDto dto) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        user.setEmail(dto.getEmail());
        user.setFirstLogin(dto.isFirstLogin());
        user.setUpdatedAt(LocalDateTime.now());

        if (dto.getOrganizationId() != null) {
            Organization organization = organizationRepository.findById(dto.getOrganizationId()).orElse(null);
            user.setOrganization(organization);
        }

        UserProfile profile = user.getUserProfile();
        if (profile != null) {
            profile.setFirstName(dto.getFirstName());
            profile.setLastName(dto.getLastName());
            profile.setStatus(dto.getStatus());
            profile.setAvtUrl(dto.getAvtUrl());
        }

        UserResponseDto result = convertToDto(userRepository.save(user));

        // ✅ NEW: Publish Kafka event for search indexing after user update
        try {
            searchEventPublisher.publishUserProfileUpdated(user.getId(), user.getId());
            System.out.println("📤 Published USER_PROFILE_UPDATED event to Kafka for user: " + user.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to publish USER_PROFILE_UPDATED event for user " + user.getId() + ": " + e.getMessage());
        }

        return result;
    }

    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setDeleted(true);
        userRepository.save(user);

        // ✅ NEW: Publish Kafka event for search indexing after user deletion
        try {
            searchEventPublisher.publishUserDeleted(user.getId(), user.getId());
            System.out.println("📤 Published USER_DELETED event to Kafka for user: " + user.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to publish USER_DELETED event for user " + user.getId() + ": " + e.getMessage());
        }
    }

    @Cacheable(value = "users", key = "#id")
    public UserResponseDto getUserById(Long id) {
        System.out.println("📌 Load DB");
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDto(user);
       // return user;
    }

    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(u -> !u.isDeleted())
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * ✅ UPDATED: Update user system role instead of roles relationship
     */
    @CachePut(value = "users", key = "#userId")
    public UserResponseDto updateUserSystemRole(Long userId, SystemRole systemRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setSystemRole(systemRole); // ✅ Update systemRole directly
        user.setUpdatedAt(LocalDateTime.now());

        return convertToDto(userRepository.save(user));
    }

    /**
     * ✅ NEW: Get user by system role
     */
    public List<UserResponseDto> getUsersBySystemRole(SystemRole systemRole) {
        return userRepository.findBySystemRoleAndDeletedFalse(systemRole).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * ✅ UPDATED: Assign system role to user (admin only) - replaces old assignRoleToUser
     */
    public void assignSystemRoleToUser(Long userId, SystemRole systemRole) {
        try {
            log.info("🔄 [UserService] Assigning system role {} to user: {}", systemRole, userId);

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

            SystemRole previousRole = user.getSystemRole();
            user.setSystemRole(systemRole);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("✅ [UserService] Successfully assigned system role {} to user: {} (previous: {})",
                    systemRole, userId, previousRole);
        } catch (Exception e) {
            log.error("❌ [UserService] Error assigning system role: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to assign system role", e);
        }
    }

    /**
     * ✅ NEW: Get system role statistics
     */
    public Map<String, Object> getSystemRoleStatistics() {
        try {
            log.info("🔍 [UserService] Getting system role statistics");

            Map<String, Object> stats = new HashMap<>();

            for (SystemRole role : SystemRole.values()) {
                Long count = userRepository.countBySystemRoleAndDeletedFalse(role);
                stats.put(role.name().toLowerCase() + "Count", count);
            }

            stats.put("lastUpdated", LocalDateTime.now());
            return stats;
        } catch (Exception e) {
            log.error("❌ [UserService] Error getting system role statistics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get system role statistics", e);
        }
    }

    /**
     * ✅ UPDATED: Perform bulk system role operations
     */
    public Map<String, Object> performBulkSystemRoleAction(Map<String, Object> bulkRequest) {
        try {
            log.info("🔄 [UserService] Performing bulk system role action: {}", bulkRequest);

            String action = (String) bulkRequest.get("action");
            @SuppressWarnings("unchecked")
            List<Long> userIds = (List<Long>) bulkRequest.get("userIds");
            String roleString = (String) bulkRequest.get("systemRole");

            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();

            SystemRole systemRole = null;
            if (roleString != null) {
                try {
                    systemRole = SystemRole.valueOf(roleString.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException("Invalid system role: " + roleString);
                }
            }

            for (Long userId : userIds) {
                try {
                    switch (action) {
                        case "ASSIGN_ROLE":
                            if (systemRole != null) {
                                assignSystemRoleToUser(userId, systemRole);
                                successCount++;
                            } else {
                                errors.add("User " + userId + ": No system role specified");
                                failCount++;
                            }
                            break;
                        case "RESET_TO_MEMBER":
                            assignSystemRoleToUser(userId, SystemRole.MEMBER);
                            successCount++;
                            break;
                        default:
                            errors.add("Unknown action: " + action);
                            failCount++;
                    }
                } catch (Exception e) {
                    errors.add("User " + userId + ": " + e.getMessage());
                    failCount++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("action", action);
            result.put("systemRole", roleString);
            result.put("totalRequested", userIds.size());
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("errors", errors);
            result.put("completedAt", LocalDateTime.now());

            return result;
        } catch (Exception e) {
            log.error("❌ [UserService] Error performing bulk system role action: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to perform bulk system role action", e);
        }
    }

    // ===== PREMIUM/UPGRADED USER MANAGEMENT =====

    /**
     * Get premium/upgraded users for admin with filtering
     */
    public Page<AdminUserResponseDto> getPremiumUsersForAdmin(
            Pageable pageable, String premiumFilter, String upgradeFilter, 
            String planType, boolean expiredOnly) {
        try {
            log.info("🔍 [UserService] Getting premium users - Premium: {}, Upgrade: {}, Plan: {}, Expired: {}",
                    premiumFilter, upgradeFilter, planType, expiredOnly);

            Specification<User> spec = (root, query, criteriaBuilder) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                
                // Always exclude deleted users
                predicates.add(criteriaBuilder.equal(root.get("deleted"), false));
                
                // Join with UserProfile
                var profileJoin = root.join("userProfile");
                
                // Filter by premium status
                if (!"ALL".equals(premiumFilter)) {
                    boolean isPremium = "PREMIUM".equals(premiumFilter);
                    predicates.add(criteriaBuilder.equal(profileJoin.get("isPremium"), isPremium));
                }
                
                // Filter by upgrade status
                if (!"ALL".equals(upgradeFilter)) {
                    boolean isUpgraded = "UPGRADED".equals(upgradeFilter);
                    predicates.add(criteriaBuilder.equal(profileJoin.get("isUpgraded"), isUpgraded));
                }
                
                // Filter by plan type
                if (planType != null && !planType.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.equal(profileJoin.get("premiumPlanType"), planType));
                }
                
                // Filter expired premium only
                if (expiredOnly) {
                    predicates.add(criteriaBuilder.equal(profileJoin.get("isPremium"), true));
                    predicates.add(criteriaBuilder.lessThan(profileJoin.get("premiumExpiry"), LocalDateTime.now()));
                }
                
                return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };

            Page<User> userPage = userRepository.findAll(spec, pageable);
            List<AdminUserResponseDto> adminUsers = userPage.getContent().stream()
                .map(this::convertToAdminDto)
                .collect(Collectors.toList());

            return new PageImpl<>(adminUsers, pageable, userPage.getTotalElements());
        } catch (Exception e) {
            log.error("❌ [UserService] Error getting premium users: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get premium users", e);
        }
    }

    /**
     * Get premium/upgrade statistics
     */
    public Map<String, Object> getPremiumStatistics() {
        try {
            log.info("🔍 [UserService] Getting premium statistics");

            // Count manually using specifications since repository methods don't exist
            Long totalPremiumUsers = userRepository.count(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.join("userProfile").get("isPremium"), true)
            );

            Long totalUpgradedUsers = userRepository.count(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.join("userProfile").get("isUpgraded"), true)
            );

            Long expiredPremiumUsers = userRepository.count(
                (root, query, criteriaBuilder) -> criteriaBuilder.and(
                    criteriaBuilder.equal(root.join("userProfile").get("isPremium"), true),
                    criteriaBuilder.lessThan(root.join("userProfile").get("premiumExpiry"), LocalDateTime.now())
                )
            );

            Long activePremiumUsers = totalPremiumUsers - expiredPremiumUsers;

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalPremiumUsers", totalPremiumUsers);
            stats.put("activePremiumUsers", activePremiumUsers);
            stats.put("expiredPremiumUsers", expiredPremiumUsers);
            stats.put("totalUpgradedUsers", totalUpgradedUsers);
            stats.put("lastUpdated", LocalDateTime.now());

            return stats;
        } catch (Exception e) {
            log.error("❌ [UserService] Error getting premium statistics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get premium statistics", e);
        }
    }

    /**
     * Update user premium status
     */
    public Map<String, Object> updateUserPremiumStatus(
            Long userId, Boolean isPremium, String planType, 
            LocalDateTime premiumExpiry, String reason) {
        try {
            log.info("🔄 [UserService] Updating premium status for user: {} - Premium: {}", userId, isPremium);

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
            
            UserProfile profile = user.getUserProfile();
            if (profile == null) {
                throw new RuntimeException("User profile not found for user: " + userId);
            }

            Boolean previousPremiumStatus = profile.getIsPremium();
            profile.setIsPremium(isPremium);
            
            if (isPremium != null && isPremium) {
                profile.setPremiumPlanType(planType);
                profile.setPremiumExpiry(premiumExpiry);
            } else {
                profile.setPremiumPlanType(null);
                profile.setPremiumExpiry(null);
            }

            userProfileRepository.save(profile);

            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("email", user.getEmail());
            result.put("previousPremiumStatus", previousPremiumStatus);
            result.put("newPremiumStatus", isPremium);
            result.put("planType", planType);
            result.put("premiumExpiry", premiumExpiry);
            result.put("reason", reason);
            result.put("updatedAt", LocalDateTime.now());

            return result;
        } catch (Exception e) {
            log.error("❌ [UserService] Error updating premium status: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update premium status", e);
        }
    }



    /**
     * Get users with expiring premium
     */
    public Page<AdminUserResponseDto> getExpiringPremiumUsers(Pageable pageable, int daysBefore) {
        try {
            log.info("🔍 [UserService] Getting users with premium expiring in {} days", daysBefore);

            LocalDateTime expiryThreshold = LocalDateTime.now().plusDays(daysBefore);

            Specification<User> spec = (root, query, criteriaBuilder) -> {
                var profileJoin = root.join("userProfile");

                return criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("deleted"), false),
                    criteriaBuilder.equal(profileJoin.get("isPremium"), true),
                    criteriaBuilder.lessThanOrEqualTo(profileJoin.get("premiumExpiry"), expiryThreshold),
                    criteriaBuilder.greaterThan(profileJoin.get("premiumExpiry"), LocalDateTime.now())
                );
            };

            Page<User> userPage = userRepository.findAll(spec, pageable);
            List<AdminUserResponseDto> adminUsers = userPage.getContent().stream()
                .map(this::convertToAdminDto)
                .collect(Collectors.toList());

            return new PageImpl<>(adminUsers, pageable, userPage.getTotalElements());
        } catch (Exception e) {
            log.error("❌ [UserService] Error getting expiring premium users: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get expiring premium users", e);
        }
    }

    /**
     * Get premium revenue analytics
     */
    public Map<String, Object> getPremiumRevenueAnalytics(String period, Integer count) {
        try {
            log.info("🔍 [UserService] Getting premium revenue analytics - Period: {}, Count: {}", period, count);

            // This is a placeholder implementation
            // In a real application, you would calculate actual revenue based on subscription data
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("period", period);
            analytics.put("count", count);
            analytics.put("totalRevenue", 0.0);
            analytics.put("averageRevenuePerUser", 0.0);
            analytics.put("conversionRate", 0.0);
            analytics.put("generatedAt", LocalDateTime.now());

            return analytics;
        } catch (Exception e) {
            log.error("❌ [UserService] Error getting premium revenue analytics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get premium revenue analytics", e);
        }
    }

    // ===== ONBOARDING SUPPORT METHODS =====

    /**
     * Get total users count for onboarding statistics
     */
    public long getTotalUsersCount() {
        return userRepository.count();
    }

    /**
     * Get new users count this month for onboarding statistics
     */
    public long getNewUsersCountThisMonth() {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        return userRepository.countByCreatedAtAfter(startOfMonth);
    }

    /**
     * Get users who still have firstLogin = true (need onboarding)
     */
    public long getFirstLoginUsersCount() {
        return userRepository.findAll().stream()
                .filter(User::isFirstLogin)
                .count();
    }

    // ===== HELPER METHODS =====

    /**
     * Generate a temporary password for user password resets
     */
    private String generateTemporaryPassword() {
        final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        final int PASSWORD_LENGTH = 12;

        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }

        return password.toString();
    }

    /**
     * Convert User to AdminUserResponseDto
     */
    private AdminUserResponseDto convertToAdminDto(User user) {
        UserProfile profile = user.getUserProfile();

        // Lấy role trực tiếp từ systemRole field thay vì từ roles relationship
        String systemRoleName = user.getSystemRole() != null ? user.getSystemRole().name() : "MEMBER";

        // Count user's teams and projects
        int totalTeams = 0;
        int totalProjects = 0;
        int totalTasks = 0;

        try {
            if (teamService != null) {
                totalTeams = teamService.getTeamsByUserId(user.getId()).size();
            }
            if (projectService != null) {
                totalProjects = projectService.getProjectsByUserId(user.getId()).size();
            }
            // TODO: Add task counting when TaskService is available
        } catch (Exception e) {
            log.warn("Could not count teams/projects for user {}: {}", user.getId(), e.getMessage());
        }

        // Calculate full name and display name
        String firstName = profile != null ? profile.getFirstName() : null;
        String lastName = profile != null ? profile.getLastName() : null;
        String fullName = getFullName(user);
        String displayName = fullName.isEmpty() || fullName.equals(user.getEmail()) ?
            (firstName != null && !firstName.isEmpty() ? firstName : user.getEmail()) : fullName;

        return AdminUserResponseDto.builder()
            .id(user.getId())
            .email(user.getEmail())
            .firstName(firstName)
            .lastName(lastName)
            .fullName(fullName)
            .displayName(displayName)
            .avatarUrl(profile != null ? profile.getAvtUrl() : null)
            .status(user.getStatus())
            .firstLogin(user.isFirstLogin())
            .deleted(user.isDeleted())
            .online(user.isOnline())
            .isOnline(user.isOnline())
            .onlineStatus(user.isOnline() ? "ONLINE" : "OFFLINE")
            .isEmailVerified(false) // Default to false since UserProfile doesn't have this field
            .emailVerified(false)   // Default to false since UserProfile doesn't have this field
            .lastLoginAt(user.getLastLoginAt())
            .lastSeenAt(null)       // User entity doesn't have this field
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .department(profile != null ? profile.getDepartment() : null)
            .jobTitle(profile != null ? profile.getJobTitle() : null)
            .aboutMe(profile != null ? profile.getAboutMe() : null)
            .roles(List.of()) // Empty list since we're using systemRole instead
            .roleNames(List.of(systemRoleName)) // Lấy từ systemRole field
            .organizationName(user.getOrganization() != null ? user.getOrganization().getName() : null)
            .totalTeams(totalTeams)
            .totalProjects(totalProjects)
            .totalTasks(totalTasks)
            .isPremium(profile != null && profile.getIsPremium() != null ? profile.getIsPremium() : false)
            .premium(profile != null && profile.getIsPremium() != null ? profile.getIsPremium() : false)

            .premiumPlanType(profile != null ? profile.getPremiumPlanType() : null)
            .premiumExpiry(profile != null ? profile.getPremiumExpiry() : null)
            .registrationSource(determineRegistrationSource(user))
            .build();
    }

    /**
     * Get full name from user profile
     */
    private String getFullName(User user) {
        UserProfile profile = user.getUserProfile();
        if (profile == null) return user.getEmail();
        
        String firstName = profile.getFirstName();
        String lastName = profile.getLastName();
        
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return user.getEmail();
        }
    }

    /**
     * Determine user registration source based on user data
     */
    private String determineRegistrationSource(User user) {
        // This is a simple heuristic - in a real app you might store this information
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            return "GOOGLE_OAUTH";
        } else if (user.isFirstLogin()) {
            return "INVITATION";
        } else {
            return "MANUAL";
        }
    }

    public UserResponseDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDto(user);
    }

    /**
     * Clear cache to prevent type mismatch issues
     */
    @CacheEvict(value = "userIdByEmail", allEntries = true)
    public void clearUserIdCache() {
        log.info("🧹 [UserService] Cleared userIdByEmail cache to prevent type mismatch");
    }

    /**
     * Get user ID by email for chat system integration
     * Returns Long to match entity ID type with robust type handling
     */
    @Cacheable(value = "userIdByEmail", key = "#email")
    public Long getUserIdByEmail(String email) {
        try {
            User user = userRepository.findByEmail(email)
                    .orElse(null);
            if (user != null) {
                // Ensure we return Long type explicitly to avoid cache type mismatch
                Long userId = user.getId(); // User.id is already Long type
                log.debug("Found user ID {} for email {}", userId, email);
                return userId;
            }
            log.warn("No user found for email: {}", email);
            return null;
        } catch (Exception e) {
            log.error("Error getting user ID by email {}: {}", email, e.getMessage(), e);
            // Clear cache on error to prevent corrupted cache entries
            try {
                clearUserIdCache();
            } catch (Exception clearError) {
                log.warn("Failed to clear cache after error: {}", clearError.getMessage());
            }
            return null;
        }
    }

    /**
     * Alternative method that bypasses cache entirely for critical operations
     */
    public Long getUserIdByEmailDirect(String email) {
        try {
            User user = userRepository.findByEmail(email)
                    .orElse(null);
            if (user != null) {
                Long userId = user.getId();
                log.debug("Found user ID {} for email {} (direct query)", userId, email);
                return userId;
            }
            log.warn("No user found for email: {} (direct query)", email);
            return null;
        } catch (Exception e) {
            log.error("Error getting user ID by email {} (direct): {}", email, e.getMessage(), e);
            return null;
        }
    }

    public UserResponseDto convertToDto(User user) {
        UserProfile profile = user.getUserProfile();

        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(profile != null ? profile.getFirstName() : null)
                .lastName(profile != null ? profile.getLastName() : null)
                .avt_url(profile != null ? profile.getAvtUrl() : null)
                .firstLogin(user.isFirstLogin())
                .deleted(user.isDeleted())
                .status(profile != null ? profile.getStatus() : null)
                .roleNames(List.of(user.getSystemRole().name())) // ✅ UPDATED: Use systemRole
                .organizationName(user.getOrganization() != null ? user.getOrganization().getName() : null)
                .build();
    }

    // ===== ADMIN-SPECIFIC METHODS =====

    /**
     * Get all users for admin with pagination, filtering and search
     */
    public Page<AdminUserResponseDto> getAllUsersForAdmin(Pageable pageable, UserStatus status, String search) {
        try {
            log.info("🔍 [UserService] Getting users for admin - Status: {}, Search: {}", status, search);

            Specification<User> spec = (root, query, criteriaBuilder) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

                // Always exclude deleted users
                predicates.add(criteriaBuilder.equal(root.get("deleted"), false));

                // Filter by status if provided
                if (status != null) {
                    predicates.add(criteriaBuilder.equal(root.get("status"), status));
                }

                // Search by name or email if provided
                if (search != null && !search.trim().isEmpty()) {
                    String searchPattern = "%" + search.toLowerCase() + "%";
                    predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.join("userProfile").get("firstName")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.join("userProfile").get("lastName")), searchPattern)
                    ));
                }

                return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };

            Page<User> userPage = userRepository.findAll(spec, pageable);
            List<AdminUserResponseDto> adminUsers = userPage.getContent().stream()
                .map(this::convertToAdminDto)
                .collect(Collectors.toList());

            return new PageImpl<>(adminUsers, pageable, userPage.getTotalElements());
        } catch (Exception e) {
            log.error("❌ [UserService] Error getting users for admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get users for admin", e);
        }
    }

    /**
     * Get detailed user information for admin
     */
    public AdminUserResponseDto getUserDetailsForAdmin(Long userId) {
        try {
            log.info("🔍 [UserService] Getting user details for admin - ID: {}", userId);

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

            return convertToAdminDto(user);
        } catch (Exception e) {
            log.error("❌ [UserService] Error getting user details for admin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get user details", e);
        }
    }

    /**
     * Change user status (admin only)
     */
    public UserStatusDto changeUserStatus(Long userId, UserStatus status, String reason) {
        try {
            log.info("🔄 [UserService] Changing user {} status to: {} - Reason: {}", userId, status, reason);

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

            UserStatus previousStatus = user.getStatus() != null ? user.getStatus() : UserStatus.ACTIVE;
            user.setStatus(status);
            user.setUpdatedAt(LocalDateTime.now());

            // Get current admin user for audit
            String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();

            userRepository.save(user);

            log.info("✅ [UserService] Successfully changed user {} status from {} to {}",
                    userId, previousStatus, status);

            return UserStatusDto.success(
                userId,
                user.getEmail(),
                getFullName(user),
                previousStatus,
                status,
                reason,
                currentUserEmail
            );
        } catch (Exception e) {
            log.error("❌ [UserService] Error changing user status: {}", e.getMessage(), e);
            return UserStatusDto.failure(userId, "", "Failed to change user status: " + e.getMessage());
        }
    }

    /**
     * Reset user password (admin only)
     */
    public String resetUserPassword(Long userId, boolean sendEmail) {
        try {
            log.info("🔄 [UserService] Resetting password for user: {} - Send email: {}", userId, sendEmail);

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

            // Generate temporary password
            String temporaryPassword = generateTemporaryPassword();

            // Update user password (in real app, should be encrypted)
            user.setPassword(temporaryPassword);
            user.setFirstLogin(true); // Force password change on next login
            user.setUpdatedAt(LocalDateTime.now());

            userRepository.save(user);

            // TODO: Send email notification if requested
            if (sendEmail) {
                log.info("📧 [UserService] Should send password reset email to: {}", user.getEmail());
                // Implementation for email service would go here
            }

            log.info("✅ [UserService] Successfully reset password for user: {}", userId);
            return temporaryPassword;
        } catch (Exception e) {
            log.error("❌ [UserService] Error resetting password: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to reset password", e);
        }
    }

    /**
     * Get system statistics (admin only)
     */
    public Map<String, Object> getSystemStatistics() {
        try {
            log.info("🔍 [UserService] Getting system statistics");

            Long totalUsers = userRepository.count();
            Long activeUsers = userRepository.countByDeletedFalseAndStatus(UserStatus.ACTIVE);
            Long inactiveUsers = userRepository.countByDeletedFalseAndStatus(UserStatus.INACTIVE);
            Long suspendedUsers = userRepository.countByDeletedFalseAndStatus(UserStatus.SUSPENDED);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", totalUsers);
            stats.put("activeUsers", activeUsers);
            stats.put("inactiveUsers", inactiveUsers);
            stats.put("suspendedUsers", suspendedUsers);
            stats.put("lastUpdated", LocalDateTime.now());

            return stats;
        } catch (Exception e) {
            log.error("❌ [UserService] Error getting system statistics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get system statistics", e);
        }
    }

    /**
     * Get user activity report (admin only)
     */
    public Map<String, Object> getUserActivityReport(Long userId, int days) {
        try {
            log.info("🔍 [UserService] Getting activity report for user: {} - Days: {}", userId, days);

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

            Map<String, Object> activity = new HashMap<>();
            activity.put("userId", userId);
            activity.put("email", user.getEmail());
            activity.put("fullName", getFullName(user));
            activity.put("lastLoginAt", user.getLastLoginAt());
            activity.put("createdAt", user.getCreatedAt());
            activity.put("status", user.getStatus());
            activity.put("reportDays", days);

            return activity;
        } catch (Exception e) {
            log.error("❌ [UserService] Error getting user activity report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get user activity report", e);
        }
    }

    /**
     * Perform bulk user operations (admin only)
     */
    public Map<String, Object> performBulkUserAction(Map<String, Object> bulkRequest) {
        try {
            log.info("🔄 [UserService] Performing bulk action: {}", bulkRequest);

            String action = (String) bulkRequest.get("action");
            @SuppressWarnings("unchecked")
            List<Long> userIds = (List<Long>) bulkRequest.get("userIds");

            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();

            for (Long userId : userIds) {
                try {
                    switch (action) {
                        case "ACTIVATE":
                            changeUserStatus(userId, UserStatus.ACTIVE, "Bulk activation");
                            successCount++;
                            break;
                        case "DEACTIVATE":
                            changeUserStatus(userId, UserStatus.INACTIVE, "Bulk deactivation");
                            successCount++;
                            break;
                        case "SUSPEND":
                            changeUserStatus(userId, UserStatus.SUSPENDED, "Bulk suspension");
                            successCount++;
                            break;
                        default:
                            errors.add("Unknown action: " + action);
                            failCount++;
                    }
                } catch (Exception e) {
                    errors.add("User " + userId + ": " + e.getMessage());
                    failCount++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("action", action);
            result.put("totalRequested", userIds.size());
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("errors", errors);
            result.put("completedAt", LocalDateTime.now());

            return result;
        } catch (Exception e) {
            log.error("❌ [UserService] Error performing bulk action: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to perform bulk action", e);
        }
    }
}
