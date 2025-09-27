package com.example.taskmanagement_backend.enums;

import lombok.Getter;

@Getter
public enum FriendshipStatus {
    PENDING("Pending confirmation"),
    ACCEPTED("Friends"),
    BLOCKED("Blocked"),
    REJECTED("Rejected"),

    // Thêm các trạng thái mới cho UX tốt hơn
    NOT_FRIENDS("Not friends"),           // Chưa kết bạn
    PENDING_SENT("Request sent"),         // Đã gửi lời mời (cho người gửi)
    PENDING_RECEIVED("Request received"); // Nhận được lời mời (cho người nhận)

    private final String description;

    FriendshipStatus(String description) {
        this.description = description;
    }
}
