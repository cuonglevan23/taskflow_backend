package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.document.EmbeddingDocument;
import com.example.taskmanagement_backend.agent.retriever.PineconeRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced Retriever Service - Advanced RAG flow với context enhancement
 * Nhiệm vụ: Truy vấn dữ liệu thông minh từ Pinecone để hỗ trợ Gemini
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrieverService {

    private final PineconeRetriever pineconeRetriever;

    /**
     * Retrieve context với intelligent query enhancement
     */
    public String retrieveContext(String userQuery, String conversationContext) {
        try {
            log.debug("Retrieving context for query: {}", userQuery.substring(0, Math.min(50, userQuery.length())));

            // Enhanced query với context
            String enhancedQuery = enhanceQuery(userQuery, conversationContext);

            // Query Pinecone với enhanced query
            List<EmbeddingDocument> documents = pineconeRetriever.queryVector(enhancedQuery, "default", 5);

            if (documents.isEmpty()) {
                log.debug("No relevant documents found for query");
                return buildEmptyContextMessage(userQuery);
            }

            // Build context từ documents
            String context = buildEnhancedContext(documents, userQuery);
            log.debug("Built context with {} documents", documents.size());

            return context;

        } catch (Exception e) {
            log.error("Error retrieving context for query: {}", userQuery, e);
            return buildErrorContext(e.getMessage());
        }
    }

    /**
     * Retrieve context specifically for task-related queries
     */
    public String retrieveTaskContext(String userQuery, Long userId) {
        try {
            // Add task-specific keywords
            String taskQuery = enhanceTaskQuery(userQuery, userId);

            List<EmbeddingDocument> documents = pineconeRetriever.queryVector(taskQuery, "tasks", 3);

            if (documents.isEmpty()) {
                return "Không tìm thấy thông tin liên quan về task management.";
            }

            return buildTaskContext(documents);

        } catch (Exception e) {
            log.error("Error retrieving task context", e);
            return "Lỗi khi truy xuất thông tin về task.";
        }
    }

    /**
     * Retrieve context for project-related queries
     */
    public String retrieveProjectContext(String userQuery, Long projectId) {
        try {
            String projectQuery = enhanceProjectQuery(userQuery, projectId);

            List<EmbeddingDocument> documents = pineconeRetriever.queryVector(projectQuery, "projects", 3);

            return documents.isEmpty() ?
                "Không tìm thấy thông tin liên quan về dự án." :
                buildProjectContext(documents);

        } catch (Exception e) {
            log.error("Error retrieving project context", e);
            return "Lỗi khi truy xuất thông tin về dự án.";
        }
    }

    /**
     * Enhanced query với conversation context
     */
    private String enhanceQuery(String userQuery, String conversationContext) {
        StringBuilder enhanced = new StringBuilder(userQuery);

        // Add context keywords if available
        if (conversationContext != null && !conversationContext.trim().isEmpty()) {
            // Extract key terms from conversation context
            if (conversationContext.toLowerCase().contains("task")) {
                enhanced.append(" task management");
            }
            if (conversationContext.toLowerCase().contains("project")) {
                enhanced.append(" project management");
            }
            if (conversationContext.toLowerCase().contains("deadline")) {
                enhanced.append(" deadline scheduling");
            }
        }

        return enhanced.toString();
    }

    /**
     * Enhance query for task-specific searches
     */
    private String enhanceTaskQuery(String userQuery, Long userId) {
        return userQuery + " task management workflow user:" + userId;
    }

    /**
     * Enhance query for project-specific searches
     */
    private String enhanceProjectQuery(String userQuery, Long projectId) {
        return userQuery + " project management collaboration project:" + projectId;
    }

    /**
     * Build enhanced context từ documents
     */
    private String buildEnhancedContext(List<EmbeddingDocument> documents, String userQuery) {
        StringBuilder context = new StringBuilder();

        context.append("=== THÔNG TIN LIÊN QUAN ===\n");

        // Group documents by relevance score
        List<EmbeddingDocument> highRelevance = documents.stream()
            .filter(doc -> doc.getScore() > 0.8)
            .collect(Collectors.toList());

        List<EmbeddingDocument> mediumRelevance = documents.stream()
            .filter(doc -> doc.getScore() <= 0.8 && doc.getScore() > 0.6)
            .collect(Collectors.toList());

        // High relevance documents
        if (!highRelevance.isEmpty()) {
            context.append("\n📋 THÔNG TIN CHÍNH:\n");
            for (EmbeddingDocument doc : highRelevance) {
                context.append("• ").append(doc.getContent()).append("\n");
            }
        }

        // Medium relevance documents
        if (!mediumRelevance.isEmpty()) {
            context.append("\n📝 THÔNG TIN LIÊN QUAN:\n");
            for (EmbeddingDocument doc : mediumRelevance) {
                context.append("• ").append(doc.getContent()).append("\n");
            }
        }

        // Add metadata if available
        if (!documents.isEmpty() && documents.get(0).getMetadata() != null) {
            context.append("\n🔍 Nguồn: ").append(documents.get(0).getMetadata().toString()).append("\n");
        }

        return context.toString();
    }

    /**
     * Build task-specific context
     */
    private String buildTaskContext(List<EmbeddingDocument> documents) {
        StringBuilder context = new StringBuilder("=== THÔNG TIN VỀ TASK MANAGEMENT ===\n");

        for (EmbeddingDocument doc : documents) {
            context.append("📌 ").append(doc.getContent()).append("\n");
        }

        return context.toString();
    }

    /**
     * Build project-specific context
     */
    private String buildProjectContext(List<EmbeddingDocument> documents) {
        StringBuilder context = new StringBuilder("=== THÔNG TIN VỀ DỰ ÁN ===\n");

        for (EmbeddingDocument doc : documents) {
            context.append("🏗️ ").append(doc.getContent()).append("\n");
        }

        return context.toString();
    }

    /**
     * Build message when no context found
     */
    private String buildEmptyContextMessage(String userQuery) {
        return "=== THÔNG TIN LIÊN QUAN ===\n" +
               "Không tìm thấy thông tin cụ thể trong cơ sở dữ liệu cho câu hỏi này.\n" +
               "Tôi sẽ trả lời dựa trên kiến thức chung về quản lý công việc và dự án.";
    }

    /**
     * Build error context message
     */
    private String buildErrorContext(String errorMessage) {
        return "=== LỖI TRUY XUẤT DỮ LIỆU ===\n" +
               "Có lỗi khi truy xuất thông tin từ cơ sở dữ liệu: " + errorMessage + "\n" +
               "Tôi sẽ trả lời dựa trên kiến thức có sẵn.";
    }

    /**
     * Get retrieval statistics
     */
    public String getRetrievalStats() {
        return "RetrieverService đang hoạt động bình thường với Pinecone integration.";
    }
}
