package com.example.taskmanagement_backend.dtos.FriendDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendDto {
    private Long id;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    private String avatarUrl;
    private String department;
    private String jobTitle;
    private LocalDateTime friendsSince; // Ngày kết bạn
    private boolean isOnline; // Trạng thái online (có thể mở rộng sau)
}
