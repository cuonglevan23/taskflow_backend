package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.SystemRole;
import com.example.taskmanagement_backend.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserJpaRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

   // ✅ UPDATED: Remove JOIN FETCH u.roles since we use systemRole now
   @Query("SELECT u FROM User u WHERE u.email = :email")
   Optional<User> findByEmail(@Param("email") String email);

   boolean existsByEmail(String email);

   /**
    * ✅ NEW: Find users by system role and not deleted
    */
   List<User> findBySystemRoleAndDeletedFalse(SystemRole systemRole);

   /**
    * ✅ NEW: Count users by system role and not deleted
    */
   long countBySystemRoleAndDeletedFalse(SystemRole systemRole);

   /**
    * ✅ UPDATED: Find first user by system role, ordered by ID ascending
    */
   @Query("SELECT u FROM User u WHERE u.systemRole = :systemRole ORDER BY u.id ASC")
   Optional<User> findFirstBySystemRoleOrderByIdAsc(@Param("systemRole") SystemRole systemRole);

   /**
    * Find first user ordered by ID ascending (fallback for default owner)
    */
   Optional<User> findFirstByOrderByIdAsc();

   /**
    * Find user by username in their profile
    */
   @Query("SELECT u FROM User u JOIN u.userProfile p WHERE p.username = :username")
   Optional<User> findByUserProfile_Username(@Param("username") String username);

   // ===== SUBSCRIPTION MANAGEMENT QUERIES =====

   /**
    * Find users with expired subscriptions
    */
   @Query("SELECT u FROM User u JOIN u.userProfile p WHERE p.isPremium = true AND p.premiumExpiry < :now")
   List<User> findUsersWithExpiredSubscriptions(@Param("now") LocalDateTime now);

   /**
    * Count active subscriptions (premium users with future expiry)
    */
   @Query("SELECT COUNT(u) FROM User u JOIN u.userProfile p WHERE p.isPremium = true AND p.premiumExpiry > :now")
   long countActiveSubscriptions(@Param("now") LocalDateTime now);

   /**
    * Count trial subscriptions
    */
   @Query("SELECT COUNT(u) FROM User u JOIN u.userProfile p WHERE p.isPremium = true AND p.premiumPlanType = 'trial' AND p.premiumExpiry > :now")
   long countTrialSubscriptions(@Param("now") LocalDateTime now);

   /**
    * Count subscriptions expiring soon
    */
   @Query("SELECT COUNT(u) FROM User u JOIN u.userProfile p WHERE p.isPremium = true AND p.premiumExpiry > :now AND p.premiumExpiry < :soonDate")
   long countExpiringSoon(@Param("now") LocalDateTime now, @Param("soonDate") LocalDateTime soonDate);

   /**
    * Count expired subscriptions
    */
   @Query("SELECT COUNT(u) FROM User u JOIN u.userProfile p WHERE p.isPremium = false OR p.premiumExpiry < :now")
   long countExpiredSubscriptions(@Param("now") LocalDateTime now);

   /**
    * Find users by plan type
    */
   @Query("SELECT u FROM User u JOIN u.userProfile p WHERE p.premiumPlanType = :planType AND p.isPremium = true AND p.premiumExpiry > :now")
   List<User> findUsersByPlanType(@Param("planType") String planType, @Param("now") LocalDateTime now);

   /**
    * Find premium users for analytics
    */
   @Query("SELECT u FROM User u JOIN u.userProfile p WHERE p.isPremium = true ORDER BY p.premiumExpiry DESC")
   List<User> findAllPremiumUsers();

   // ===== TRIAL MANAGEMENT QUERIES =====

   /**
    * Find users with active trial subscriptions
    */
   @Query("SELECT u FROM User u JOIN u.userProfile p WHERE p.isPremium = true AND p.premiumPlanType = 'trial' AND p.premiumExpiry > :now")
   List<User> findTrialUsers(@Param("now") LocalDateTime now);

   /**
    * Find trial users expiring soon (within specified period)
    */
   @Query("SELECT u FROM User u JOIN u.userProfile p WHERE p.isPremium = true AND p.premiumPlanType = 'trial' AND p.premiumExpiry > :now AND p.premiumExpiry <= :endPeriod")
   List<User> findTrialExpiringSoon(@Param("now") LocalDateTime now, @Param("endPeriod") LocalDateTime endPeriod);

   /**
    * Find users with expired trials (still marked as premium but past expiry date)
    */
   @Query("SELECT u FROM User u JOIN u.userProfile p WHERE p.isPremium = true AND p.premiumPlanType = 'trial' AND p.premiumExpiry < :now")
   List<User> findExpiredTrialUsers(@Param("now") LocalDateTime now);

   /**
    * Count trial users by status
    */
   @Query("SELECT COUNT(u) FROM User u JOIN u.userProfile p WHERE p.isPremium = true AND p.premiumPlanType = 'trial' AND p.premiumExpiry > :now")
   long countActiveTrialUsers(@Param("now") LocalDateTime now);

   /**
    * Find users who never had trial (for new user initialization)
    */
   @Query("SELECT u FROM User u LEFT JOIN u.userProfile p WHERE p.premiumExpiry IS NULL OR (p.premiumPlanType != 'trial' AND p.isPremium = false)")
   List<User> findUsersNeverHadTrial();

   /**
    * Find trial users expiring today
    */
   @Query("SELECT u FROM User u JOIN u.userProfile p WHERE p.isPremium = true AND p.premiumPlanType = 'trial' AND DATE(p.premiumExpiry) = DATE(:today)")
   List<User> findTrialUsersExpiringToday(@Param("today") LocalDateTime today);

   /**
    * Find trial users by remaining days
    */
   @Query("SELECT u FROM User u JOIN u.userProfile p WHERE p.isPremium = true AND p.premiumPlanType = 'trial' AND p.premiumExpiry BETWEEN :startRange AND :endRange")
   List<User> findTrialUsersByRemainingDays(@Param("startRange") LocalDateTime startRange, @Param("endRange") LocalDateTime endRange);

   /**
    * Find all users who are currently online
    */
   List<User> findByOnlineTrue();

   /**
    * 🔍 NEW: Search users for User Lookup APIs
    * Search by firstName, lastName, email, or username with limit
    * Fixed: Use explicit column selection to avoid duplicate 'id' alias conflict
    */
   @Query(nativeQuery = true, value = "SELECT u.id, u.email, u.password, u.system_role, u.deleted, u.first_login, " +
          "u.online, u.status, u.last_login_at, u.last_seen, u.created_at, u.updated_at, u.organization_id, u.default_workspace_id " +
          "FROM users u " +
          "LEFT JOIN user_profiles p ON u.id = p.user_id " +
          "WHERE (LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
          "LOWER(p.first_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
          "LOWER(p.last_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
          "LOWER(p.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
          "AND u.deleted = false " +
          "ORDER BY p.first_name ASC, p.last_name ASC " +
          "LIMIT :limit")
   List<User> findUsersForSearch(@Param("searchTerm") String searchTerm, @Param("limit") int limit);

   // ===== ADMIN-SPECIFIC QUERY METHODS =====

   /**
    * Count users by deleted status and user status
    */
   long countByDeletedFalseAndStatus(UserStatus status);

   /**
    * Count users by deleted status
    */
   long countByDeletedTrue();

   /**
    * Count users created after a specific date
    */
   long countByCreatedAtAfter(LocalDateTime date);

   /**
    * Find users created after a specific date
    */
   List<User> findByCreatedAtAfter(LocalDateTime date);

   /**
    * Find users by status (for admin filtering)
    */
   List<User> findByStatus(UserStatus status);

   /**
    * ✅ NEW: Find users by system role for admin management
    */
   List<User> findBySystemRole(SystemRole systemRole);

   /**
    * ✅ NEW: Find users by system role and status
    */
   List<User> findBySystemRoleAndStatus(SystemRole systemRole, UserStatus status);

   /**
    * ✅ NEW: Count total users by system role
    */
   long countBySystemRole(SystemRole systemRole);

   /**
    * ✅ NEW: Find users with multiple system roles (for admin queries)
    */
   @Query("SELECT u FROM User u WHERE u.systemRole IN :systemRoles AND u.deleted = false")
   List<User> findBySystemRoleInAndDeletedFalse(@Param("systemRoles") List<SystemRole> systemRoles);

   /**
    * ✅ NEW: Get system role distribution statistics
    */
   @Query("SELECT u.systemRole, COUNT(u) FROM User u WHERE u.deleted = false GROUP BY u.systemRole")
   List<Object[]> getSystemRoleDistribution();

   /**
    * ✅ NEW: Count users by online status
    */
   long countByOnlineTrue();

   /**
    * ✅ NEW: Count users created between two dates
    */
   long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

   /**
    * ✅ NEW: Count users by last login between two dates
    */
   long countByLastLoginAtBetween(LocalDateTime startDate, LocalDateTime endDate);

   /**
    * ✅ NEW: Find users who logged in after a specific date
    */
   List<User> findByLastLoginAtAfter(LocalDateTime date);
}