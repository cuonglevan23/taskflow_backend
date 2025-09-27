package com.example.taskmanagement_backend.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Pinecone Service for Vector Database Operations
 * Uses new host-based connection instead of deprecated environment
 */
@Slf4j
@Service
public class PineconeService {

    private final WebClient pineconeWebClient;
    private final String indexName;
    private final boolean isConfigured;

    public PineconeService(
            @Qualifier("pineconeWebClient") WebClient pineconeWebClient,
            @Qualifier("pineconeIndexName") String indexName,
            @Qualifier("pineconeConfigured") boolean isConfigured
    ) {
        this.pineconeWebClient = pineconeWebClient;
        this.indexName = indexName;
        this.isConfigured = isConfigured;

        if (isConfigured) {
            log.info("🚀 PineconeService initialized with index: {}", indexName);
        } else {
            log.warn("⚠️ PineconeService disabled - configuration incomplete");
        }
    }

    /**
     * Upsert vectors to Pinecone index
     * Example: Store document embeddings for RAG
     */
    public Mono<Map<String, Object>> upsertVectors(List<Map<String, Object>> vectors) {
        if (!isConfigured) {
            log.warn("Pinecone not configured - skipping upsert operation");
            return Mono.just(Map.of("success", false, "error", "Pinecone not configured"));
        }

        try {
            Map<String, Object> requestBody = Map.of(
                "vectors", vectors,
                "namespace", "" // Use default namespace or customize as needed
            );

            return pineconeWebClient.post()
                .uri("/vectors/upsert")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedResponse = (Map<String, Object>) response;
                    return typedResponse;
                })
                .doOnSuccess(response -> log.info("✅ Successfully upserted {} vectors to Pinecone", vectors.size()))
                .doOnError(error -> log.error("❌ Failed to upsert vectors to Pinecone: {}", error.getMessage()));

        } catch (Exception e) {
            log.error("Error upserting vectors to Pinecone", e);
            return Mono.just(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Query vectors from Pinecone index
     * Example: Find similar documents for RAG context
     */
    public Mono<Map<String, Object>> queryVectors(List<Double> queryVector, int topK, Map<String, Object> filter) {
        if (!isConfigured) {
            log.warn("Pinecone not configured - skipping query operation");
            return Mono.just(Map.of("matches", List.of()));
        }

        try {
            Map<String, Object> requestBody = Map.of(
                "vector", queryVector,
                "topK", topK,
                "includeMetadata", true,
                "includeValues", false,
                "namespace", "" // Use default namespace
            );

            // Add filter if provided
            if (filter != null && !filter.isEmpty()) {
                requestBody = Map.of(
                    "vector", queryVector,
                    "topK", topK,
                    "includeMetadata", true,
                    "includeValues", false,
                    "filter", filter,
                    "namespace", ""
                );
            }

            return pineconeWebClient.post()
                .uri("/query")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedResponse = (Map<String, Object>) response;
                    return typedResponse;
                })
                .doOnSuccess(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> matches = (List<Map<String, Object>>) response.get("matches");
                    log.info("✅ Found {} similar vectors in Pinecone", matches != null ? matches.size() : 0);
                })
                .doOnError(error -> log.error("❌ Failed to query vectors from Pinecone: {}", error.getMessage()));

        } catch (Exception e) {
            log.error("Error querying vectors from Pinecone", e);
            return Mono.just(Map.of("matches", List.of()));
        }
    }

    /**
     * Delete vectors from Pinecone index
     */
    public Mono<Map<String, Object>> deleteVectors(List<String> vectorIds) {
        if (!isConfigured) {
            log.warn("Pinecone not configured - skipping delete operation");
            return Mono.just(Map.of("success", false, "error", "Pinecone not configured"));
        }

        try {
            Map<String, Object> requestBody = Map.of(
                "ids", vectorIds,
                "namespace", ""
            );

            return pineconeWebClient.post()
                .uri("/vectors/delete")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedResponse = (Map<String, Object>) response;
                    return typedResponse;
                })
                .doOnSuccess(response -> log.info("✅ Successfully deleted {} vectors from Pinecone", vectorIds.size()))
                .doOnError(error -> log.error("❌ Failed to delete vectors from Pinecone: {}", error.getMessage()));

        } catch (Exception e) {
            log.error("Error deleting vectors from Pinecone", e);
            return Mono.just(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get index statistics
     */
    public Mono<Map<String, Object>> getIndexStats() {
        if (!isConfigured) {
            log.warn("Pinecone not configured - skipping stats operation");
            return Mono.just(Map.of("error", "Pinecone not configured"));
        }

        try {
            return pineconeWebClient.post()
                .uri("/describe_index_stats")
                .bodyValue(Map.of("filter", Map.of())) // Empty filter to get all stats
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedResponse = (Map<String, Object>) response;
                    return typedResponse;
                })
                .doOnSuccess(response -> log.info("✅ Retrieved Pinecone index statistics"))
                .doOnError(error -> log.error("❌ Failed to get Pinecone index stats: {}", error.getMessage()));

        } catch (Exception e) {
            log.error("Error getting Pinecone index stats", e);
            return Mono.just(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Health check for Pinecone connection
     */
    public boolean isHealthy() {
        if (!isConfigured) {
            return false;
        }

        try {
            // Try to get index stats as a health check
            Map<String, Object> stats = getIndexStats().block();
            return stats != null && !stats.containsKey("error");
        } catch (Exception e) {
            log.error("Pinecone health check failed", e);
            return false;
        }
    }
}
