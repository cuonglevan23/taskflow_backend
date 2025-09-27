package com.example.taskmanagement_backend.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * AI Agent Configuration - UNIFIED RAG PIPELINE CONFIG
 * Cấu hình cho Gemini API, Pinecone, Embedding API và toàn bộ RAG pipeline
 */
@Configuration
public class AIAgentConfig {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    // Pinecone configuration (legacy)
    @Value("${pinecone.api.key:}")
    private String legacyPineconeApiKey;

    @Value("${pinecone.environment:}")
    private String pineconeEnvironment;

    // RAG Pipeline configuration
    @Value("${ai.pinecone.host:}")
    private String pineconeHost;

    @Value("${ai.pinecone.api.key:}")
    private String pineconeApiKey;

    @Value("${ai.pinecone.index.name:taskflow-documents}")
    private String pineconeIndexName;

    @Value("${ai.embedding.api.timeout:30}")
    private int embeddingApiTimeout;

    @Value("${ai.pinecone.api.timeout:15}")
    private int pineconeApiTimeout;

    /**
     * WebClient cho Gemini API calls
     */
    @Bean("geminiWebClient")
    public WebClient geminiWebClient() {
        return WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
            .build();
    }

    /**
     * WebClient for embedding service (llama-text-embed-v2)
     */
    @Bean("embeddingWebClient")
    public WebClient embeddingWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer
            .build();
    }

    /**
     * WebClient cho Pinecone API calls - RAG Enhanced
     */
    @Bean("pineconeWebClient")
    public WebClient pineconeWebClient() {
        // Use new RAG config if available, fallback to legacy
        String actualApiKey = !pineconeApiKey.isEmpty() ? pineconeApiKey : legacyPineconeApiKey;

        WebClient.Builder builder = WebClient.builder()
            .defaultHeader("Api-Key", actualApiKey)
            .defaultHeader("Content-Type", "application/json")
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)); // 50MB for vectors

        // Set base URL if pineconeHost is provided (new RAG config)
        if (!pineconeHost.isEmpty()) {
            // Remove https:// prefix if present to avoid duplicate
            String cleanHost = pineconeHost.replace("https://", "");
            builder.baseUrl("https://" + cleanHost);
        }

        return builder.build();
    }

    /**
     * Pinecone index name for taskflow documents
     */
    @Bean("pineconeIndexName")
    public String pineconeIndexName() {
        return pineconeIndexName;
    }

    /**
     * Pinecone configuration status - Enhanced for RAG
     */
    @Bean("pineconeConfigured")
    public boolean pineconeConfigured() {
        boolean newConfigValid = !pineconeHost.isEmpty() && !pineconeApiKey.isEmpty();
        boolean legacyConfigValid = !legacyPineconeApiKey.isEmpty() && !pineconeEnvironment.isEmpty();
        return newConfigValid || legacyConfigValid;
    }

    /**
     * Validate configuration on startup - Enhanced for RAG
     */
    @Bean
    public AIConfigValidator aiConfigValidator() {
        return new AIConfigValidator(geminiApiKey, pineconeApiKey, legacyPineconeApiKey,
                                   pineconeHost, pineconeEnvironment);
    }

    /**
     * Configuration validator - Enhanced for RAG Pipeline
     */
    public static class AIConfigValidator {
        public AIConfigValidator(String geminiApiKey, String pineconeApiKey, String legacyPineconeApiKey,
                               String pineconeHost, String pineconeEnvironment) {

            // Gemini validation
            if (geminiApiKey == null || geminiApiKey.isEmpty()) {
                System.out.println("⚠️  WARNING: Gemini API key not configured - using mock responses");
            } else {
                System.out.println("✅ Gemini API configured");
            }

            // RAG Pipeline validation
            boolean newPineconeConfig = pineconeHost != null && !pineconeHost.isEmpty() &&
                                      pineconeApiKey != null && !pineconeApiKey.isEmpty();
            boolean legacyPineconeConfig = legacyPineconeApiKey != null && !legacyPineconeApiKey.isEmpty() &&
                                         pineconeEnvironment != null && !pineconeEnvironment.isEmpty();

            if (newPineconeConfig) {
                System.out.println("✅ RAG Pipeline: New Pinecone configuration detected");
                System.out.println("🚀 Vector database: " + pineconeHost);
            } else if (legacyPineconeConfig) {
                System.out.println("✅ RAG Pipeline: Legacy Pinecone configuration detected");
                System.out.println("🚀 Vector database: " + pineconeEnvironment);
            } else {
                System.out.println("⚠️  WARNING: Pinecone not configured - using in-memory vector search");
            }

            System.out.println("🧠 AI Agent + RAG Pipeline configuration validated");
        }
    }
}
