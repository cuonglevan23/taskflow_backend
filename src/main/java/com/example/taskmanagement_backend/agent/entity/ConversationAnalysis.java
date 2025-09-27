package com.example.taskmanagement_backend.agent.entity;

import com.example.taskmanagement_backend.agent.dto.ChatAnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Entity
@Table(name = "conversation_analysis")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, unique = true)
    private String conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "summary", length = 1000)
    private String summary;

    @Column(name = "primary_category", nullable = false)
    private String primaryCategory;

    @Column(name = "secondary_categories", length = 500)
    private String secondaryCategories; // Stored as JSON array string

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "total_messages")
    private Integer totalMessages;

    @Column(name = "analyzed_messages")
    private Integer analyzedMessages;

    @Column(name = "additional_metrics", columnDefinition = "TEXT")
    private String additionalMetricsJson; // Stored as JSON string

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "analysis_method")
    private String analysisMethod; // "GEMINI_API", "LOCAL_FALLBACK", etc.

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    /**
     * Convert entity to DTO
     */
    public ChatAnalysisResponse toDto(ObjectMapper objectMapper) {
        try {
            // Convert secondary categories string to enum list
            List<ChatAnalysisResponse.ChatCategory> secondaryCategoriesList =
                parseSecondaryCategories(objectMapper, secondaryCategories);

            // Convert additionalMetricsJson to Map
            Map<String, Object> additionalMetrics = parseAdditionalMetrics(objectMapper, additionalMetricsJson);

            // Parse primary category
            ChatAnalysisResponse.ChatCategory primaryCat;
            try {
                primaryCat = ChatAnalysisResponse.ChatCategory.valueOf(primaryCategory);
            } catch (Exception e) {
                primaryCat = ChatAnalysisResponse.ChatCategory.SMALLTALK;
            }

            // Build response
            return ChatAnalysisResponse.builder()
                .conversationId(conversationId)
                .userId(userId)
                .summary(summary)
                .primaryCategory(primaryCat)
                .secondaryCategories(secondaryCategoriesList)
                .confidence(confidence)
                .totalMessages(totalMessages)
                .analyzedMessages(analyzedMessages)
                .analysisTimestamp(updatedAt)
                .additionalMetrics(additionalMetrics)
                .build();

        } catch (Exception e) {
            // Fallback in case of parsing errors
            return ChatAnalysisResponse.builder()
                .conversationId(conversationId)
                .userId(userId)
                .summary("Error parsing stored analysis: " + e.getMessage())
                .primaryCategory(ChatAnalysisResponse.ChatCategory.SMALLTALK)
                .confidence(0.5)
                .totalMessages(totalMessages)
                .analyzedMessages(analyzedMessages)
                .analysisTimestamp(updatedAt)
                .build();
        }
    }

    /**
     * Create entity from DTO
     */
    public static ConversationAnalysis fromDto(ChatAnalysisResponse dto, ObjectMapper objectMapper) {
        try {
            // Convert secondary categories to JSON string
            String secondaryCatsJson = objectMapper.writeValueAsString(
                dto.getSecondaryCategories().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList())
            );

            // Convert additional metrics to JSON
            String metricsJson = objectMapper.writeValueAsString(dto.getAdditionalMetrics());

            // Get analysis method
            String method = "UNKNOWN";
            if (dto.getAdditionalMetrics() != null && dto.getAdditionalMetrics().containsKey("analysisMethod")) {
                method = dto.getAdditionalMetrics().get("analysisMethod").toString();
            }

            return ConversationAnalysis.builder()
                .conversationId(dto.getConversationId())
                .userId(dto.getUserId())
                .summary(dto.getSummary())
                .primaryCategory(dto.getPrimaryCategory().name())
                .secondaryCategories(secondaryCatsJson)
                .confidence(dto.getConfidence())
                .totalMessages(dto.getTotalMessages())
                .analyzedMessages(dto.getAnalyzedMessages())
                .additionalMetricsJson(metricsJson)
                .analysisMethod(method)
                .isDeleted(false)
                .build();

        } catch (Exception e) {
            throw new RuntimeException("Error converting analysis response to entity", e);
        }
    }

    /**
     * Update this entity from DTO data
     */
    public void updateFromDto(ChatAnalysisResponse dto, ObjectMapper objectMapper) {
        try {
            // Update fields from the DTO
            this.summary = dto.getSummary();
            this.primaryCategory = dto.getPrimaryCategory().name();
            this.confidence = dto.getConfidence();
            this.totalMessages = dto.getTotalMessages();
            this.analyzedMessages = dto.getAnalyzedMessages();

            // Convert secondary categories to JSON string
            this.secondaryCategories = objectMapper.writeValueAsString(
                dto.getSecondaryCategories().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList())
            );

            // Convert additional metrics to JSON
            this.additionalMetricsJson = objectMapper.writeValueAsString(dto.getAdditionalMetrics());

            // Get analysis method
            if (dto.getAdditionalMetrics() != null && dto.getAdditionalMetrics().containsKey("analysisMethod")) {
                this.analysisMethod = dto.getAdditionalMetrics().get("analysisMethod").toString();
            } else {
                this.analysisMethod = "UNKNOWN";
            }

            // Don't update conversationId and userId as they are identifying fields
            // updatedAt will be automatically updated by @UpdateTimestamp

        } catch (Exception e) {
            throw new RuntimeException("Error updating entity from analysis response", e);
        }
    }

    private List<ChatAnalysisResponse.ChatCategory> parseSecondaryCategories(ObjectMapper mapper, String categoriesJson) {
        if (categoriesJson == null || categoriesJson.isEmpty()) {
            return List.of();
        }

        try {
            List<String> categoryNames = mapper.readValue(categoriesJson, List.class);
            return categoryNames.stream()
                .map(name -> {
                    try {
                        return ChatAnalysisResponse.ChatCategory.valueOf(name);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(cat -> cat != null)
                .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseAdditionalMetrics(ObjectMapper mapper, String metricsJson) {
        if (metricsJson == null || metricsJson.isEmpty()) {
            return new HashMap<>();
        }

        try {
            return mapper.readValue(metricsJson, Map.class);
        } catch (IOException e) {
            return new HashMap<>();
        }
    }
}
