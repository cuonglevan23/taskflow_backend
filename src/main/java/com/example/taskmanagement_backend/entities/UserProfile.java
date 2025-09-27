package com.example.taskmanagement_backend.entities;

import com.example.taskmanagement_backend.enums.Language;
import com.example.taskmanagement_backend.enums.Theme;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;

    private String lastName;



    // Premium features
    @Column(name = "is_premium", columnDefinition = "bit default false")
    @Builder.Default
    private Boolean isPremium = false;

    @Column(name = "premium_expiry")
    private LocalDateTime premiumExpiry;

    @Column(name = "premium_plan_type")
    private String premiumPlanType; // "monthly", "yearly", "lifetime"

    private String username;

    private String jobTitle;

    private String department;

    @Column(columnDefinition = "TEXT")
    private String aboutMe;

    private String status;

    private String avtUrl;

    // Cover image for LinkedIn/Facebook style profile
    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "cover_image_s3_key")
    private String coverImageS3Key;

    // Social links (premium feature)
    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "website_url")
    private String websiteUrl;



    @Column(name = "show_email", columnDefinition = "bit default true")
    @Builder.Default
    private Boolean showEmail = true;

    @Column(name = "show_department", columnDefinition = "bit default true")
    @Builder.Default
    private Boolean showDepartment = true;

    // ✅ NEW: Account Settings
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language")
    @Builder.Default
    private Language preferredLanguage = Language.EN;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_theme")
    @Builder.Default
    private Theme preferredTheme = Theme.DARK;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
}
