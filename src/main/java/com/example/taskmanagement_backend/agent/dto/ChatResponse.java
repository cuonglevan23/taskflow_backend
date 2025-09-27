package com.example.taskmanagement_backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chat Response DTO - Chuẩn RESTful API design
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String messageId;
    private String content;
    private String senderType; // USER, AGENT, SUPERVISOR
    private LocalDateTime timestamp;

    // AI-specific metadata
    private String aiModel;
    private Double confidence;
    private String intent;
    private List<String> tags; // Changed from String[] to List<String>
    private String context;

    // Response status
    private boolean success;
    private String status; // PROCESSED, MODERATED, ERROR
    private String errorMessage;

    // Conversation metadata
    private String conversationId;
    private boolean agentActive;
    private String supervisorId;

    // Post-processing metadata
    private String qualityAssessment; // HIGH, MEDIUM, LOW
    private boolean adminEscalated; // Flag để biết có cần admin can thiệp không
    private String escalationReason; // Lý do escalate

    // Tool calling metadata
    private boolean toolCalled; // Flag để biết có sử dụng tool calling không
    private String toolsUsed; // Danh sách tools được sử dụng
    private String toolResults; // Kết quả từ tool execution
}
