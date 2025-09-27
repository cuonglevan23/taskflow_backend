package com.example.taskmanagement_backend.agent.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * RAG Service - Retrieval-Augmented Generation
 * Enhances AI responses with relevant context from knowledge base
 * Handles DECLINING intent with contextual understanding
 * FIXED: Removed SessionMemoryService dependency to avoid circular reference
 */
@Slf4j
@Service
public class RAGService {

    private final EmbeddingService embeddingService;
    private final PineconeService pineconeService;
    // REMOVED: SessionMemoryService dependency to break circular reference

    @Value("${ai.rag.similarity.threshold:0.7}")
    private double similarityThreshold;

    @Value("${ai.rag.max.context.documents:5}")
    private int maxContextDocuments;

    // In-memory knowledge base for quick lookup (can be replaced with Redis/DB)
    private final Map<String, KnowledgeDocument> knowledgeBase = new ConcurrentHashMap<>();

    public RAGService(EmbeddingService embeddingService,
                      PineconeService pineconeService) {
        this.embeddingService = embeddingService;
        this.pineconeService = pineconeService;

        // Initialize with basic knowledge about declining/rejection patterns
        initializeDefaultKnowledge();
        log.info("🧠 RAG Service initialized with knowledge base");
    }

    /**
     * 2. Generate embedding for document chunk (delegates to EmbeddingService)
     */
    public double[] generateEmbeddingForChunk(String chunkContent) {
        return embeddingService.generateEmbedding(chunkContent);
    }

