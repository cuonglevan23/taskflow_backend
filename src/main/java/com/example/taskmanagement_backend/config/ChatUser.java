package com.example.taskmanagement_backend.config;

import java.security.Principal;

/**
 * Custom Principal implementation for WebSocket chat users
 */
public class ChatUser implements Principal {
    private final Long userId;
    private final String email;

    public ChatUser(Long userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    @Override
    public String getName() {
        return email;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        return "ChatUser{" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                '}';
    }
}
