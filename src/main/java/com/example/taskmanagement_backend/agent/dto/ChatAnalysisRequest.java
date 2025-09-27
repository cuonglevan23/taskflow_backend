package com.example.taskmanagement_backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Request DTO for chat analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAnalysisRequest {

    @NotNull(message = "Conversation ID is required")
    private String conversationId;

    @Min(value = 1, message = "User ID must be positive")
    private Long userId;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Builder.Default
    private Boolean includeSystemMessages = false;

    @Builder.Default
    private Integer maxMessages = 100;
}
