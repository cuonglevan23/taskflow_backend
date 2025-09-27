package com.example.taskmanagement_backend.agent.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enhanced Embedding Document - RAG với AI Agent Integration
 * Hỗ trợ text/markdown files với metadata phong phú cho AI Agent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmbeddingDocument {

    // === BASIC DOCUMENT INFO ===
    private String id;
    private String content;
    private String originalContent; // Raw content trước khi process
    private String title;
    private String summary; // AI-generated summary

    // === CATEGORIZATION ===
    private String category;
    private String sourceId;
    private String sourceType; // KNOWLEDGE_BASE, FAQ, DOCUMENTATION, PROJECT_DOC, TASK_NOTE, MARKDOWN_FILE, TEXT_FILE
    private String subCategory; // More specific classification
    private List<String> tags; // AI-generated tags

    // === FILE METADATA ===
    private String fileName;
    private String fileExtension; // .md, .txt, .pdf, etc.
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private String encoding; // UTF-8, etc.

    // === CONTENT ANALYSIS ===
    private double[] embedding; // Vector representation
    private String language; // vi, en, auto-detected
    private Integer wordCount;
    private Integer characterCount;
    private Double readabilityScore; // AI-assessed readability
    private String contentType; // TECHNICAL, BUSINESS, GUIDE, API_DOC, etc.

    // === AI AGENT ENHANCEMENTS ===
    private String aiExtractedKeywords; // AI-extracted important keywords
    private String aiContentSummary; // AI-generated content summary
    private String aiRecommendedActions; // AI suggestions based on content
    private List<String> aiTopics; // AI-identified topics
    private Map<String, Object> aiInsights; // AI analysis results
    private Double aiRelevanceScore; // AI-assessed relevance for queries

    // === SEARCH & RETRIEVAL ===
    private double relevanceScore; // For search results
    private Double semanticSimilarity; // Similarity to user query
    private Integer chunkIndex; // If document is chunked
    private Integer totalChunks;
    private String parentDocumentId; // If this is a chunk

    // === PROJECT CONTEXT ===
    private Long projectId;
    private Long userId; // Creator
    private Long organizationId;
    private String accessLevel; // PUBLIC, PRIVATE, TEAM, PROJECT
    private List<Long> authorizedUsers; // Who can access this document
    private List<Long> authorizedTeams;

    // === TEMPORAL DATA ===
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastAccessed;
    private LocalDateTime lastIndexed; // Last time embeddings were updated
    private LocalDateTime expiryDate; // Optional expiry for temporary docs

    // === USAGE STATISTICS ===
    private Integer accessCount;
    private Integer queryMatchCount; // How many times this doc matched queries
    private Double averageRating; // User feedback on document usefulness
    private LocalDateTime lastUsedInChat; // Last time used in AI chat

    // === DOCUMENT RELATIONSHIPS ===
    private List<String> relatedDocumentIds; // AI-identified related documents
    private List<String> referencedDocuments; // Documents this one references
    private List<String> referencingDocuments; // Documents that reference this one
    private String documentVersion; // Version tracking
    private String previousVersionId;

    // === PROCESSING STATUS ===
    private String processingStatus; // PENDING, PROCESSING, COMPLETED, FAILED
    private String processingError; // Error message if processing failed
    private LocalDateTime processingStarted; // When processing started
    private LocalDateTime processingCompleted; // When processing completed
    private Integer processingAttempts; // Number of processing attempts

    // === SEARCH METHODS ===
    /**
     * Get search relevance score for this document
     */
    public double getScore() {
        return this.relevanceScore;
    }

    /**
     * Get document metadata as a Map
     */
    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("sourceType", this.sourceType);
        metadata.put("category", this.category);
        metadata.put("fileName", this.fileName);
        metadata.put("projectId", this.projectId);
        metadata.put("userId", this.userId);
        metadata.put("createdAt", this.createdAt);
        metadata.put("tags", this.tags);
        return metadata;
    }

    /**
     * Set processing completed timestamp and update status
     */
    public void setProcessingCompleted(LocalDateTime completedTime) {
        this.processingCompleted = completedTime;
        this.processingStatus = "COMPLETED";
    }

    // === MARKDOWN SPECIFIC ===
    private List<String> markdownHeaders; // Extracted headers from markdown
    private Map<String, String> markdownMetadata; // Front matter metadata
    private List<String> markdownLinks; // Extracted links
    private List<String> markdownImages; // Extracted images
    private String markdownTableOfContents; // Generated TOC

    // === SEARCH OPTIMIZATION ===
    private String searchableText; // Processed text optimized for search
    private Map<String, Double> termFrequency; // TF-IDF scores
    private List<String> searchKeywords; // Keywords for search optimization
    private String searchSummary; // Summary optimized for search results

    // === AI AGENT CONTEXT ===
    private Map<String, Object> chatContext; // Context for AI conversations
    private List<String> suggestedQuestions; // AI-generated questions this doc can answer
    private String conversationStarters; // How AI should introduce this document
    private Map<String, String> aiPersonalization; // Personalized content for different user types

    /**
     * Helper method to check if document is ready for AI Agent use
     */
    public boolean isReadyForAI() {
        return "COMPLETED".equals(processingStatus) &&
               embedding != null &&
               embedding.length > 0 &&
               aiContentSummary != null &&
               !aiContentSummary.isEmpty();
    }

    /**
     * Helper method to check if document is accessible by user
     */
    public boolean isAccessibleBy(Long userId, List<Long> userTeams) {
        if ("PUBLIC".equals(accessLevel)) {
            return true;
        }

        if (this.userId != null && this.userId.equals(userId)) {
            return true;
        }

        if (authorizedUsers != null && authorizedUsers.contains(userId)) {
            return true;
        }

        if (authorizedTeams != null && userTeams != null) {
            return authorizedTeams.stream().anyMatch(userTeams::contains);
        }

        return false;
    }

    /**
     * Helper method to get document type for AI Agent
     */
    public String getDocumentTypeForAI() {
        if (fileExtension != null) {
            switch (fileExtension.toLowerCase()) {
                case ".md":
                    return "Markdown Document";
                case ".txt":
                    return "Text Document";
                case ".pdf":
                    return "PDF Document";
                case ".docx":
                    return "Word Document";
                default:
                    return "Document";
            }
        }
        return sourceType != null ? sourceType : "Document";
    }

    /**
     * Helper method to get AI-friendly description
     */
    public String getAIDescription() {
        StringBuilder desc = new StringBuilder();

        if (title != null) {
            desc.append("Title: ").append(title).append(". ");
        }

        if (aiContentSummary != null) {
            desc.append("Summary: ").append(aiContentSummary).append(". ");
        }

        if (aiTopics != null && !aiTopics.isEmpty()) {
            desc.append("Topics: ").append(String.join(", ", aiTopics)).append(". ");
        }

        if (wordCount != null) {
            desc.append("Length: ").append(wordCount).append(" words. ");
        }

        return desc.toString();
    }

    /**
     * Helper method to create search-optimized text
     */
    public String getSearchableContent() {
        if (searchableText != null) {
            return searchableText;
        }

        StringBuilder searchable = new StringBuilder();

        if (title != null) {
            searchable.append(title).append(" ");
        }

        if (aiExtractedKeywords != null) {
            searchable.append(aiExtractedKeywords).append(" ");
        }

        if (content != null) {
            searchable.append(content);
        }

        return searchable.toString();
    }
}
