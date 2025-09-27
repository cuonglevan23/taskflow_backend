package com.example.taskmanagement_backend.agent.retriever;

import com.example.taskmanagement_backend.agent.document.EmbeddingDocument;
import com.example.taskmanagement_backend.agent.exception.AgentException;
import com.example.taskmanagement_backend.agent.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * Pinecone Retriever - Vector DB integration với Pinecone
 * Hỗ trợ namespace riêng cho từng project/agent và metadata
 */
@Slf4j
@Component
public class PineconeRetriever {

    private final WebClient webClient;
    private final EmbeddingService embeddingService;

    @Value("${pinecone.api.key:}")
    private String pineconeApiKey;

    @Value("${pinecone.environment:}")
    private String pineconeEnvironment;

    @Value("${pinecone.index.name:taskflow-agent}")
    private String indexName;

    @Value("${pinecone.host:}")
    private String pineconeHost;

    public PineconeRetriever(@Qualifier("pineconeWebClient") WebClient webClient,
                            EmbeddingService embeddingService) {
        this.webClient = webClient;
        this.embeddingService = embeddingService;
    }

    /**
     * Query vector với top-K context cho RAG
     */
    public List<EmbeddingDocument> queryVector(String query, String namespace, int topK) {
        try {
            log.debug("Querying Pinecone: query={}, namespace={}, topK={}", query, namespace, topK);

            // Tạo embedding cho query
            double[] queryVector = embeddingService.generateEmbedding(query);

            // Prepare Pinecone query request
            Map<String, Object> queryRequest = Map.of(
                "vector", queryVector,
                "topK", topK,
                "includeMetadata", true,
                "includeValues", false,
                "namespace", namespace != null ? namespace : "default"
            );

            Map<String, Object> requestBody = Map.of("queries", List.of(queryRequest));

            // Call Pinecone API
            Map<String, Object> response = webClient.post()
                .uri(getPineconeUrl() + "/query")
                .header("Api-Key", pineconeApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            return parseQueryResponse(response);

        } catch (Exception e) {
            log.error("Error querying Pinecone", e);
            // Fallback to mock data for development
            return getMockResults(query, topK);
        }
    }

    /**
     * Upsert document với metadata vào Pinecone
     */
    public void upsertDocument(EmbeddingDocument document, String namespace) {
        try {
            log.info("Upserting document to Pinecone: id={}, namespace={}", document.getId(), namespace);

            // Tạo embedding nếu chưa có
            if (document.getEmbedding() == null || document.getEmbedding().length == 0) {
                double[] embedding = embeddingService.generateEmbedding(document.getContent());
                document.setEmbedding(embedding);
            }

            // Prepare metadata
            Map<String, Object> metadata = Map.of(
                "doc_id", document.getId(),
                "source", document.getSourceType(),
                "category", document.getCategory(),
                "language", document.getLanguage(),
                "content", document.getContent().substring(0, Math.min(1000, document.getContent().length())), // Truncate for storage
                "source_id", document.getSourceId() != null ? document.getSourceId() : ""
            );

            // Prepare vector for upsert
            Map<String, Object> vector = Map.of(
                "id", document.getId(),
                "values", document.getEmbedding(),
                "metadata", metadata
            );

            Map<String, Object> upsertRequest = Map.of(
                "vectors", List.of(vector),
                "namespace", namespace != null ? namespace : "default"
            );

            // Call Pinecone upsert API
            webClient.post()
                .uri(getPineconeUrl() + "/vectors/upsert")
                .header("Api-Key", pineconeApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(upsertRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            log.info("Successfully upserted document: {}", document.getId());

        } catch (Exception e) {
            log.error("Error upserting document to Pinecone", e);
            throw new AgentException.RAGException("Failed to upsert document: " + e.getMessage());
        }
    }

    /**
     * Batch upsert multiple documents
     */
    public void batchUpsertDocuments(List<EmbeddingDocument> documents, String namespace) {
        try {
            log.info("Batch upserting {} documents to namespace: {}", documents.size(), namespace);

            List<Map<String, Object>> vectors = new ArrayList<>();

            for (EmbeddingDocument doc : documents) {
                // Tạo embedding nếu chưa có
                if (doc.getEmbedding() == null || doc.getEmbedding().length == 0) {
                    double[] embedding = embeddingService.generateEmbedding(doc.getContent());
                    doc.setEmbedding(embedding);
                }

                Map<String, Object> metadata = Map.of(
                    "doc_id", doc.getId(),
                    "source", doc.getSourceType(),
                    "category", doc.getCategory(),
                    "language", doc.getLanguage(),
                    "content", doc.getContent().substring(0, Math.min(1000, doc.getContent().length())),
                    "source_id", doc.getSourceId() != null ? doc.getSourceId() : ""
                );

                Map<String, Object> vector = Map.of(
                    "id", doc.getId(),
                    "values", doc.getEmbedding(),
                    "metadata", metadata
                );
                vectors.add(vector);
            }

            Map<String, Object> upsertRequest = Map.of(
                "vectors", vectors,
                "namespace", namespace != null ? namespace : "default"
            );

            // Call Pinecone upsert API
            webClient.post()
                .uri(getPineconeUrl() + "/vectors/upsert")
                .header("Api-Key", pineconeApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(upsertRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            log.info("Successfully batch upserted {} documents", documents.size());

        } catch (Exception e) {
            log.error("Error batch upserting documents to Pinecone", e);
            throw new AgentException.RAGException("Failed to batch upsert documents: " + e.getMessage());
        }
    }

    /**
     * Delete document from Pinecone
     */
    public void deleteDocument(String documentId, String namespace) {
        try {
            Map<String, Object> deleteRequest = Map.of(
                "ids", List.of(documentId),
                "namespace", namespace != null ? namespace : "default"
            );

            webClient.post()
                .uri(getPineconeUrl() + "/vectors/delete")
                .header("Api-Key", pineconeApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(deleteRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            log.info("Deleted document from Pinecone: {}", documentId);

        } catch (Exception e) {
            log.error("Error deleting document from Pinecone", e);
            throw new AgentException.RAGException("Failed to delete document: " + e.getMessage());
        }
    }

    /**
     * Query by metadata filters
     */
    public List<EmbeddingDocument> queryByMetadata(String query, String namespace, Map<String, Object> filters, int topK) {
        try {
            double[] queryVector = embeddingService.generateEmbedding(query);

            Map<String, Object> queryRequest = Map.of(
                "vector", queryVector,
                "topK", topK,
                "includeMetadata", true,
                "includeValues", false,
                "namespace", namespace != null ? namespace : "default",
                "filter", filters
            );

            Map<String, Object> requestBody = Map.of("queries", List.of(queryRequest));

            Map<String, Object> response = webClient.post()
                .uri(getPineconeUrl() + "/query")
                .header("Api-Key", pineconeApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            return parseQueryResponse(response);

        } catch (Exception e) {
            log.error("Error querying Pinecone by metadata", e);
            return getMockResults(query, topK);
        }
    }

    /**
     * Get index statistics
     */
    public Map<String, Object> getIndexStats() {
        try {
            Map<String, Object> response = webClient.post()
                .uri(getPineconeUrl() + "/describe_index_stats")
                .header("Api-Key", pineconeApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(Map.of())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            return response != null ? response : Map.of("status", "unavailable");

        } catch (Exception e) {
            log.error("Error getting Pinecone index stats", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /**
     * Parse Pinecone query response
     */
    private List<EmbeddingDocument> parseQueryResponse(Map<String, Object> response) {
        List<EmbeddingDocument> results = new ArrayList<>();

        if (response == null || !response.containsKey("results")) {
            return results;
        }

        List<Map<String, Object>> resultsList = (List<Map<String, Object>>) response.get("results");
        if (resultsList.isEmpty()) {
            return results;
        }

        Map<String, Object> firstResult = resultsList.get(0);
        if (!firstResult.containsKey("matches")) {
            return results;
        }

        List<Map<String, Object>> matches = (List<Map<String, Object>>) firstResult.get("matches");

        for (Map<String, Object> match : matches) {
            try {
                EmbeddingDocument doc = EmbeddingDocument.builder()
                    .id((String) match.get("id"))
                    .relevanceScore(((Number) match.get("score")).doubleValue())
                    .build();

                if (match.containsKey("metadata")) {
                    Map<String, Object> metadata = (Map<String, Object>) match.get("metadata");
                    doc.setContent((String) metadata.get("content"));
                    doc.setSourceType((String) metadata.get("source"));
                    doc.setCategory((String) metadata.get("category"));
                    doc.setLanguage((String) metadata.get("language"));
                    doc.setSourceId((String) metadata.get("source_id"));
                }

                results.add(doc);
            } catch (Exception e) {
                log.warn("Error parsing match result: {}", e.getMessage());
            }
        }

        return results;
    }

    /**
     * Get Pinecone URL
     */
    private String getPineconeUrl() {
        if (pineconeHost != null && !pineconeHost.isEmpty()) {
            return pineconeHost;
        }
        return String.format("https://%s-%s.svc.%s.pinecone.io",
            indexName, "abc123", pineconeEnvironment);
    }

    /**
     * Mock results for development/fallback
     */
    private List<EmbeddingDocument> getMockResults(String query, int topK) {
        List<EmbeddingDocument> mockResults = new ArrayList<>();

        // Add some mock context based on common task management queries
        if (query.toLowerCase().contains("task") || query.toLowerCase().contains("project")) {
            EmbeddingDocument mockDoc = EmbeddingDocument.builder()
                .id("mock-1")
                .content("Task management involves creating, assigning, and tracking tasks within projects to ensure timely completion.")
                .category("general")
                .sourceType("KNOWLEDGE_BASE")
                .language("en")
                .relevanceScore(0.8)
                .build();
            mockResults.add(mockDoc);
        }

        if (query.toLowerCase().contains("team") || query.toLowerCase().contains("collaboration")) {
            EmbeddingDocument mockDoc = EmbeddingDocument.builder()
                .id("mock-2")
                .content("Team collaboration features include real-time chat, file sharing, and progress tracking for better coordination.")
                .category("collaboration")
                .sourceType("KNOWLEDGE_BASE")
                .language("en")
                .relevanceScore(0.7)
                .build();
            mockResults.add(mockDoc);
        }

        return mockResults.subList(0, Math.min(topK, mockResults.size()));
    }

    /**
     * Upsert single vector with metadata - required by DocumentProcessingService
     */
    public boolean upsertVector(String id, double[] embedding, Map<String, Object> metadata, String namespace) {
        try {
            log.debug("Upserting single vector: id={}, namespace={}", id, namespace);

            // Prepare vector for upsert
            Map<String, Object> vector = Map.of(
                "id", id,
                "values", embedding,
                "metadata", metadata
            );

            Map<String, Object> upsertRequest = Map.of(
                "vectors", List.of(vector),
                "namespace", namespace != null ? namespace : "default"
            );

            // Call Pinecone upsert API
            Map<String, Object> response = webClient.post()
                .uri(getPineconeUrl() + "/vectors/upsert")
                .header("Api-Key", pineconeApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(upsertRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            log.debug("Pinecone upsert response: {}", response);
            return true;

        } catch (Exception e) {
            log.error("Error upserting vector to Pinecone: id={}", id, e);
            return false;
        }
    }
}
