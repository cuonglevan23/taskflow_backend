package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.document.EmbeddingDocument;
import com.example.taskmanagement_backend.agent.dto.ChatResponse;
import com.example.taskmanagement_backend.agent.retriever.PineconeRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced Memory & RAG Layer
 * Kết hợp Redis session memory với Pinecone RAG để agent hiểu dự án
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedMemoryRAGService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PineconeRetriever pineconeRetriever;
    private final SessionMemoryService sessionMemoryService;

    private static final String CONTEXT_PREFIX = "agent_context:";
    private static final String PROJECT_NAMESPACE_PREFIX = "project_";
    private static final int CONTEXT_TTL_HOURS = 24;

    /**
     * Get enhanced context cho AI agent từ nhiều nguồn
     */
    public String getEnhancedContext(String conversationId, Long userId, String userMessage, Long projectId) {
        try {
            log.debug("Getting enhanced context for conversation: {}, project: {}", conversationId, projectId);

            // 1. Session memory context (ephemeral)
            String sessionContext = getSessionContext(conversationId, userId);

            // 2. RAG context từ Pinecone (knowledge base)
            String ragContext = getRAGContext(userMessage, projectId);

            // 3. Project-specific context (cached)
            String projectContext = getProjectContext(projectId);

            // 4. User interaction patterns (analytics)
            String userPatternContext = getUserPatternContext(userId);

            // Combine all contexts intelligently
            return combineContexts(sessionContext, ragContext, projectContext, userPatternContext, userMessage);

        } catch (Exception e) {
            log.error("Error getting enhanced context", e);
            return "Context retrieval error - using fallback basic context";
        }
    }

    /**
     * Get session context từ Redis
     */
    private String getSessionContext(String conversationId, Long userId) {
        try {
            List<ChatResponse> sessionMessages = sessionMemoryService.getSessionMessages(conversationId, userId);

            if (sessionMessages.isEmpty()) {
                return "New conversation - no previous context";
            }

            // Get last 5 messages cho context
            int start = Math.max(0, sessionMessages.size() - 5);
            List<ChatResponse> recentMessages = sessionMessages.subList(start, sessionMessages.size());

            StringBuilder context = new StringBuilder("Recent conversation:\n");
            for (ChatResponse msg : recentMessages) {
                context.append(msg.getSenderType()).append(": ")
                       .append(msg.getContent()).append("\n");
            }

            return context.toString();

        } catch (Exception e) {
            log.error("Error getting session context", e);
            return "Session context unavailable";
        }
    }

    /**
     * Get RAG context từ Pinecone với enhanced document processing
     */
    private String getRAGContext(String userMessage, Long projectId) {
        try {
            String namespace = projectId != null ? PROJECT_NAMESPACE_PREFIX + projectId : "general";

            // Query Pinecone for relevant documents
            List<EmbeddingDocument> relevantDocs = pineconeRetriever.queryVector(userMessage, namespace, 5);

            if (relevantDocs.isEmpty()) {
                return "No specific context found in knowledge base";
            }

            StringBuilder ragContext = new StringBuilder("Relevant documents from knowledge base:\n\n");

            for (int i = 0; i < relevantDocs.size(); i++) {
                EmbeddingDocument doc = relevantDocs.get(i);

                // Enhanced document context với AI-generated content
                ragContext.append(String.format("=== DOCUMENT %d: %s ===\n", i + 1, doc.getDocumentTypeForAI()));

                if (doc.getTitle() != null) {
                    ragContext.append("Title: ").append(doc.getTitle()).append("\n");
                }

                // Use AI-enhanced summary if available
                if (doc.getAiContentSummary() != null) {
                    ragContext.append("AI Summary: ").append(doc.getAiContentSummary()).append("\n");
                } else if (doc.getSummary() != null) {
                    ragContext.append("Summary: ").append(doc.getSummary()).append("\n");
                }

                // Add AI-extracted keywords and topics
                if (doc.getAiExtractedKeywords() != null) {
                    ragContext.append("Key Terms: ").append(doc.getAiExtractedKeywords()).append("\n");
                }

                if (doc.getAiTopics() != null && !doc.getAiTopics().isEmpty()) {
                    ragContext.append("Topics: ").append(String.join(", ", doc.getAiTopics())).append("\n");
                }

                // Add relevant content snippet
                String content = doc.getContent();
                if (content != null) {
                    if (content.length() > 500) {
                        ragContext.append("Content Excerpt: ").append(content.substring(0, 500)).append("...\n");
                    } else {
                        ragContext.append("Content: ").append(content).append("\n");
                    }
                }

                // Add AI recommendations if available
                if (doc.getAiRecommendedActions() != null) {
                    ragContext.append("AI Recommendations: ").append(doc.getAiRecommendedActions()).append("\n");
                }

                // Add file-specific metadata for markdown/text files
                if ("MARKDOWN_FILE".equals(doc.getSourceType()) || "TEXT_FILE".equals(doc.getSourceType())) {
                    if (doc.getFileName() != null) {
                        ragContext.append("File: ").append(doc.getFileName()).append("\n");
                    }

                    if (doc.getMarkdownHeaders() != null && !doc.getMarkdownHeaders().isEmpty()) {
                        ragContext.append("Document Structure: ").append(String.join(", ", doc.getMarkdownHeaders().subList(0, Math.min(3, doc.getMarkdownHeaders().size())))).append("\n");
                    }
                }

                ragContext.append("Relevance Score: ").append(String.format("%.2f", doc.getRelevanceScore())).append("\n");
                ragContext.append("\n");
            }

            // Add context about document accessibility and personalization
            ragContext.append("=== CONTEXT NOTES ===\n");
            ragContext.append("Found ").append(relevantDocs.size()).append(" relevant documents from your accessible knowledge base.\n");
            ragContext.append("These documents have been AI-analyzed and enhanced for better understanding.\n\n");

            return ragContext.toString();

        } catch (Exception e) {
            log.error("Error getting RAG context from enhanced documents", e);
            return "Enhanced RAG context temporarily unavailable - using basic context";
        }
    }

    /**
     * Get project-specific context (cached trong Redis)
     */
    private String getProjectContext(Long projectId) {
        if (projectId == null) {
            return "General Taskflow context";
        }

        try {
            String cacheKey = CONTEXT_PREFIX + "project:" + projectId;
            String cachedContext = (String) redisTemplate.opsForValue().get(cacheKey);

            if (cachedContext != null) {
                return cachedContext;
            }

            // Build project context from various sources
            String projectContext = buildProjectContext(projectId);

            // Cache for future use
            redisTemplate.opsForValue().set(cacheKey, projectContext, CONTEXT_TTL_HOURS, TimeUnit.HOURS);

            return projectContext;

        } catch (Exception e) {
            log.error("Error getting project context for project: {}", projectId, e);
            return "Project context unavailable";
        }
    }

    /**
     * Get user interaction patterns
     */
    private String getUserPatternContext(Long userId) {
        try {
            String cacheKey = CONTEXT_PREFIX + "user_patterns:" + userId;
            String cachedPatterns = (String) redisTemplate.opsForValue().get(cacheKey);

            if (cachedPatterns != null) {
                return cachedPatterns;
            }

            // Analyze user patterns (simplified)
            String patterns = analyzeUserPatterns(userId);

            // Cache patterns
            redisTemplate.opsForValue().set(cacheKey, patterns, CONTEXT_TTL_HOURS, TimeUnit.HOURS);

            return patterns;

        } catch (Exception e) {
            log.error("Error getting user patterns for user: {}", userId, e);
            return "User pattern analysis unavailable";
        }
    }

    /**
     * Store conversation insights cho future context
     */
    public void storeConversationInsights(String conversationId, Long userId, String userMessage,
                                        String aiResponse, String detectedIntent, Long projectId) {
        try {
            String insightKey = CONTEXT_PREFIX + "insights:" + conversationId;

            Map<String, Object> insights = Map.of(
                "lastIntent", detectedIntent,
                "lastUserMessage", userMessage.substring(0, Math.min(200, userMessage.length())),
                "lastAIResponse", aiResponse.substring(0, Math.min(200, aiResponse.length())),
                "projectId", projectId != null ? projectId : 0,
                "userId", userId,
                "timestamp", System.currentTimeMillis()
            );

            redisTemplate.opsForHash().putAll(insightKey, insights);
            redisTemplate.expire(insightKey, CONTEXT_TTL_HOURS, TimeUnit.HOURS);

            log.debug("Stored conversation insights for: {}", conversationId);

        } catch (Exception e) {
            log.error("Error storing conversation insights", e);
        }
    }

    /**
     * Update project knowledge base
     */
    public void updateProjectKnowledge(Long projectId, String content, String category, String sourceId) {
        try {
            String namespace = PROJECT_NAMESPACE_PREFIX + projectId;

            EmbeddingDocument document = EmbeddingDocument.builder()
                .id("project_" + projectId + "_" + sourceId + "_" + System.currentTimeMillis())
                .content(content)
                .category(category)
                .sourceType("PROJECT_DATA")
                .sourceId(sourceId)
                .language("vi")
                .build();

            pineconeRetriever.upsertDocument(document, namespace);

            // Invalidate project context cache
            String cacheKey = CONTEXT_PREFIX + "project:" + projectId;
            redisTemplate.delete(cacheKey);

            log.info("Updated project knowledge for project: {}", projectId);

        } catch (Exception e) {
            log.error("Error updating project knowledge", e);
        }
    }

    /**
     * Clear user session context on logout
     */
    public void clearUserSessionContext(Long userId) {
        try {
            String pattern = CONTEXT_PREFIX + "*:" + userId;
            // Note: This is a simplified approach. In production, use SCAN for large datasets
            redisTemplate.delete(pattern);

            log.info("Cleared session context for user: {}", userId);

        } catch (Exception e) {
            log.error("Error clearing user session context", e);
        }
    }

    /**
     * Query specific document by filename/type - for targeted knowledge retrieval
     */
    public List<EmbeddingDocument> querySpecificDocument(String query, String documentIdentifier, int topK) {
        try {
            log.debug("Querying specific document: {}, identifier: {}", query, documentIdentifier);

            // Create enhanced query for specific document search
            String enhancedQuery = query + " " + documentIdentifier.replace("_", " ");

            // Query with metadata filter for specific document
            Map<String, Object> metadataFilter = Map.of(
                "title", documentIdentifier,
                "category", "API_DOCUMENTATION"
            );

            List<EmbeddingDocument> results = pineconeRetriever.queryByMetadata(
                enhancedQuery, "general", metadataFilter, topK);

            if (!results.isEmpty()) {
                log.debug("Found {} results for document: {}", results.size(), documentIdentifier);
                return results;
            }

            // Fallback: general query if specific document not found
            log.debug("No specific document found, falling back to general query");
            return pineconeRetriever.queryVector(query, "general", topK);

        } catch (Exception e) {
            log.error("Error querying specific document: {}", documentIdentifier, e);
            return List.of(); // Return empty list on error
        }
    }


    // Helper methods
    private String combineContexts(String sessionContext, String ragContext,
                                 String projectContext, String userPatternContext, String userMessage) {

        StringBuilder combinedContext = new StringBuilder();

        // Add session context (most recent)
        if (sessionContext != null && !sessionContext.contains("unavailable")) {
            combinedContext.append("CONVERSATION HISTORY:\n").append(sessionContext).append("\n");
        }

        // Add RAG context (knowledge base)
        if (ragContext != null && !ragContext.contains("unavailable")) {
            combinedContext.append("KNOWLEDGE BASE:\n").append(ragContext).append("\n");
        }

        // Add project context if relevant
        if (projectContext != null && !projectContext.contains("unavailable")) {
            combinedContext.append("PROJECT CONTEXT:\n").append(projectContext).append("\n");
        }

        // Add user patterns if relevant to query
        if (userPatternContext != null && !userPatternContext.contains("unavailable") &&
            isPatternRelevant(userMessage, userPatternContext)) {
            combinedContext.append("USER PREFERENCES:\n").append(userPatternContext).append("\n");
        }

        // Add current query context
        combinedContext.append("CURRENT QUERY:\n").append(userMessage);

        return combinedContext.toString();
    }

    private String buildProjectContext(Long projectId) {
        // In a real implementation, this would:
        // 1. Query project details from database
        // 2. Get recent project activities
        // 3. Analyze project team composition
        // 4. Get project-specific settings

        return String.format("Project %d context: Active project with ongoing tasks and team collaboration", projectId);
    }

    private String analyzeUserPatterns(Long userId) {
        // In a real implementation, this would analyze:
        // 1. Frequent question types
        // 2. Preferred response styles
        // 3. Common workflow patterns
        // 4. Time zone and activity patterns

        return "User patterns: Professional user, prefers detailed explanations, active during business hours";
    }

    private boolean isPatternRelevant(String userMessage, String userPatternContext) {
        // Simple relevance check - can be enhanced with ML
        String lowerMessage = userMessage.toLowerCase();
        return lowerMessage.contains("how") || lowerMessage.contains("prefer") ||
               lowerMessage.contains("usually") || lowerMessage.contains("normally");
    }

    /**
     * Get context statistics for monitoring
     */
    public Map<String, Object> getContextStatistics() {
        try {
            // Get Redis memory usage for contexts
            Map<String, Object> stats = Map.of(
                "redisConnectionStatus", "connected",
                "pineconeConnectionStatus", "connected",
                "cachedContexts", redisTemplate.keys(CONTEXT_PREFIX + "*").size(),
                "lastUpdated", System.currentTimeMillis()
            );

            return stats;

        } catch (Exception e) {
            log.error("Error getting context statistics", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /**
     * Cache document metadata cho quick access
     */
    private void cacheDocumentMetadata(EmbeddingDocument document) {
        try {
            String cacheKey = "doc_metadata:" + document.getId();
            Map<String, Object> metadata = Map.of(
                "title", document.getTitle() != null ? document.getTitle() : "",
                "summary", document.getAiContentSummary() != null ? document.getAiContentSummary() : "",
                "topics", document.getAiTopics() != null ? document.getAiTopics() : List.of(),
                "keywords", document.getAiExtractedKeywords() != null ? document.getAiExtractedKeywords() : "",
                "fileType", document.getDocumentTypeForAI(),
                "lastUpdated", document.getUpdatedAt().toString()
            );

            redisTemplate.opsForValue().set(cacheKey, metadata, 24, TimeUnit.HOURS);

        } catch (Exception e) {
            log.error("Error caching document metadata", e);
        }
    }

    /**
     * Update user document interaction patterns
     */
    private void updateUserDocumentPatterns(Long userId, String intent, Long projectId) {
        try {
            String patternKey = "user_doc_patterns:" + userId;

            @SuppressWarnings("unchecked")
            Map<String, Object> patterns = (Map<String, Object>) redisTemplate.opsForValue().get(patternKey);
            if (patterns == null) {
                patterns = new java.util.HashMap<>();
            }

            // Update intent frequency
            @SuppressWarnings("unchecked")
            Map<String, Integer> intentCounts = (Map<String, Integer>) patterns.getOrDefault("intentCounts", new java.util.HashMap<String, Integer>());
            intentCounts.merge(intent, 1, Integer::sum);
            patterns.put("intentCounts", intentCounts);

            // Update project interaction
            if (projectId != null) {
                @SuppressWarnings("unchecked")
                Map<String, Integer> projectCounts = (Map<String, Integer>) patterns.getOrDefault("projectCounts", new java.util.HashMap<String, Integer>());
                projectCounts.merge(projectId.toString(), 1, Integer::sum);
                patterns.put("projectCounts", projectCounts);
            }

            // Update last activity
            patterns.put("lastActivity", System.currentTimeMillis());

            redisTemplate.opsForValue().set(patternKey, patterns, 30, TimeUnit.DAYS);

        } catch (Exception e) {
            log.error("Error updating user document patterns", e);
        }
    }

    /**
     * Update document usage statistics
     */
    private void updateDocumentStatistics(EmbeddingDocument document) {
        try {
            String statsKey = "doc_stats:" + document.getId();

            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) redisTemplate.opsForValue().get(statsKey);
            if (stats == null) {
                stats = new java.util.HashMap<>();
                stats.put("addedAt", System.currentTimeMillis());
                stats.put("accessCount", 0);
                stats.put("queryMatches", 0);
            }

            stats.put("lastUpdated", System.currentTimeMillis());

            redisTemplate.opsForValue().set(statsKey, stats, 30, TimeUnit.DAYS);

        } catch (Exception e) {
            log.error("Error updating document statistics", e);
        }
    }

    /**
     * Get user document interaction patterns
     */
    private Map<String, Object> getUserDocumentPatterns(Long userId) {
        try {
            String patternKey = "user_doc_patterns:" + userId;

            @SuppressWarnings("unchecked")
            Map<String, Object> patterns = (Map<String, Object>) redisTemplate.opsForValue().get(patternKey);

            return patterns != null ? patterns : Map.of();

        } catch (Exception e) {
            log.error("Error getting user document patterns", e);
            return Map.of();
        }
    }
}
