package com.example.taskmanagement_backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for chat analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAnalysisResponse {

    private String conversationId;
    private Long userId;
    private String summary;
    private ChatCategory primaryCategory;
    private List<ChatCategory> secondaryCategories;
    private Double confidence;
    private Integer totalMessages;
    private Integer analyzedMessages;
    private LocalDateTime analysisTimestamp;
    private Map<String, Object> additionalMetrics;

    /**
     * Chat categories based on your requirements
     */
    public enum ChatCategory {
        POTENTIAL_CUSTOMER("khách hàng tiềm năng"),
        COMPLAINT("khiếu nại"),
        SUPPORT_REQUEST("yêu cầu hỗ trợ"),
        SMALLTALK("chuyện phiếm"),
        TASK_COMMAND("tạo / cập nhật / xóa task"),
        MISSING_INFO("thiếu dữ liệu cần hỏi thêm"),
        SPAM("spam");

        private final String vietnameseDescription;

        ChatCategory(String vietnameseDescription) {
            this.vietnameseDescription = vietnameseDescription;
        }

        public String getVietnameseDescription() {
            return vietnameseDescription;
        }
    }
}
