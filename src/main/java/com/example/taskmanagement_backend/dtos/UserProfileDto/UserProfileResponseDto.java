package com.example.taskmanagement_backend.dtos.UserProfileDto;


import com.example.taskmanagement_backend.enums.Language;
import com.example.taskmanagement_backend.enums.Theme;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileResponseDto {

    @NotBlank
    private Long id;

    private String firstName;

    private String lastName;

    private String username;

    private String jobTitle;

    private String department;

    private String aboutMe;

    private String status;

    private String avtUrl;

    // ✅ ADD: Premium fields for icon display
    private Boolean isPremium;

    private LocalDateTime premiumExpiry;

    private String premiumPlanType; // "monthly", "quarterly", "yearly"

    // ✅ ADD: Additional profile fields
    private String coverImageUrl;

    private String linkedinUrl;

    private String githubUrl;

    private String websiteUrl;

    private String themeColor;

    private Boolean showEmail;

    private Boolean showDepartment;

    private Language preferredLanguage;

    private Theme preferredTheme;

    private Long userId;

}
