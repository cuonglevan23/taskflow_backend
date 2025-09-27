package com.example.taskmanagement_backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Chat Request DTO - Chuẩn RESTful API design
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Message content cannot be empty")
    @Size(max = 5000, message = "Message too long")
    private String content;

    private String sessionId;
    private String conversationId; // NEW: Add conversationId field to support frontend requests
    private String context;
    private String language;

    // Optional metadata
    private String userAgent;
    private String clientIp;
}
