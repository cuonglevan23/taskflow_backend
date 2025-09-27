package com.example.taskmanagement_backend.dtos.ProfileDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileInfoDto {

    private Long userId;
    private String firstName;
    private String lastName;
    private String jobTitle;
    private String department;
    private String aboutMe;
    private String avatarUrl;

    // Các thông tin bổ sung có thể hiển thị trên profile
    private String email;
    private String fullName;
    private boolean isOnline;

    /**
     * Custom setter for avatarUrl to ensure it's always a full URL
     * @param avatarUrl The avatar URL or S3 key
     */
    public void setAvatarUrl(String avatarUrl) {
        if (avatarUrl == null) {
            this.avatarUrl = null;
            return;
        }

        // If it's already a full URL, use it as is
        if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            this.avatarUrl = avatarUrl;
            return;
        }

        // For S3 keys (task-files/ or avatars/), convert to a full URL
        if (avatarUrl.startsWith("task-files/") || avatarUrl.startsWith("avatars/")) {
            // Use a hardcoded S3 URL pattern for simplicity
            // This should match your S3 bucket URL pattern
            this.avatarUrl = "https://taskflowprojectteam123.s3.ap-southeast-2.amazonaws.com/" + avatarUrl;
            return;
        }

        // For any other case, use as is
        this.avatarUrl = avatarUrl;
    }
}
