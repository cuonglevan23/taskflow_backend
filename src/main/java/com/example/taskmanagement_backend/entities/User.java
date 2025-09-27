package com.example.taskmanagement_backend.entities;

import com.example.taskmanagement_backend.enums.SystemRole;
import com.example.taskmanagement_backend.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;

@Entity
@Table(name = "users") 
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FilterDef(name = "softDeleteFilter", parameters = @ParamDef(name = "deleted", type = Boolean.class))
@Filter(name = "softDeleteFilter", condition = "deleted = :deleted")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SystemRole systemRole = SystemRole.MEMBER; // Default là MEMBER

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(nullable = false) 
    @Builder.Default
    private boolean firstLogin = true;

    // Online status management - NEW
    @Column(nullable = false)
    @Builder.Default
    private boolean online = false; // Đổi từ isOnline thành online

    // Admin-specific fields for user management
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE; // User account status

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt; // Track last login time

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Explicit getter/setter cho online status để tránh lỗi Lombok
    public boolean isOnline() {
        return this.online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    // --- Relationships ---

    @ManyToOne
    @JoinColumn(name = "organization_id", foreignKey = @ForeignKey(name = "fk_user_organization"))
    private Organization organization;

    @OneToMany(mappedBy = "user", orphanRemoval = true)
    @Builder.Default
    private java.util.Set<TaskAssignee> assignees = new java.util.HashSet<>();

    @OneToOne(mappedBy = "user", orphanRemoval = true, fetch = FetchType.LAZY)
    private UserProfile userProfile;

    @OneToOne
    @JoinColumn(name = "default_workspace_id", foreignKey = @ForeignKey(name = "fk_user_default_workspace"))
    private Team defaultWorkspace;

    // Helper method để set 2 chiều
    public void setUserProfile(UserProfile profile) {
        this.userProfile = profile;
        if (profile != null) {
            profile.setUser(this);
        }
    }

    // ==================== SEARCH INDEXING HELPER METHODS ====================

    /**
     * Get first name for search indexing
     */
    public String getFirstName() {
        return userProfile != null ? userProfile.getFirstName() : null;
    }

    /**
     * Get last name for search indexing
     */
    public String getLastName() {
        return userProfile != null ? userProfile.getLastName() : null;
    }

    /**
     * Get username for search indexing
     */
    public String getUsername() {
        return userProfile != null ? userProfile.getUsername() : null;
    }

    /**
     * Get job title for search indexing
     */
    public String getJobTitle() {
        return userProfile != null ? userProfile.getJobTitle() : null;
    }

    /**
     * Get department for search indexing
     */
    public String getDepartment() {
        return userProfile != null ? userProfile.getDepartment() : null;
    }

    /**
     * Get bio for search indexing
     */
    public String getBio() {
        return userProfile != null ? userProfile.getAboutMe() : null;
    }

    /**
     * Get avatar URL for search indexing
     * @return URL đầy đủ của avatar, tự động chuyển đổi S3 key thành URL trực tiếp
     */
    public String getAvatarUrl() {
        if (userProfile == null || userProfile.getAvtUrl() == null) {
            return null;
        }

        String avatarPath = userProfile.getAvtUrl();

        // Nếu đã là URL đầy đủ, trả về nguyên dạng
        if (avatarPath.startsWith("http://") || avatarPath.startsWith("https://")) {
            return avatarPath;
        }

        // Nếu là path trong S3, convert sang URL đầy đủ
        if (avatarPath.startsWith("task-files/")) {
            // Format là: https://bucket-name.s3.region.amazonaws.com/key
            String fullUrl = "https://taskflowprojectteam123.s3.amazonaws.com/" + avatarPath;
            return fullUrl;
        }

        // Nếu là path local, thêm prefix /api/files/avatars/
        return "/api/files/avatars/" + avatarPath;
    }

    /**
     * Get skills for search indexing
     */
    public java.util.List<String> getSkills() {
        // For now, return empty list - implement skills system later if needed
        return java.util.Collections.emptyList();
    }

    /**
     * Get location for search indexing
     */
    public String getLocation() {
        // UserProfile doesn't have location field, return null for now
        return null;
    }

    /**
     * Get company for search indexing
     */
    public String getCompany() {
        // UserProfile doesn't have company field, return null for now
        return null;
    }

    /**
     * Check if user is active for search indexing
     */
    public Boolean getIsActive() {
        return !deleted;
    }

    /**
     * Check if user is online for search indexing
     */
    public Boolean getIsOnline() {
        return this.online; // Trả về trạng thái online thực tế
    }

    /**
     * Check if user is premium for search indexing
     */
    public Boolean getIsPremium() {
        return userProfile != null ? userProfile.getIsPremium() : false;
    }

    /**
     * Get premium plan type for search indexing
     */
    public String getPremiumPlanType() {
        return userProfile != null ? userProfile.getPremiumPlanType() : null;
    }

    /**
     * Get profile visibility for search indexing
     */
    public String getProfileVisibility() {
        // UserProfile doesn't have profileVisibility field, return default
        return "PUBLIC";
    }

    /**
     * Check if user is searchable for search indexing
     */
    public Boolean getSearchable() {
        // UserProfile doesn't have searchable field, return true by default
        return true;
    }

    /**
     * Get friend IDs for search indexing
     */
    public java.util.List<Long> getFriendIds() {
        // This would need to be implemented with friend relationships
        return java.util.Collections.emptyList();
    }

    /**
     * Get team IDs for search indexing
     */
    public java.util.List<Long> getTeamIds() {
        // This would need to be implemented with team member relationships
        return java.util.Collections.emptyList();
    }

    /**
     * Get team names for search indexing
     */
    public java.util.List<String> getTeamNames() {
        // This would need to be implemented with team member relationships
        return java.util.Collections.emptyList();
    }

    /**
     * Get connections count for search indexing
     */
    public Integer getConnectionsCount() {
        // This would need to be calculated from friend relationships
        return 0;
    }

    /**
     * Get completed tasks count for search indexing
     */
    public Integer getCompletedTasksCount() {
        // This would need to be calculated from task assignments
        return 0;
    }

    // Computed fields for timestamps
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
