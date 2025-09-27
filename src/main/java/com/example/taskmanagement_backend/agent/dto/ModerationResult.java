package com.example.taskmanagement_backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Moderation Result DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationResult {

    private boolean safe;
    private String reason;
    private String category; // TOXIC, SPAM, INAPPROPRIATE, SAFE
    private double confidence;
    private String[] flaggedTerms;
    private String recommendation; // ALLOW, BLOCK, REVIEW
}
