package com.example.taskmanagement_backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Conversation DTO - RESTful API design
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDto {

    private String conversationId;
    private String title;
    private String status; // ACTIVE, TAKEOVER, CLOSED
    private Long userId;
    private String userEmail;

    // Agent status
    private boolean agentActive;
    private String aiPersonality;
    private String language;

    // Takeover info
    private String supervisorId;
    private String supervisorEmail;
    private LocalDateTime takenOverAt;

    // Metadata
    private int messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivity;

    // Analytics
    private Double satisfactionScore;
    private List<String> tags;
    private String category; // SUPPORT, SALES, GENERAL
}
