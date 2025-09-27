package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ProfileDto.UpdateUserProfileInfoRequestDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.UserProfileInfoDto;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.repositories.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileInfoService {

    private final UserJpaRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final S3Service s3Service;
    private static final String AVATAR_PREFIX = "avatars/";

    /**
     * Lấy thông tin profile của người dùng
     */
    public UserProfileInfoDto getUserProfileInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        UserProfile profile = user.getUserProfile();
        if (profile == null) {
            throw new RuntimeException("User profile not found for user: " + userId);
        }

        // Sử dụng URL trực tiếp cho avatar thay vì URL presigned
        String avatarUrl = s3Service.getDirectAvatarUrl(profile.getAvtUrl());

        return UserProfileInfoDto.builder()
                .userId(userId)
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .fullName(formatFullName(profile.getFirstName(), profile.getLastName()))
                .jobTitle(profile.getJobTitle())
                .department(profile.getDepartment())
                .aboutMe(profile.getAboutMe())
                .avatarUrl(avatarUrl)
                .email(user.getEmail())
                .isOnline(user.isOnline())
                .build();
    }

    /**
     * Cập nhật thông tin cá nhân của người dùng
     */
    @Transactional
    public UserProfileInfoDto updateUserProfileInfo(Long userId, UpdateUserProfileInfoRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        UserProfile profile = user.getUserProfile();
        if (profile == null) {
            profile = new UserProfile();
            profile.setUser(user);
            user.setUserProfile(profile);
        }

        // Cập nhật thông tin nếu có
        if (request.getFirstName() != null) {
            profile.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            profile.setLastName(request.getLastName());
        }
        if (request.getJobTitle() != null) {
            profile.setJobTitle(request.getJobTitle());
        }
        if (request.getDepartment() != null) {
            profile.setDepartment(request.getDepartment());
        }
        if (request.getAboutMe() != null) {
            profile.setAboutMe(request.getAboutMe());
        }

        UserProfile savedProfile = userProfileRepository.save(profile);

        // Sử dụng URL trực tiếp cho avatar thay vì URL presigned
        String avatarUrl = s3Service.getDirectAvatarUrl(savedProfile.getAvtUrl());

        return UserProfileInfoDto.builder()
                .userId(userId)
                .firstName(savedProfile.getFirstName())
                .lastName(savedProfile.getLastName())
                .fullName(formatFullName(savedProfile.getFirstName(), savedProfile.getLastName()))
                .jobTitle(savedProfile.getJobTitle())
                .department(savedProfile.getDepartment())
                .aboutMe(savedProfile.getAboutMe())
                .avatarUrl(avatarUrl)
                .email(user.getEmail())
                .isOnline(user.isOnline())
                .build();
    }

    /**
     * Cập nhật avatar của người dùng sử dụng S3
     */
    @Transactional
    public UserProfileInfoDto updateUserAvatar(Long userId, MultipartFile avatarFile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        UserProfile profile = user.getUserProfile();
        if (profile == null) {
            profile = new UserProfile();
            profile.setUser(user);
            user.setUserProfile(profile);
        }

        try {
            // Xử lý và lưu file avatar lên S3
            String filename = "avatar_" + userId + "_" + UUID.randomUUID().toString();
            String extension = getFileExtension(avatarFile.getOriginalFilename());
            if (extension != null) {
                filename += extension;
            }

            String s3Key = AVATAR_PREFIX + filename;

            // Upload file to S3
            String uploadedKey = s3Service.uploadFile(
                filename,
                avatarFile.getInputStream(),
                avatarFile.getSize(),
                avatarFile.getContentType()
            );

            // Lưu S3 key vào profile thay vì URL đầy đủ
            // Điều này giúp tránh lỗi truncation và cho phép tạo URL khi cần
            profile.setAvtUrl(uploadedKey);

            // Lưu S3 key để có thể quản lý file sau này (xóa, cập nhật)
            profile.setCoverImageS3Key(uploadedKey);

            UserProfile savedProfile = userProfileRepository.save(profile);

            log.info("✅ Avatar updated for user {}: {}", userId, uploadedKey);

            // Sử dụng URL trực tiếp không hết hạn thay vì presigned URL
            String directUrl = s3Service.getDirectAvatarUrl(uploadedKey);

            return UserProfileInfoDto.builder()
                    .userId(userId)
                    .firstName(savedProfile.getFirstName())
                    .lastName(savedProfile.getLastName())
                    .fullName(formatFullName(savedProfile.getFirstName(), savedProfile.getLastName()))
                    .jobTitle(savedProfile.getJobTitle())
                    .department(savedProfile.getDepartment())
                    .aboutMe(savedProfile.getAboutMe())
                    .avatarUrl(directUrl) // Trả về URL trực tiếp không hết hạn
                    .email(user.getEmail())
                    .isOnline(user.isOnline())
                    .build();
        } catch (IOException e) {
            log.error("❌ Error saving avatar for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Error saving avatar: " + e.getMessage(), e);
        }
    }

    /**
     * Format họ tên đầy đủ
     */
    private String formatFullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) {
            return "";
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    /**
     * Lấy phần mở rộng của file
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return null;
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
