package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.document.EmbeddingDocument;
import com.example.taskmanagement_backend.agent.service.UserContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced Document Processor - Xử lý file text/markdown cho AI Agent RAG
 * Tích hợp với Gemini AI để phân tích và enhance documents
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedDocumentProcessor {

    private final EmbeddingService embeddingService;
    private final UserContextService userContextService;
    private final CoreAgentService coreAgentService;

    // Markdown patterns
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^\\)]+)\\)");
    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("^---\\n(.*?)\\n---\\n", Pattern.DOTALL);

    /**
     * Process uploaded text/markdown file cho AI Agent
     */
    public EmbeddingDocument processUploadedFile(MultipartFile file, Long userId, Long projectId, String accessLevel) {
        try {
            log.info("Processing uploaded file: {} for user: {}, project: {}", file.getOriginalFilename(), userId, projectId);

            // Validate file
            validateFile(file);

            // Extract content
            String content = extractContent(file);
            String originalContent = content;

            // Get user context cho personalization
            UserContextService.UserChatContext userContext = userContextService.getUserChatContext(userId);

            // Create base document
            EmbeddingDocument document = EmbeddingDocument.builder()
                .id(UUID.randomUUID().toString())
                .originalContent(originalContent)
                .content(content)
                .fileName(file.getOriginalFilename())
                .fileExtension(getFileExtension(file.getOriginalFilename()))
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .encoding("UTF-8")
                .userId(userId)
                .projectId(projectId)
                .organizationId(userContext.getOrganizationId())
                .accessLevel(accessLevel != null ? accessLevel : "PRIVATE")
                .sourceType(getSourceType(file.getOriginalFilename()))
                .processingStatus("PROCESSING")
                .processingStarted(LocalDateTime.now())
                .processingAttempts(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            // Process based on file type
            if (isMarkdownFile(file.getOriginalFilename())) {
                processMarkdownDocument(document);
            } else {
                processTextDocument(document);
            }

            // AI Enhancement với Gemini
            enhanceWithAI(document, userContext);

            // Generate embeddings
            generateEmbeddings(document);

            // Finalize processing
            document.setProcessingStatus("COMPLETED");
            document.setProcessingCompleted(LocalDateTime.now());

            log.info("Successfully processed document: {} with {} words", document.getFileName(), document.getWordCount());

            return document;

        } catch (Exception e) {
            log.error("Error processing file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to process document: " + e.getMessage());
        }
    }

    /**
     * Process existing text content cho AI Agent
     */
    public EmbeddingDocument processTextContent(String content, String title, Long userId, Long projectId, String sourceType) {
        try {
            log.info("Processing text content for user: {}, project: {}", userId, projectId);

            UserContextService.UserChatContext userContext = userContextService.getUserChatContext(userId);

            EmbeddingDocument document = EmbeddingDocument.builder()
                .id(UUID.randomUUID().toString())
                .originalContent(content)
                .content(content)
                .title(title)
                .sourceType(sourceType != null ? sourceType : "TEXT_CONTENT")
                .userId(userId)
                .projectId(projectId)
                .organizationId(userContext.getOrganizationId())
                .accessLevel("PRIVATE")
                .processingStatus("PROCESSING")
                .processingStarted(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

            // Analyze content
            analyzeTextContent(document);

            // AI Enhancement
            enhanceWithAI(document, userContext);

            // Generate embeddings
            generateEmbeddings(document);

            document.setProcessingStatus("COMPLETED");
            document.setProcessingCompleted(LocalDateTime.now());

            return document;

        } catch (Exception e) {
            log.error("Error processing text content", e);
            throw new RuntimeException("Failed to process text content: " + e.getMessage());
        }
    }

    /**
     * Process markdown document với advanced parsing
     */
    private void processMarkdownDocument(EmbeddingDocument document) {
        String content = document.getContent();

        // Extract front matter
        Map<String, String> frontMatter = extractFrontMatter(content);
        document.setMarkdownMetadata(frontMatter);

        // Remove front matter from content
        content = removeFrontMatter(content);
        document.setContent(content);

        // Extract title from front matter hoặc first header
        if (document.getTitle() == null) {
            String title = frontMatter.get("title");
            if (title == null) {
                title = extractFirstHeader(content);
            }
            document.setTitle(title);
        }

        // Extract headers
        List<String> headers = extractHeaders(content);
        document.setMarkdownHeaders(headers);

        // Generate table of contents
        String toc = generateTableOfContents(headers);
        document.setMarkdownTableOfContents(toc);

        // Extract links
        List<String> links = extractLinks(content);
        document.setMarkdownLinks(links);

        // Extract images
        List<String> images = extractImages(content);
        document.setMarkdownImages(images);

        // Analyze content
        analyzeTextContent(document);

        // Set category based on content
        document.setCategory(determineMarkdownCategory(content, frontMatter));
    }

    /**
     * Process plain text document
     */
    private void processTextDocument(EmbeddingDocument document) {
        String content = document.getContent();

        // Extract title from first line if not set
        if (document.getTitle() == null) {
            String title = extractTitleFromText(content);
            document.setTitle(title);
        }

        // Analyze content
        analyzeTextContent(document);

        // Set category
        document.setCategory(determineTextCategory(content));
    }

    /**
     * Analyze text content cho basic metrics
     */
    private void analyzeTextContent(EmbeddingDocument document) {
        String content = document.getContent();

        if (content == null || content.isEmpty()) {
            return;
        }

        // Word count
        String[] words = content.trim().split("\\s+");
        document.setWordCount(words.length);

        // Character count
        document.setCharacterCount(content.length());

        // Detect language
        String language = detectLanguage(content);
        document.setLanguage(language);

        // Extract basic keywords
        List<String> keywords = extractBasicKeywords(content);
        document.setSearchKeywords(keywords);

        // Create searchable text
        String searchableText = createSearchableText(document);
        document.setSearchableText(searchableText);
    }

    /**
     * Enhance document với AI (Gemini)
     */
    private void enhanceWithAI(EmbeddingDocument document, UserContextService.UserChatContext userContext) {
        try {
            log.debug("Enhancing document with AI: {}", document.getTitle());

            // Build prompt cho AI analysis
            String analysisPrompt = buildAnalysisPrompt(document, userContext);

            // Call Gemini cho analysis (simplified - trong thực tế sẽ integrate với CoreAgentService)
            Map<String, Object> aiAnalysis = performAIAnalysis(analysisPrompt);

            // Extract AI insights
            document.setAiContentSummary((String) aiAnalysis.get("summary"));
            document.setAiExtractedKeywords((String) aiAnalysis.get("keywords"));
            document.setAiTopics((List<String>) aiAnalysis.get("topics"));
            document.setAiRecommendedActions((String) aiAnalysis.get("recommendations"));
            document.setContentType((String) aiAnalysis.get("contentType"));
            document.setAiInsights(aiAnalysis);

            // Generate suggested questions
            List<String> questions = generateSuggestedQuestions(document);
            document.setSuggestedQuestions(questions);

            // Create conversation starters
            String starters = createConversationStarters(document, userContext);
            document.setConversationStarters(starters);

            // Personalize for different user types
            Map<String, String> personalization = createPersonalization(document);
            document.setAiPersonalization(personalization);

        } catch (Exception e) {
            log.error("Error enhancing document with AI", e);
            // Set fallback values
            document.setAiContentSummary("AI analysis unavailable - " +
                (document.getContent().length() > 200 ?
                    document.getContent().substring(0, 200) + "..." :
                    document.getContent()));
        }
    }

    /**
     * Generate embeddings for document
     */
    private void generateEmbeddings(EmbeddingDocument document) {
        try {
            String textToEmbed = prepareTextForEmbedding(document);
            double[] embeddings = embeddingService.generateEmbedding(textToEmbed);
            document.setEmbedding(embeddings);
            document.setLastIndexed(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error generating embeddings for document", e);
            throw new RuntimeException("Failed to generate embeddings: " + e.getMessage());
        }
    }

    // === HELPER METHODS ===

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > 10 * 1024 * 1024) { // 10MB limit
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".txt") && !filename.endsWith(".md") && !filename.endsWith(".markdown"))) {
            throw new IllegalArgumentException("Only .txt, .md, and .markdown files are supported");
        }
    }

    private String extractContent(MultipartFile file) throws IOException {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    private String getFileExtension(String filename) {
        if (filename == null) return null;
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : null;
    }

    private String getSourceType(String filename) {
        String extension = getFileExtension(filename);
        if (".md".equals(extension) || ".markdown".equals(extension)) {
            return "MARKDOWN_FILE";
        } else if (".txt".equals(extension)) {
            return "TEXT_FILE";
        }
        return "DOCUMENT";
    }

    private boolean isMarkdownFile(String filename) {
        String extension = getFileExtension(filename);
        return ".md".equals(extension) || ".markdown".equals(extension);
    }

    private Map<String, String> extractFrontMatter(String content) {
        Map<String, String> frontMatter = new HashMap<>();
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content);

        if (matcher.find()) {
            String yamlContent = matcher.group(1);
            String[] lines = yamlContent.split("\n");

            for (String line : lines) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    frontMatter.put(parts[0].trim(), parts[1].trim().replaceAll("\"", ""));
                }
            }
        }

        return frontMatter;
    }

    private String removeFrontMatter(String content) {
        return FRONT_MATTER_PATTERN.matcher(content).replaceFirst("");
    }

    private List<String> extractHeaders(String content) {
        List<String> headers = new ArrayList<>();
        Matcher matcher = HEADER_PATTERN.matcher(content);

        while (matcher.find()) {
            String level = matcher.group(1);
            String text = matcher.group(2);
            headers.add(level + " " + text);
        }

        return headers;
    }

    private String extractFirstHeader(String content) {
        Matcher matcher = HEADER_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }

    private String generateTableOfContents(List<String> headers) {
        StringBuilder toc = new StringBuilder();
        for (String header : headers) {
            String[] parts = header.split(" ", 2);
            String level = parts[0];
            String text = parts.length > 1 ? parts[1] : "";

            String indent = "  ".repeat(level.length() - 1);
            toc.append(indent).append("- ").append(text).append("\n");
        }
        return toc.toString();
    }

    private List<String> extractLinks(String content) {
        List<String> links = new ArrayList<>();
        Matcher matcher = LINK_PATTERN.matcher(content);

        while (matcher.find()) {
            links.add(matcher.group(2)); // URL
        }

        return links;
    }

    private List<String> extractImages(String content) {
        List<String> images = new ArrayList<>();
        Matcher matcher = IMAGE_PATTERN.matcher(content);

        while (matcher.find()) {
            images.add(matcher.group(2)); // Image URL
        }

        return images;
    }

    private String extractTitleFromText(String content) {
        String[] lines = content.split("\n");
        if (lines.length > 0) {
            String firstLine = lines[0].trim();
            return firstLine.length() > 100 ? firstLine.substring(0, 100) + "..." : firstLine;
        }
        return "Untitled Document";
    }

    private String detectLanguage(String content) {
        // Simple language detection
        if (content.matches(".*[àáảãạăắằẳẵặâấầẩẫậêếềểễệôốồổỗộơớờởỡợuúùủũụưứừửữựiíìỉĩịyýỳỷỹỵ].*")) {
            return "vi";
        }
        return "en";
    }

    private List<String> extractBasicKeywords(String content) {
        // Basic keyword extraction
        String[] words = content.toLowerCase()
            .replaceAll("[^a-zA-Z0-9\\s]", "")
            .split("\\s+");

        Map<String, Integer> wordCount = new HashMap<>();
        for (String word : words) {
            if (word.length() > 3) {
                wordCount.merge(word, 1, Integer::sum);
            }
        }

        return wordCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .toList();
    }

    private String createSearchableText(EmbeddingDocument document) {
        StringBuilder searchable = new StringBuilder();

        if (document.getTitle() != null) {
            searchable.append(document.getTitle()).append(" ");
        }

        if (document.getSearchKeywords() != null) {
            searchable.append(String.join(" ", document.getSearchKeywords())).append(" ");
        }

        if (document.getContent() != null) {
            searchable.append(document.getContent());
        }

        return searchable.toString();
    }

    private String determineMarkdownCategory(String content, Map<String, String> frontMatter) {
        String category = frontMatter.get("category");
        if (category != null) return category;

        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("api") || lowerContent.contains("endpoint")) {
            return "API_DOCUMENTATION";
        } else if (lowerContent.contains("guide") || lowerContent.contains("tutorial")) {
            return "GUIDE";
        } else if (lowerContent.contains("readme")) {
            return "README";
        }

        return "DOCUMENTATION";
    }

    private String determineTextCategory(String content) {
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("note") || lowerContent.contains("ghi chú")) {
            return "NOTE";
        } else if (lowerContent.contains("log") || lowerContent.contains("history")) {
            return "LOG";
        }

        return "TEXT_DOCUMENT";
    }

    private String buildAnalysisPrompt(EmbeddingDocument document, UserContextService.UserChatContext userContext) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Analyze this document for AI Agent integration:\n\n");
        prompt.append("Document Type: ").append(document.getSourceType()).append("\n");
        prompt.append("File: ").append(document.getFileName()).append("\n");
        prompt.append("User Role: ").append(userContext.getSystemRole()).append("\n");
        prompt.append("Organization: ").append(userContext.getOrganizationName()).append("\n\n");

        prompt.append("Content to analyze:\n");
        prompt.append(document.getContent().length() > 3000 ?
            document.getContent().substring(0, 3000) + "..." :
            document.getContent());

        prompt.append("\n\nProvide JSON response with:");
        prompt.append("\n- summary: concise summary");
        prompt.append("\n- keywords: important keywords");
        prompt.append("\n- topics: main topics covered");
        prompt.append("\n- recommendations: suggested actions");
        prompt.append("\n- contentType: TECHNICAL/BUSINESS/GUIDE/etc");

        return prompt.toString();
    }

    private Map<String, Object> performAIAnalysis(String prompt) {
        // Placeholder - trong thực tế sẽ integrate với Gemini API
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("summary", "Document analysis completed");
        analysis.put("keywords", "document, analysis, content");
        analysis.put("topics", Arrays.asList("documentation", "content analysis"));
        analysis.put("recommendations", "Review and update regularly");
        analysis.put("contentType", "DOCUMENTATION");

        return analysis;
    }

    private List<String> generateSuggestedQuestions(EmbeddingDocument document) {
        List<String> questions = new ArrayList<>();
        questions.add("What is this document about?");
        questions.add("Can you summarize the main points?");
        questions.add("What are the key takeaways?");

        return questions;
    }

    private String createConversationStarters(EmbeddingDocument document, UserContextService.UserChatContext userContext) {
        return String.format("I found a %s titled '%s' that might help answer your question. Would you like me to explain its contents?",
            document.getDocumentTypeForAI(), document.getTitle());
    }

    private Map<String, String> createPersonalization(EmbeddingDocument document) {
        Map<String, String> personalization = new HashMap<>();
        personalization.put("ADMIN", "This document contains administrative information relevant to system management.");
        personalization.put("MEMBER", "This document provides information that may be useful for your daily tasks.");

        return personalization;
    }

    private String prepareTextForEmbedding(EmbeddingDocument document) {
        StringBuilder text = new StringBuilder();

        if (document.getTitle() != null) {
            text.append(document.getTitle()).append(". ");
        }

        if (document.getAiContentSummary() != null) {
            text.append(document.getAiContentSummary()).append(". ");
        }

        if (document.getContent() != null) {
            text.append(document.getContent());
        }

        return text.toString();
    }
}
