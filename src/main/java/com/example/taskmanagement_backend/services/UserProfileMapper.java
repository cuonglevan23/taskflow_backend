package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileMapper {

    private final OnlineStatusService onlineStatusService;

    /**
     * Convert User entity to UserProfileDto
     */
    public UserProfileDto toUserProfileDto(User user) {
        if (user == null) {
            return null;
        }

        UserProfile profile = user.getUserProfile();

        // Tính toán displayName và initials
        String displayName = getDisplayName(profile, user);
        String initials = getInitials(profile, user);

        // Lấy thông tin trạng thái online
        boolean isOnline = onlineStatusService.isUserOnline(user.getId());
        String onlineStatus = onlineStatusService.getOnlineStatus(user.getId());

        return UserProfileDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(profile != null ? profile.getFirstName() : null)
                .lastName(profile != null ? profile.getLastName() : null)
                .username(profile != null ? profile.getUsername() : user.getEmail())
                .jobTitle(profile != null ? profile.getJobTitle() : null)
                .department(profile != null ? profile.getDepartment() : null)
                .aboutMe(profile != null ? profile.getAboutMe() : null)
                .status(profile != null ? profile.getStatus() : "Active")
                .avatarUrl(user.getAvatarUrl()) // Sử dụng user.getAvatarUrl() thay vì profile.getAvtUrl()

                // Online status fields - NEW
                .onlineStatus(onlineStatus)
                .lastSeen(user.getLastSeen())
                .isOnline(isOnline)
                .displayName(displayName)
                .initials(initials)
                .build();
    }

    /**
     * Convert list of Users to list of UserProfileDto
     */
    public List<UserProfileDto> toUserProfileDtoList(List<User> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }

        return users.stream()
                .map(this::toUserProfileDto)
                .collect(Collectors.toList());
    }

    /**
     * Tính toán display name để hiển thị trên UI
     */
    private String getDisplayName(UserProfile profile, User user) {
        if (profile != null && profile.getFirstName() != null && profile.getLastName() != null) {
            return profile.getFirstName() + " " + profile.getLastName();
        }

        if (profile != null && profile.getUsername() != null && !profile.getUsername().trim().isEmpty()) {
            return profile.getUsername();
        }

        // Fallback to email without @domain
        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }

        return email != null ? email : "Unknown User";
    }

    /**
     * Tính toán initials để hiển thị trong avatar khi không có ảnh
     */
    private String getInitials(UserProfile profile, User user) {
        if (profile != null && profile.getFirstName() != null && profile.getLastName() != null) {
            String firstInitial = profile.getFirstName().substring(0, 1).toUpperCase();
            String lastInitial = profile.getLastName().substring(0, 1).toUpperCase();
            return firstInitial + lastInitial;
        }

        if (profile != null && profile.getUsername() != null && !profile.getUsername().trim().isEmpty()) {
            return profile.getUsername().substring(0, Math.min(2, profile.getUsername().length())).toUpperCase();
        }

        // Fallback to email initials
        String email = user.getEmail();
        if (email != null && !email.isEmpty()) {
            return email.substring(0, Math.min(2, email.length())).toUpperCase();
        }

        return "??";
    }
}
