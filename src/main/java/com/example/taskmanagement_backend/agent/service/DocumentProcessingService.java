package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.document.EmbeddingDocument;
import com.example.taskmanagement_backend.agent.retriever.PineconeRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Document Processing Service for RAG Integration
 * Handles markdown files like AI_AGENT_MYTASK_API_GUIDE.md
 * Processes content for optimal embedding and retrieval
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final EmbeddingService embeddingService;
    private final PineconeRetriever pineconeRetriever;

    // Regex patterns for markdown parsing
    private static final Pattern HEADER_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```", Pattern.MULTILINE);
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^\\*]+)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*([^\\*]+)\\*");

    /**
     * Process and index the AI_AGENT_MYTASK_API_GUIDE.md file
     */
    public EmbeddingDocument processMyTaskAPIGuide(String filePath) {
        try {
            log.info("Processing MyTask API Guide: {}", filePath);

            // Read file content
            String content = Files.readString(Path.of(filePath));

            // Parse markdown content
            EmbeddingDocument document = parseMarkdownDocument(content, filePath);

            // Enhance with AI analysis
            enhanceDocumentWithAI(document);

            // Generate embedding
            generateEmbeddingForDocument(document);

            // Index in Pinecone
            indexDocumentInPinecone(document);

            log.info("Successfully processed and indexed MyTask API Guide");
            return document;

        } catch (Exception e) {
            log.error("Error processing MyTask API Guide", e);
            throw new RuntimeException("Failed to process document", e);
        }
    }

    /**
     * Parse markdown document and extract metadata
     */
    private EmbeddingDocument parseMarkdownDocument(String content, String filePath) {
        EmbeddingDocument doc = EmbeddingDocument.builder()
                .id(UUID.randomUUID().toString())
                .originalContent(content)
                .content(cleanContentForEmbedding(content))
                .title("AI Agent - MyTask API Integration Guide")
                .fileName("AI_AGENT_MYTASK_API_GUIDE.md")
                .filePath(filePath)
                .fileExtension(".md")
                .sourceType("MARKDOWN_FILE")
                .category("API_DOCUMENTATION")
                .subCategory("AI_AGENT_INTEGRATION")
                .language("en")
                .contentType("TECHNICAL_GUIDE")
                .accessLevel("AGENT_KNOWLEDGE")
                .mimeType("text/markdown")
                .encoding("UTF-8")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .processingStatus("PROCESSING")
                .build();

        // Extract markdown-specific metadata
        extractMarkdownMetadata(doc, content);

        // Calculate content statistics
        calculateContentStatistics(doc, content);

        return doc;
    }

    /**
     * Extract headers, links, and other markdown elements
     */
    private void extractMarkdownMetadata(EmbeddingDocument doc, String content) {
        // Extract headers
        List<String> headers = new ArrayList<>();
        Matcher headerMatcher = HEADER_PATTERN.matcher(content);
        while (headerMatcher.find()) {
            headers.add(headerMatcher.group(1));
        }
        doc.setMarkdownHeaders(headers);

        // Extract links
        List<String> links = new ArrayList<>();
        Matcher linkMatcher = LINK_PATTERN.matcher(content);
        while (linkMatcher.find()) {
            links.add(linkMatcher.group(2));
        }
        doc.setMarkdownLinks(links);

        // Generate table of contents from headers
        if (!headers.isEmpty()) {
            String toc = generateTableOfContents(headers);
            doc.setMarkdownTableOfContents(toc);
        }

        // Extract metadata from content
        Map<String, String> metadata = new HashMap<>();
        metadata.put("document_type", "API_INTEGRATION_GUIDE");
        metadata.put("target_audience", "AI_AGENTS");
        metadata.put("api_category", "MYTASK");
        metadata.put("integration_type", "RAG_KNOWLEDGE_BASE");
        doc.setMarkdownMetadata(metadata);
    }

    /**
     * Calculate word count, character count, and readability
     */
    private void calculateContentStatistics(EmbeddingDocument doc, String content) {
        String cleanText = content.replaceAll("```[\\s\\S]*?```", "") // Remove code blocks
                                .replaceAll("#", "")                    // Remove header markers
                                .replaceAll("\\*", "")                  // Remove emphasis markers
                                .trim();

        doc.setWordCount(cleanText.split("\\s+").length);
        doc.setCharacterCount(cleanText.length());
        doc.setFileSize((long) content.getBytes().length);

        // Simple readability score (0-100, higher is more readable)
        double avgWordsPerSentence = calculateAverageWordsPerSentence(cleanText);
        double readabilityScore = Math.max(0, 100 - (avgWordsPerSentence * 2));
        doc.setReadabilityScore(readabilityScore);
    }

    /**
     * Enhance document with AI-generated content
     */
    private void enhanceDocumentWithAI(EmbeddingDocument doc) {
        try {
            // AI-extracted keywords for MyTask API Guide
            doc.setAiExtractedKeywords("MyTask API, task management, AI agent integration, REST endpoints, CRUD operations, validation rules, priority levels, deadline management, user authentication, RAG system, Pinecone vector database");

            // AI-generated summary
            doc.setAiContentSummary("Comprehensive guide for AI agents to interact with the MyTask API system. Covers task creation, retrieval, updates, and deletion with intelligent defaults and validation rules. Includes endpoint specifications, error handling, and best practices for natural language processing integration.");

            // AI-identified topics
            List<String> topics = Arrays.asList(
                "task_management", "api_endpoints", "ai_agent", "validation_rules",
                "error_handling", "user_interaction", "smart_defaults", "priority_system",
                "deadline_management", "authentication", "rag_integration", "natural_language_processing"
            );
            doc.setAiTopics(topics);

            // AI recommendations
            doc.setAiRecommendedActions("Use this guide when users request task creation, updates, or management. Apply smart defaults for missing fields. Always validate required fields before API calls. Provide helpful error messages and suggestions.");

            // Generate suggested questions this document can answer
            List<String> suggestedQuestions = Arrays.asList(
                "How do I create a new task?",
                "What are the required fields for task creation?",
                "How do I set task priorities?",
                "What happens if I don't provide a deadline?",
                "How do I update an existing task?",
                "What are the available task statuses?",
                "How do I handle validation errors?",
                "Can I upload files with tasks?",
                "How do I delete a task?",
                "What statistics can I get about my tasks?"
            );
            doc.setSuggestedQuestions(suggestedQuestions);

            // Conversation starters for AI agent
            doc.setConversationStarters("I can help you manage your tasks using the MyTask API. I can create, update, view, and organize your tasks with intelligent defaults and validation.");

            // AI insights
            Map<String, Object> insights = new HashMap<>();
            insights.put("api_complexity", "MODERATE");
            insights.put("user_interaction_level", "HIGH");
            insights.put("automation_potential", "EXCELLENT");
            insights.put("integration_difficulty", "LOW");
            doc.setAiInsights(insights);

            // Set relevance score for general task management queries
            doc.setAiRelevanceScore(0.95);

            log.debug("Enhanced document with AI-generated metadata");

        } catch (Exception e) {
            log.warn("Failed to enhance document with AI content", e);
        }
    }

    /**
     * Generate embedding for the document
     */
    private void generateEmbeddingForDocument(EmbeddingDocument doc) {
        try {
            // Combine title, summary, and key content for embedding
            String textForEmbedding = buildTextForEmbedding(doc);

            double[] embedding = embeddingService.generateEmbedding(textForEmbedding);
            doc.setEmbedding(embedding);

            log.debug("Generated embedding vector with {} dimensions", embedding.length);

        } catch (Exception e) {
            log.error("Failed to generate embedding for document", e);
            throw new RuntimeException("Embedding generation failed", e);
        }
    }

    /**
     * Index document in Pinecone vector database
     */
    private void indexDocumentInPinecone(EmbeddingDocument doc) {
        try {
            // Use "general" namespace for API documentation
            String namespace = "general";

            // Prepare metadata for Pinecone
            Map<String, Object> metadata = buildPineconeMetadata(doc);

            // Index the document
            boolean success = pineconeRetriever.upsertVector(doc.getId(), doc.getEmbedding(), metadata, namespace);

            if (success) {
                doc.setProcessingStatus("COMPLETED");
                doc.setProcessingCompleted(LocalDateTime.now());
                doc.setLastIndexed(LocalDateTime.now());
                log.info("Successfully indexed document in Pinecone: {}", doc.getId());
            } else {
                throw new RuntimeException("Failed to index document in Pinecone");
            }

        } catch (Exception e) {
            doc.setProcessingStatus("FAILED");
            doc.setProcessingError(e.getMessage());
            log.error("Failed to index document in Pinecone", e);
            throw e;
        }
    }

    /**
     * Clean content for optimal embedding generation
     */
    private String cleanContentForEmbedding(String content) {
        return content
                // Remove file path comments
                .replaceAll("// filepath:.*", "")
                // Clean up excessive whitespace
                .replaceAll("\\n{3,}", "\n\n")
                // Remove markdown syntax while keeping content
                .replaceAll("```\\w*\\n", "")
                .replaceAll("```", "")
                .replaceAll("#{1,6}\\s+", "")
                .replaceAll("\\*\\*([^\\*]+)\\*\\*", "$1")
                .replaceAll("\\*([^\\*]+)\\*", "$1")
                .trim();
    }

    /**
     * Build optimized text for embedding generation
     */
    private String buildTextForEmbedding(EmbeddingDocument doc) {
        StringBuilder text = new StringBuilder();

        if (doc.getTitle() != null) {
            text.append(doc.getTitle()).append(". ");
        }

        if (doc.getAiContentSummary() != null) {
            text.append(doc.getAiContentSummary()).append(". ");
        }

        if (doc.getAiExtractedKeywords() != null) {
            text.append("Keywords: ").append(doc.getAiExtractedKeywords()).append(". ");
        }

        if (doc.getContent() != null) {
            // Use first 1000 characters of content
            String contentSnippet = doc.getContent().length() > 1000 ?
                doc.getContent().substring(0, 1000) + "..." : doc.getContent();
            text.append(contentSnippet);
        }

        return text.toString();
    }

    /**
     * Build metadata for Pinecone indexing
     */
    private Map<String, Object> buildPineconeMetadata(EmbeddingDocument doc) {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("title", doc.getTitle());
        metadata.put("category", doc.getCategory());
        metadata.put("source_type", doc.getSourceType());
        metadata.put("content_type", doc.getContentType());
        metadata.put("language", doc.getLanguage());
        metadata.put("file_name", doc.getFileName());
        metadata.put("access_level", doc.getAccessLevel());
        metadata.put("word_count", doc.getWordCount());
        metadata.put("created_at", doc.getCreatedAt().toString());

        if (doc.getAiTopics() != null) {
            metadata.put("topics", String.join(",", doc.getAiTopics()));
        }

        if (doc.getAiExtractedKeywords() != null) {
            metadata.put("keywords", doc.getAiExtractedKeywords());
        }

        metadata.put("ai_relevance_score", doc.getAiRelevanceScore());

        return metadata;
    }

    /**
     * Generate table of contents from headers
     */
    private String generateTableOfContents(List<String> headers) {
        StringBuilder toc = new StringBuilder("Table of Contents:\n");

        for (int i = 0; i < Math.min(headers.size(), 10); i++) {
            toc.append("- ").append(headers.get(i)).append("\n");
        }

        return toc.toString();
    }

    /**
     * Calculate average words per sentence for readability
     */
    private double calculateAverageWordsPerSentence(String text) {
        String[] sentences = text.split("[.!?]+");
        if (sentences.length == 0) return 0;

        int totalWords = 0;
        for (String sentence : sentences) {
            totalWords += sentence.trim().split("\\s+").length;
        }

        return (double) totalWords / sentences.length;
    }
}
