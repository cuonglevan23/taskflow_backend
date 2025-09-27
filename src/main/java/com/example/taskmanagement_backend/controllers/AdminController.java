package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.AnalyticsDto.AnalyticsFilterDto;
import com.example.taskmanagement_backend.dtos.AnalyticsDto.UserAnalyticsResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.UpdateUserRequestDto;
import com.example.taskmanagement_backend.dtos.UserDto.UserResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.AdminUserResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.UserStatusDto;
import com.example.taskmanagement_backend.dtos.TeamDto.TeamResponseDto;
import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectResponseDto;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.services.UserService;
import com.example.taskmanagement_backend.services.TeamService;
import com.example.taskmanagement_backend.services.ProjectService;
import com.example.taskmanagement_backend.services.UserAnalyticsService;
import com.example.taskmanagement_backend.enums.UserStatus;
import com.example.taskmanagement_backend.enums.SystemRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Management", description = "Admin APIs for managing users, roles, and system-wide operations")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class AdminController {

    private final UserService userService;
    private final TeamService teamService;
    private final ProjectService projectService;
    private final UserAnalyticsService userAnalyticsService;
    private final UserJpaRepository userRepository;

    // ===== USER MANAGEMENT =====

    /**
     * Get all users with pagination and filtering (Admin only)
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all users with pagination",
               description = "Admin endpoint to get all users with pagination, sorting, and filtering options")
    public ResponseEntity<Page<AdminUserResponseDto>> getAllUsers(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Filter by status") @RequestParam(required = false) UserStatus status,
            @Parameter(description = "Search by name or email") @RequestParam(required = false) String search) {

        try {
            Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            log.info("🔍 [AdminController] Getting users - page: {}, size: {}, sortBy: {}, status: {}, search: {}",
                    page, size, sortBy, status, search);

            Page<AdminUserResponseDto> users = userService.getAllUsersForAdmin(pageable, status, search);

            log.info("✅ [AdminController] Retrieved {} users", users.getTotalElements());
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting users: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get detailed user information by ID (Admin only)
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user details by ID",
               description = "Admin endpoint to get detailed user information including roles, teams, projects")
    public ResponseEntity<Map<String, Object>> getUserDetails(@PathVariable Long userId) {
        try {
            log.info("🔍 [AdminController] Getting user details for ID: {}", userId);

            AdminUserResponseDto user = userService.getUserDetailsForAdmin(userId);
            List<TeamResponseDto> teams = teamService.getTeamsByUserId(userId);
            List<ProjectResponseDto> projects = projectService.getProjectsByUserId(userId);

            Map<String, Object> response = Map.of(
                "user", user,
                "teams", teams,
                "projects", projects,
                "totalTeams", teams.size(),
                "totalProjects", projects.size()
            );

            log.info("✅ [AdminController] Retrieved details for user: {} - Teams: {}, Projects: {}",
                    userId, teams.size(), projects.size());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("❌ [AdminController] User not found: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting user details: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update user information (Admin only)
     * Note: Only profile information updates, not account creation
     */
    @PutMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user information",
               description = "Admin endpoint to update user's profile information (users are created via Google OAuth only)")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequestDto dto) {
        try {
            log.info("🔄 [AdminController] Updating user: {} - Data: {}", userId, dto);

            UserResponseDto updatedUser = userService.updateUser(userId, dto);

            log.info("✅ [AdminController] Successfully updated user: {}", userId);
            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            log.warn("❌ [AdminController] User not found: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [AdminController] Error updating user: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Change user status (Admin only)
     */
    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change user status",
               description = "Admin endpoint to activate, deactivate, suspend, or delete user accounts")
    public ResponseEntity<UserStatusDto> changeUserStatus(
            @PathVariable Long userId,
            @RequestParam UserStatus status,
            @RequestParam(required = false) String reason) {
        try {
            log.info("🔄 [AdminController] Changing user {} status to: {} - Reason: {}",
                    userId, status, reason);

            UserStatusDto result = userService.changeUserStatus(userId, status, reason);

            log.info("✅ [AdminController] Successfully changed user {} status to: {}", userId, status);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.warn("❌ [AdminController] User not found: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [AdminController] Error changing user status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Assign system role to user (Admin only)
     * Replaces the old assignRoleToUser method
     */
    @PostMapping("/users/{userId}/system-role/{systemRole}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign system role to user",
               description = "Admin endpoint to assign system role to user (ADMIN, MEMBER, etc.)")
    public ResponseEntity<Map<String, String>> assignSystemRoleToUser(
            @PathVariable Long userId,
            @PathVariable SystemRole systemRole) {
        try {
            log.info("🔄 [AdminController] Assigning system role {} to user: {}", systemRole, userId);

            userService.assignSystemRoleToUser(userId, systemRole);

            Map<String, String> response = Map.of(
                "message", "System role assigned successfully",
                "userId", userId.toString(),
                "systemRole", systemRole.toString()
            );

            log.info("✅ [AdminController] Successfully assigned system role {} to user: {}", systemRole, userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("❌ [AdminController] User not found - User: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [AdminController] Error assigning system role: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DEPRECATED: Legacy role assignment method - redirects to system role assignment
     */
    @PostMapping("/users/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign role to user (DEPRECATED)",
               description = "DEPRECATED: Use /users/{userId}/system-role/{systemRole} instead")
    @Deprecated
    public ResponseEntity<Map<String, String>> assignRoleToUser(
            @PathVariable Long userId,
            @PathVariable Long roleId) {
        try {
            log.info("🔄 [AdminController] DEPRECATED: Assigning role {} to user: {} - Converting to system role", roleId, userId);

            // Convert roleId to SystemRole (simple mapping)
            SystemRole systemRole = roleId == 1L ? SystemRole.ADMIN : SystemRole.MEMBER;
            userService.assignSystemRoleToUser(userId, systemRole);

            Map<String, String> response = Map.of(
                "message", "Role assigned successfully (converted to system role)",
                "userId", userId.toString(),
                "roleId", roleId.toString(),
                "systemRole", systemRole.toString()
            );

            log.info("✅ [AdminController] Successfully assigned system role {} to user: {}", systemRole, userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("❌ [AdminController] User not found - User: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [AdminController] Error assigning role: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DEPRECATED: Legacy role removal method - redirects to system role assignment
     */
    @DeleteMapping("/users/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remove role from user (DEPRECATED)",
               description = "DEPRECATED: Use /users/{userId}/system-role/{systemRole} instead")
    @Deprecated
    public ResponseEntity<Map<String, String>> removeRoleFromUser(
            @PathVariable Long userId,
            @PathVariable Long roleId) {
        try {
            log.info("🔄 [AdminController] DEPRECATED: Removing role {} from user: {} - Setting to MEMBER", roleId, userId);

            // When removing a role, default to MEMBER
            userService.assignSystemRoleToUser(userId, SystemRole.MEMBER);

            Map<String, String> response = Map.of(
                "message", "Role removed successfully (set to MEMBER)",
                "userId", userId.toString(),
                "roleId", roleId.toString(),
                "systemRole", SystemRole.MEMBER.toString()
            );

            log.info("✅ [AdminController] Successfully set user {} to MEMBER role", userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("❌ [AdminController] User not found - User: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [AdminController] Error removing role: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }


    // ===== SYSTEM STATISTICS =====

    /**
     * Get system statistics (Admin only)
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get system statistics",
               description = "Admin endpoint to get overall system statistics")
    public ResponseEntity<Map<String, Object>> getSystemStatistics() {
        try {
            log.info("🔍 [AdminController] Getting system statistics");

            Map<String, Object> stats = userService.getSystemStatistics();

            log.info("✅ [AdminController] Retrieved system statistics");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get user activity report (Admin only)
     */
    @GetMapping("/users/{userId}/activity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user activity report",
               description = "Admin endpoint to get detailed user activity report")
    public ResponseEntity<Map<String, Object>> getUserActivityReport(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "30") int days) {
        try {
            log.info("🔍 [AdminController] Getting activity report for user: {} - Days: {}", userId, days);

            Map<String, Object> activity = userService.getUserActivityReport(userId, days);

            log.info("✅ [AdminController] Retrieved activity report for user: {}", userId);
            return ResponseEntity.ok(activity);
        } catch (RuntimeException e) {
            log.warn("❌ [AdminController] User not found: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting activity report: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===== BULK OPERATIONS =====

    /**
     * Bulk user operations (Admin only)
     */
    @PostMapping("/users/bulk-action")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Perform bulk operations on users",
               description = "Admin endpoint to perform bulk operations like status changes, role assignments")
    public ResponseEntity<Map<String, Object>> bulkUserAction(
            @RequestBody Map<String, Object> bulkRequest) {
        try {
            log.info("🔄 [AdminController] Performing bulk action: {}", bulkRequest);

            Map<String, Object> result = userService.performBulkUserAction(bulkRequest);

            log.info("✅ [AdminController] Successfully performed bulk action");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error performing bulk action: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    // ===== ANALYTICS & CHARTS =====

    /**
     * Get comprehensive user analytics for dashboard charts (Admin only)
     */
    @GetMapping("/analytics/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user analytics for dashboard",
               description = "Admin endpoint to get comprehensive user analytics including charts data for registrations, logins, and growth rates")
    public ResponseEntity<UserAnalyticsResponseDto> getUserAnalytics(
            @Parameter(description = "Period type for grouping") @RequestParam(defaultValue = "month") String periodType,
            @Parameter(description = "Number of periods to include") @RequestParam(defaultValue = "12") Integer periodCount,
            @Parameter(description = "Include registration data") @RequestParam(defaultValue = "true") boolean includeRegistrations,
            @Parameter(description = "Include login data") @RequestParam(defaultValue = "true") boolean includeLogins,
            @Parameter(description = "Include online status") @RequestParam(defaultValue = "true") boolean includeOnlineStatus,
            @Parameter(description = "User status filter") @RequestParam(defaultValue = "ALL") String userStatus) {

        try {
            log.info("🔍 [AdminController] Getting user analytics - Period: {}, Count: {}, Status: {}",
                    periodType, periodCount, userStatus);

            // Build filter object
            AnalyticsFilterDto filter = AnalyticsFilterDto.builder()
                .periodType(periodType)
                .periodCount(periodCount)
                .includeRegistrations(includeRegistrations)
                .includeLogins(includeLogins)
                .includeOnlineStatus(includeOnlineStatus)
                .userStatus(userStatus)
                .timezone("UTC")
                .build();

            // Set date range based on period type and count
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = switch (periodType.toLowerCase()) {
                case "day" -> endDate.minusDays(periodCount);
                case "week" -> endDate.minusWeeks(periodCount);
                case "month" -> endDate.minusMonths(periodCount);
                case "quarter" -> endDate.minusMonths(periodCount * 3);
                case "year" -> endDate.minusYears(periodCount);
                default -> endDate.minusMonths(12); // Default to 12 months
            };

            filter.setStartDate(startDate);
            filter.setEndDate(endDate);

            UserAnalyticsResponseDto analytics = userAnalyticsService.getUserAnalytics(filter);

            log.info("✅ [AdminController] Retrieved user analytics - Total Users: {}, Active: {}, Online: {}",
                    analytics.getTotalUsers(), analytics.getActiveUsers(), analytics.getOnlineUsers());

            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting user analytics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get current user statistics for dashboard overview (Admin only)
     */
    @GetMapping("/analytics/users/current")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get current user statistics",
               description = "Admin endpoint to get current user statistics for dashboard overview cards")
    public ResponseEntity<Map<String, Object>> getCurrentUserStatistics() {
        try {
            log.info("🔍 [AdminController] Getting current user statistics");

            long totalUsers = userRepository.count();
            long activeUsers = userRepository.countByDeletedFalseAndStatus(UserStatus.ACTIVE);
            long onlineUsers = userRepository.countByOnlineTrue();
            long inactiveUsers = userRepository.countByDeletedFalseAndStatus(UserStatus.INACTIVE);
            long suspendedUsers = userRepository.countByDeletedFalseAndStatus(UserStatus.SUSPENDED);

            // Calculate today's and this week's new users
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();
            LocalDateTime monthStart = LocalDate.now().minusMonths(1).atStartOfDay();

            long newUsersToday = userRepository.countByCreatedAtAfter(todayStart);
            long newUsersThisWeek = userRepository.countByCreatedAtAfter(weekStart);
            long newUsersThisMonth = userRepository.countByCreatedAtAfter(monthStart);

            // Calculate growth rates
            long lastMonthUsers = userRepository.countByCreatedAtBetween(monthStart.minusMonths(1), monthStart);
            double monthlyGrowthRate = lastMonthUsers > 0 ?
                ((double) (newUsersThisMonth - lastMonthUsers) / lastMonthUsers) * 100 : 0.0;

            // Create statistics map using HashMap to avoid Map.of() limitations
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalUsers", totalUsers);
            statistics.put("activeUsers", activeUsers);
            statistics.put("onlineUsers", onlineUsers);
            statistics.put("inactiveUsers", inactiveUsers);
            statistics.put("suspendedUsers", suspendedUsers);
            statistics.put("newUsersToday", newUsersToday);
            statistics.put("newUsersThisWeek", newUsersThisWeek);
            statistics.put("newUsersThisMonth", newUsersThisMonth);
            statistics.put("monthlyGrowthRate", monthlyGrowthRate);
            statistics.put("lastUpdated", LocalDateTime.now());

            // Add user distribution as nested map
            Map<String, Long> userDistribution = new HashMap<>();
            userDistribution.put("active", activeUsers);
            userDistribution.put("inactive", inactiveUsers);
            userDistribution.put("suspended", suspendedUsers);
            userDistribution.put("online", onlineUsers);

            statistics.put("userDistribution", userDistribution);

            log.info("✅ [AdminController] Retrieved current user statistics - Total: {}, Active: {}, Online: {}",
                    totalUsers, activeUsers, onlineUsers);

            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting current user statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get user registration trend data for line charts (Admin only)
     */
    @GetMapping("/analytics/users/registrations")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user registration trends",
               description = "Admin endpoint to get user registration trend data for charts")
    public ResponseEntity<Map<String, Object>> getUserRegistrationTrends(
            @Parameter(description = "Period type") @RequestParam(defaultValue = "month") String period,
            @Parameter(description = "Number of periods") @RequestParam(defaultValue = "12") Integer count) {

        try {
            log.info("🔍 [AdminController] Getting registration trends - Period: {}, Count: {}", period, count);

            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = switch (period.toLowerCase()) {
                case "day" -> endDate.minusDays(count);
                case "week" -> endDate.minusWeeks(count);
                case "month" -> endDate.minusMonths(count);
                case "year" -> endDate.minusYears(count);
                default -> endDate.minusMonths(12);
            };

            // Build filter for registration data only
            AnalyticsFilterDto filter = AnalyticsFilterDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .periodType(period)
                .includeRegistrations(true)
                .includeLogins(false)
                .includeOnlineStatus(false)
                .build();

            UserAnalyticsResponseDto analytics = userAnalyticsService.getUserAnalytics(filter);

            Map<String, Object> trendData = Map.of(
                "period", period,
                "count", count,
                "startDate", startDate,
                "endDate", endDate,
                "dailyData", analytics.getDailyRegistrations() != null ? analytics.getDailyRegistrations() : List.of(),
                "monthlyData", analytics.getMonthlyRegistrations() != null ? analytics.getMonthlyRegistrations() : List.of(),
                "quarterlyData", analytics.getQuarterlyRegistrations() != null ? analytics.getQuarterlyRegistrations() : List.of(),
                "yearlyData", analytics.getYearlyRegistrations() != null ? analytics.getYearlyRegistrations() : List.of(),
                "growthRate", analytics.getMonthlyGrowthRate(),
                "generatedAt", LocalDateTime.now()
            );

            log.info("✅ [AdminController] Retrieved registration trends");
            return ResponseEntity.ok(trendData);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting registration trends: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get user login activity trends for charts (Admin only)
     */
    @GetMapping("/analytics/users/logins")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user login activity trends",
               description = "Admin endpoint to get user login activity trend data for charts")
    public ResponseEntity<Map<String, Object>> getUserLoginTrends(
            @Parameter(description = "Period type") @RequestParam(defaultValue = "month") String period,
            @Parameter(description = "Number of periods") @RequestParam(defaultValue = "12") Integer count) {

        try {
            log.info("🔍 [AdminController] Getting login trends - Period: {}, Count: {}", period, count);

            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = switch (period.toLowerCase()) {
                case "day" -> endDate.minusDays(count);
                case "week" -> endDate.minusWeeks(count);
                case "month" -> endDate.minusMonths(count);
                case "year" -> endDate.minusYears(count);
                default -> endDate.minusMonths(12);
            };

            // Build filter for login data only
            AnalyticsFilterDto filter = AnalyticsFilterDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .periodType(period)
                .includeRegistrations(false)
                .includeLogins(true)
                .includeOnlineStatus(false)
                .build();

            UserAnalyticsResponseDto analytics = userAnalyticsService.getUserAnalytics(filter);

            Map<String, Object> loginData = Map.of(
                "period", period,
                "count", count,
                "startDate", startDate,
                "endDate", endDate,
                "dailyLogins", analytics.getDailyLogins() != null ? analytics.getDailyLogins() : List.of(),
                "monthlyLogins", analytics.getMonthlyLogins() != null ? analytics.getMonthlyLogins() : List.of(),
                "quarterlyLogins", analytics.getQuarterlyLogins() != null ? analytics.getQuarterlyLogins() : List.of(),
                "yearlyLogins", analytics.getYearlyLogins() != null ? analytics.getYearlyLogins() : List.of(),
                "peakLoginTime", analytics.getPeakLoginTime(),
                "generatedAt", LocalDateTime.now()
            );

            log.info("✅ [AdminController] Retrieved login trends");
            return ResponseEntity.ok(loginData);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting login trends: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===== PREMIUM/UPGRADED USER MANAGEMENT =====

    /**
     * Get all premium/upgraded users with filtering (Admin only)
     */
    @GetMapping("/users/premium")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get premium/upgraded users",
               description = "Admin endpoint to get all premium and upgraded users with filtering options")
    public ResponseEntity<Page<AdminUserResponseDto>> getPremiumUsers(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Filter by premium status") @RequestParam(defaultValue = "ALL") String premiumFilter,
            @Parameter(description = "Filter by upgrade status") @RequestParam(defaultValue = "ALL") String upgradeFilter,
            @Parameter(description = "Filter by plan type") @RequestParam(required = false) String planType,
            @Parameter(description = "Show only expired premium") @RequestParam(defaultValue = "false") boolean expiredOnly) {

        try {
            Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            log.info("🔍 [AdminController] Getting premium users - page: {}, premiumFilter: {}, upgradeFilter: {}, planType: {}, expiredOnly: {}",
                    page, premiumFilter, upgradeFilter, planType, expiredOnly);

            Page<AdminUserResponseDto> premiumUsers = userService.getPremiumUsersForAdmin(
                pageable, premiumFilter, upgradeFilter, planType, expiredOnly);

            log.info("✅ [AdminController] Retrieved {} premium users", premiumUsers.getTotalElements());
            return ResponseEntity.ok(premiumUsers);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting premium users: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get premium/upgrade statistics (Admin only)
     */
    @GetMapping("/statistics/premium")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get premium/upgrade statistics",
               description = "Admin endpoint to get premium and upgrade statistics")
    public ResponseEntity<Map<String, Object>> getPremiumStatistics() {
        try {
            log.info("🔍 [AdminController] Getting premium statistics");

            Map<String, Object> stats = userService.getPremiumStatistics();

            log.info("✅ [AdminController] Retrieved premium statistics");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting premium statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update user premium status (Admin only)
     */
    @PatchMapping("/users/{userId}/premium")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user premium status",
               description = "Admin endpoint to grant, extend, or revoke premium status")
    public ResponseEntity<Map<String, Object>> updateUserPremiumStatus(
            @PathVariable Long userId,
            @RequestParam Boolean isPremium,
            @RequestParam(required = false) String planType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime premiumExpiry,
            @RequestParam(required = false) String reason) {

        try {
            log.info("🔄 [AdminController] Updating premium status for user: {} - Premium: {}, Plan: {}, Expiry: {}",
                    userId, isPremium, planType, premiumExpiry);

            Map<String, Object> result = userService.updateUserPremiumStatus(userId, isPremium, planType, premiumExpiry, reason);

            log.info("✅ [AdminController] Successfully updated premium status for user: {}", userId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.warn("❌ [AdminController] User not found: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [AdminController] Error updating premium status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }






    /**
     * Get users with expiring premium (Admin only)
     */
    @GetMapping("/users/premium/expiring")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get users with expiring premium",
               description = "Admin endpoint to get users whose premium is expiring soon")
    public ResponseEntity<Page<AdminUserResponseDto>> getExpiringPremiumUsers(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Days before expiry") @RequestParam(defaultValue = "7") int daysBefore) {

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "premiumExpiry"));

            log.info("🔍 [AdminController] Getting users with premium expiring in {} days", daysBefore);

            Page<AdminUserResponseDto> expiringUsers = userService.getExpiringPremiumUsers(pageable, daysBefore);

            log.info("✅ [AdminController] Retrieved {} users with expiring premium", expiringUsers.getTotalElements());
            return ResponseEntity.ok(expiringUsers);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting expiring premium users: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get premium revenue analytics (Admin only)
     */
    @GetMapping("/analytics/premium/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get premium revenue analytics",
               description = "Admin endpoint to get premium subscription and revenue analytics")
    public ResponseEntity<Map<String, Object>> getPremiumRevenueAnalytics(
            @Parameter(description = "Period type") @RequestParam(defaultValue = "month") String period,
            @Parameter(description = "Number of periods") @RequestParam(defaultValue = "12") Integer count) {

        try {
            log.info("🔍 [AdminController] Getting premium revenue analytics - Period: {}, Count: {}", period, count);

            Map<String, Object> analytics = userService.getPremiumRevenueAnalytics(period, count);

            log.info("✅ [AdminController] Retrieved premium revenue analytics");
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error getting premium revenue analytics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update user system role (admin only)
     */
    @PutMapping("/users/{userId}/system-role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user system role",
               description = "Admin endpoint to update user's system role (ADMIN, MEMBER)")
    public ResponseEntity<Map<String, Object>> updateUserSystemRole(
            @PathVariable Long userId,
            @RequestParam String roleName) {
        try {
            log.info("🔄 [AdminController] Updating system role for user: {} to role: {}", userId, roleName);

            // Find user
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            // Convert roleName to SystemRole enum
            SystemRole newSystemRole;
            try {
                newSystemRole = SystemRole.valueOf(roleName.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid system role: " + roleName + ". Valid roles: ADMIN, MEMBER");
            }

            // Update systemRole field directly
            user.setSystemRole(newSystemRole);
            user.setUpdatedAt(LocalDateTime.now());

            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("newSystemRole", newSystemRole.name());
            response.put("message", "System role updated successfully");
            response.put("updatedAt", LocalDateTime.now());

            log.info("✅ [AdminController] Successfully updated user {} system role to: {}", userId, newSystemRole);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ [AdminController] Error updating user system role: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }


    /**
     * Clear user cache and refresh data (Admin only)
     */
    @PostMapping("/users/{userId}/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Clear user cache and refresh data",
               description = "Admin endpoint to clear cached user data and force refresh from database")
    public ResponseEntity<AdminUserResponseDto> refreshUserData(@PathVariable Long userId) {
        try {
            log.info("🔄 [AdminController] Refreshing user data for ID: {}", userId);

            // Force refresh from database by getting fresh data
            AdminUserResponseDto refreshedUser = userService.getUserDetailsForAdmin(userId);

            log.info("✅ [AdminController] Successfully refreshed user data for: {}", userId);
            return ResponseEntity.ok(refreshedUser);
        } catch (RuntimeException e) {
            log.warn("❌ [AdminController] User not found: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ [AdminController] Error refreshing user data: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
