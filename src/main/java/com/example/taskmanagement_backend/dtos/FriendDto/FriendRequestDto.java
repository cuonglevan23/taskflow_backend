package com.example.taskmanagement_backend.dtos.FriendDto;

import com.example.taskmanagement_backend.enums.FriendshipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestDto {
    private Long id;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    private String avatarUrl;
    private String department;
    private String jobTitle;
    private FriendshipStatus status;
    private boolean isSender;
    private LocalDateTime createdAt;
}