    /**
     * Enhanced context retrieval for user messages
     * Combines vector search with conversation history
     */
    public RAGContext retrieveContext(String userMessage, String conversationId, Long userId) {
        try {
            log.debug("🔍 RAG Pipeline: Processing user query: {}", userMessage.substring(0, Math.min(50, userMessage.length())));

            // 4-5. Generate embedding for user question
            double[] queryEmbedding = embeddingService.generateEmbedding(userMessage);

            // 6. Query Pinecone for top-k similar vectors
            List<KnowledgeDocument> similarDocuments = queryVectorDatabase(queryEmbedding, maxContextDocuments);

            // Get conversation context
            String conversationContext = getConversationContext(conversationId);

            // Analyze declining patterns
            DecliningAnalysis decliningAnalysis = analyzeDecliningPatterns(userMessage, conversationContext);

            // 7-8. Build enhanced context from retrieved vectors
            return RAGContext.builder()
                .relevantDocuments(similarDocuments)
                .conversationContext(conversationContext)
                .decliningAnalysis(decliningAnalysis)
                .userMessage(userMessage)
                .contextQuality(calculateContextQuality(similarDocuments))
                .retrievalTime(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("❌ RAG Pipeline error", e);
            return RAGContext.builder()
                .relevantDocuments(new ArrayList<>())
                .conversationContext("")
                .decliningAnalysis(new DecliningAnalysis(false, "unknown", 0.0))
                .userMessage(userMessage)
                .contextQuality(0.0)
                .retrievalTime(LocalDateTime.now())
                .build();
        }
    }

    /**
     * 6. Query vector database (Pinecone) for similar documents
     * Uses cosine similarity with taskflow-documents index
     */
    private List<KnowledgeDocument> queryVectorDatabase(double[] queryEmbedding, int topK) {
        // First try Pinecone vector search
        List<KnowledgeDocument> pineconeResults = queryPineconeVectors(queryEmbedding, topK);

        if (!pineconeResults.isEmpty()) {
            return pineconeResults;
        }

        // Fallback to in-memory search
        log.debug("🔄 Fallback to in-memory vector search");
        return findSimilarDocuments(queryEmbedding, topK);
    }

    /**
     * Query Pinecone vector database
     */
    private List<KnowledgeDocument> queryPineconeVectors(double[] queryEmbedding, int topK) {
        try {
            // Convert double[] to List<Double> for Pinecone API
            List<Double> queryVector = Arrays.stream(queryEmbedding).boxed().collect(Collectors.toList());

            // Query Pinecone with cosine similarity
            Map<String, Object> queryResult = pineconeService.queryVectors(queryVector, topK, Map.of()).block();

            if (queryResult == null) {
                return new ArrayList<>();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = (List<Map<String, Object>>) queryResult.get("matches");

            if (matches == null) {
                return new ArrayList<>();
            }

            return matches.stream()
                .map(this::convertPineconeMatchToKnowledgeDocument)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("⚠️ Pinecone query failed, using fallback: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 7. Convert Pinecone match to KnowledgeDocument (lấy content từ metadata/database)
     */
    private KnowledgeDocument convertPineconeMatchToKnowledgeDocument(Map<String, Object> match) {
        try {
            String id = (String) match.get("id");
            Double score = (Double) match.get("score");

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) match.get("metadata");

            if (id == null || metadata == null) {
                return null;
            }

            // 7. Get content from metadata (stored during upsert)
            String content = (String) metadata.get("content");
            String title = (String) metadata.get("title");

            if (content == null) {
                // Fallback: try to get from in-memory knowledge base
                KnowledgeDocument memoryDoc = knowledgeBase.get(id);
                if (memoryDoc != null) {
                    content = memoryDoc.getContent();
                    title = memoryDoc.getTitle();
                }
            }

            return KnowledgeDocument.builder()
                .id(id)
                .title(title != null ? title : "Document " + id)
                .content(content != null ? content : "Content not available")
                .metadata(metadata)
                .similarityScore(score != null ? score : 0.0)
                .createdAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.warn("⚠️ Failed to convert Pinecone match: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Enhanced Pinecone storage with automatic dimension detection
     */
    private void storeInVectorDatabase(KnowledgeDocument doc) {
        try {
            // Validate embedding exists
            if (doc.getEmbedding() == null || doc.getEmbedding().length == 0) {
                log.warn("⚠️ Skipping Pinecone storage - no embedding for document: {}", doc.getId());
                return;
            }

            // Prepare sanitized content for metadata
            String sanitizedContent = sanitizeContentForPinecone(doc.getContent());
            String sanitizedTitle = sanitizeContentForPinecone(doc.getTitle());

            // Store essential metadata only to avoid size issues
            Map<String, Object> enhancedMetadata = new HashMap<>();
            enhancedMetadata.put("title", sanitizedTitle);
            enhancedMetadata.put("content", sanitizedContent);
            enhancedMetadata.put("created_at", doc.getCreatedAt().toString());
            enhancedMetadata.put("doc_type", doc.getMetadata().getOrDefault("type", "knowledge"));

            // Add category if available
            if (doc.getMetadata().containsKey("category")) {
                enhancedMetadata.put("category", doc.getMetadata().get("category").toString());
            }

            // FIXED: Auto-detect vector dimension instead of hardcoding 768
            List<Double> vectorValues = Arrays.stream(doc.getEmbedding()).boxed().collect(Collectors.toList());
            int actualDimension = vectorValues.size();

            log.debug("Vector info for {}: dimension={}, expectedDimensions=[768,1024,1536]",
                doc.getId(), actualDimension);

            // Accept common embedding dimensions (768, 1024, 1536)
            if (actualDimension != 768 && actualDimension != 1024 && actualDimension != 1536) {
                log.warn("⚠️ Unusual vector dimension: {} for document: {} (supported: 768, 1024, 1536)",
                    actualDimension, doc.getId());
                // Still proceed - let Pinecone validate
            }

            List<Map<String, Object>> vectors = List.of(
                Map.of(
                    "id", doc.getId(),
                    "values", vectorValues,
                    "metadata", enhancedMetadata
                )
            );

            // 3. Upsert to taskflow-documents index with cosine metric
            pineconeService.upsertVectors(vectors)
                .doOnSuccess(response -> log.debug("✅ Vector upserted to Pinecone: {} (dim: {})", doc.getId(), actualDimension))
                .doOnError(error -> {
                    log.error("❌ Pinecone upsert failed for {} (dim: {}): {}", doc.getId(), actualDimension, error.getMessage());
                    log.debug("Failed vector details - ID: {}, dimension: {}, metadata keys: {}",
                        doc.getId(), actualDimension, enhancedMetadata.keySet());
                })
                .subscribe();

        } catch (Exception e) {
            log.error("❌ Error preparing vector for Pinecone storage ({}): {}", doc.getId(), e.getMessage());
        }
    }

    /**
     * Sanitize content for Pinecone metadata to avoid special characters and size issues
     */
    private String sanitizeContentForPinecone(String content) {
        if (content == null) {
            return "";
        }

        // Limit content length for metadata
        String sanitized = content.length() > 500 ? content.substring(0, 500) + "..." : content;

        // Remove or replace problematic characters
        sanitized = sanitized.replaceAll("[\\x00-\\x1F\\x7F]", " "); // Remove control characters
        sanitized = sanitized.replaceAll("\\s+", " "); // Normalize whitespace
        sanitized = sanitized.trim();

        return sanitized;
    }

    /**
     * Enhanced context generation for AI prompts with declining support
     */
    public String generateEnhancedContext(RAGContext ragContext) {
        StringBuilder context = new StringBuilder();

        context.append("=== RAG ENHANCED CONTEXT ===\n");

        // Add relevant documents
        if (!ragContext.getRelevantDocuments().isEmpty()) {
            context.append("=== RELEVANT KNOWLEDGE ===\n");
            for (KnowledgeDocument doc : ragContext.getRelevantDocuments()) {
                context.append("Document: ").append(doc.getTitle()).append("\n");
                context.append("Content: ").append(doc.getContent().substring(0,
                    Math.min(200, doc.getContent().length()))).append("...\n");
                context.append("Relevance: ").append(String.format("%.2f", doc.getSimilarityScore())).append("\n\n");
            }
        }

        // Add declining analysis
        DecliningAnalysis declining = ragContext.getDecliningAnalysis();
        if (declining.isDeclining()) {
            context.append("=== DECLINING DETECTION ===\n");
            context.append("User is declining/rejecting: ").append(declining.getDeclineType()).append("\n");
            context.append("Confidence: ").append(String.format("%.2f", declining.getConfidence())).append("\n");
            context.append("Suggested response: Respect user's decision, don't push tools\n\n");
        }

        // Add conversation context
        if (!ragContext.getConversationContext().isEmpty()) {
            context.append("=== CONVERSATION HISTORY ===\n");
            context.append(ragContext.getConversationContext()).append("\n\n");
        }

        context.append("=== SYSTEM INSTRUCTIONS ===\n");
        context.append("- If user is declining, respect their decision and don't use tools\n");
        context.append("- Use knowledge base information to provide accurate responses\n");
        context.append("- Maintain context awareness from conversation history\n");
        context.append("- Be helpful but not pushy when user shows resistance\n\n");

        return context.toString();
    }

    /**
     * Store knowledge document in both memory and vector database
     */
    public void storeKnowledge(String id, String title, String content, Map<String, Object> metadata) {
        try {
            // Generate embedding
            double[] embedding = embeddingService.generateEmbedding(content);

            // Create knowledge document
            KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(id)
                .title(title)
                .content(content)
                .metadata(metadata)
                .embedding(embedding)
                .createdAt(LocalDateTime.now())
                .build();

            // Store in memory
            knowledgeBase.put(id, doc);

            // Store in Pinecone (async)
            storeInVectorDatabase(doc);

            log.info("📚 Stored knowledge document: {}", title);

        } catch (Exception e) {
            log.error("Error storing knowledge document", e);
        }
    }

    /**
     * Retrieve contextual information for general use (backward compatibility)
     */
    public String retrieveContextualInformation(String userMessage, Long userId) {
        try {
            RAGContext ragContext = retrieveContext(userMessage, "general", userId);
            return generateEnhancedContext(ragContext);
        } catch (Exception e) {
            log.debug("Could not retrieve contextual information", e);
            return "Basic context available - enhanced retrieval temporarily unavailable.";
        }
    }

    /**
     * Bulk load documents from content map (for DocumentLoaderService)
     */
    public void bulkLoadDocuments(Map<String, DocumentContent> documents) {
        log.info("📚 Starting bulk document loading - {} documents", documents.size());

        int loaded = 0;
        for (Map.Entry<String, DocumentContent> entry : documents.entrySet()) {
            try {
                DocumentContent doc = entry.getValue();
                storeKnowledge(entry.getKey(), doc.getTitle(), doc.getContent(), doc.getMetadata());
                loaded++;

                if (loaded % 10 == 0) {
                    log.info("📖 Loaded {} documents...", loaded);
                }
            } catch (Exception e) {
                log.warn("❌ Failed to load document: {}", entry.getKey(), e);
            }
        }

        log.info("✅ Bulk loading completed - {} documents loaded into knowledge base", loaded);
    }

    /**
     * Search documents by category for quick access
     */
    public List<KnowledgeDocument> searchByCategory(String category, int maxResults) {
        return knowledgeBase.values().stream()
            .filter(doc -> {
                Object categoryMeta = doc.getMetadata().get("category");
                return categoryMeta != null && categoryMeta.toString().equalsIgnoreCase(category);
            })
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(maxResults)
            .collect(Collectors.toList());
    }

    /**
     * Get knowledge base statistics
     */
    public Map<String, Object> getKnowledgeBaseStats() {
        Map<String, Long> categoryStats = knowledgeBase.values().stream()
            .collect(Collectors.groupingBy(
                doc -> doc.getMetadata().getOrDefault("category", "unknown").toString(),
                Collectors.counting()
            ));

        return Map.of(
            "totalDocuments", knowledgeBase.size(),
            "categoryCounts", categoryStats,
            "lastUpdated", LocalDateTime.now().toString(),
            "systemStatus", "operational"
        );
    }

    /**
     * Analyze declining patterns in user message - IMPROVED: Let AI model decide instead of hard-coding
     */
    private DecliningAnalysis analyzeDecliningPatterns(String userMessage, String conversationContext) {
        // FIXED: Remove hard-coded keyword matching - let Gemini AI decide the intent instead
        // Only check for very explicit declining patterns in conversation context

        boolean isDeclining = false;
        String declineType = "unknown";
        double confidence = 0.0;

        // Only detect declining from conversation context (previous AI-user interactions)
        if (conversationContext != null && !conversationContext.isEmpty()) {
            String lowerContext = conversationContext.toLowerCase();

            // Check if user explicitly declined a previous suggestion
            if (lowerContext.contains("ai: tôi có thể giúp") &&
                lowerContext.contains("user: không") &&
                userMessage.toLowerCase().trim().equals("không")) {
                isDeclining = true;
                declineType = "context_decline";
                confidence = 0.8;
            }
        }

        // For standalone messages, let the AI model decide - don't hard-code "không" as declining
        // Questions like "bạn tạo được project không?" should be classified as QUESTION, not DECLINING

        log.debug("Declining analysis - message: '{}', declining: {}, confidence: {}",
            userMessage, isDeclining, confidence);

        return new DecliningAnalysis(isDeclining, declineType, confidence);
    }

    /**
     * Find similar documents using embedding similarity
     */
    private List<KnowledgeDocument> findSimilarDocuments(double[] queryEmbedding, int maxResults) {
        return knowledgeBase.values().stream()
            .map(doc -> {
                double similarity = embeddingService.calculateSimilarity(queryEmbedding, doc.getEmbedding());
                doc.setSimilarityScore(similarity);
                return doc;
            })
            .filter(doc -> doc.getSimilarityScore() > similarityThreshold)
            .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
            .limit(maxResults)
            .collect(Collectors.toList());
    }

    /**
     * Get conversation context - FIXED: Remove dependency on SessionMemoryService
     */
    private String getConversationContext(String conversationId) {
        // FIXED: Return empty string instead of calling SessionMemoryService
        // The conversation context will be provided by CoreAgentService instead
        log.debug("Conversation context will be provided by calling service");
        return "";
    }

    /**
     * Calculate context quality based on retrieved documents
     */
    private double calculateContextQuality(List<KnowledgeDocument> documents) {
        if (documents.isEmpty()) {
            return 0.0;
        }

        return documents.stream()
            .mapToDouble(KnowledgeDocument::getSimilarityScore)
            .average()
            .orElse(0.0);
    }


    /**
     * Initialize default knowledge base with declining patterns and task management knowledge
     */
    private void initializeDefaultKnowledge() {
        // Declining patterns knowledge
        storeKnowledge(
            "decline_patterns_vi",
            "Vietnamese Declining Patterns",
            "Các từ khóa từ chối trong tiếng Việt: không, không cần, không muốn, thôi, bỏ qua, từ chối, hủy, dừng lại. " +
            "Khi user dùng các từ này, cần tôn trọng quyết định và không ép buộc sử dụng tools.",
            Map.of("type", "declining_patterns", "language", "vietnamese")
        );

        storeKnowledge(
            "decline_patterns_en",
            "English Declining Patterns",
            "English declining keywords: no, no thanks, don't want, cancel, stop, decline, refuse, reject, skip, pass, not interested. " +
            "When user uses these words, respect their decision and avoid pushing tools.",
            Map.of("type", "declining_patterns", "language", "english")
        );

        // Task management knowledge
        storeKnowledge(
            "task_management_guide",
            "Task Management Best Practices",
            "Hệ thống task management hỗ trợ tạo, cập nhật, xóa và thống kê task. " +
            "Khi user từ chối tạo task, có thể đề xuất các cách khác như ghi chú hoặc reminder.",
            Map.of("type", "task_management", "category", "guide")
        );

        log.info("📚 Initialized default knowledge base with {} documents", knowledgeBase.size());
    }

    // Data classes
    @Data
    @lombok.Builder
    public static class RAGContext {
        private List<KnowledgeDocument> relevantDocuments;
        private String conversationContext;
        private DecliningAnalysis decliningAnalysis;
        private String userMessage;
        private double contextQuality;
        private LocalDateTime retrievalTime;
    }

    @Data
    @lombok.Builder
    public static class KnowledgeDocument {
        private String id;
        private String title;
        private String content;
        private Map<String, Object> metadata;
        private double[] embedding;
        private double similarityScore;
        private LocalDateTime createdAt;
    }

    @Data
    @lombok.AllArgsConstructor
    public static class DecliningAnalysis {
        private boolean isDeclining;
        private String declineType;
        private double confidence;
    }

    // Data class for bulk loading
    @Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class DocumentContent {
        private String title;
        private String content;
        private Map<String, Object> metadata;
    }
}
