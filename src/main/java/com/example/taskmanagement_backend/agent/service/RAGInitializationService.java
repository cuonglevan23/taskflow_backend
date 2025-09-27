package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.document.EmbeddingDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * RAG Initialization Service - DISABLED
 * Replaced by new DocumentLoaderService + RAGService system
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rag.legacy.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Order(1000)
public class RAGInitializationService implements CommandLineRunner {

    private final DocumentProcessingService documentProcessingService;

    @Value("${app.docs.path:src/main/java/com/example/taskmanagement_backend/agent/docs}")
    private String docsPath;

    @Value("${ai.rag.enabled:true}")
    private boolean ragEnabled;

    @Override
    public void run(String... args) {
        if (!ragEnabled) {
            log.info("RAG is disabled, skipping document indexing");
            return;
        }

        log.info("🚀 Starting RAG initialization - indexing key documents...");

        // Process documents asynchronously to avoid blocking startup
        CompletableFuture.runAsync(this::initializeRAGDocuments)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("RAG initialization failed", throwable);
                } else {
                    log.info("✅ RAG initialization completed successfully");
                }
            });
    }

    /**
     * Initialize RAG with key documents
     */
    private void initializeRAGDocuments() {
        try {
            log.info("📚 Processing AI Agent documentation...");

            // Process MyTask API Guide
            processMyTaskAPIGuide();

            // You can add more documents here in the future
            // processOtherDocuments();

        } catch (Exception e) {
            log.error("Error during RAG initialization", e);
        }
    }

    /**
     * Process the MyTask API Guide document
     */
    private void processMyTaskAPIGuide() {
        try {
            String filePath = docsPath + "/AI_AGENT_MYTASK_API_GUIDE.md";
            Path path = Paths.get(filePath);

            if (!Files.exists(path)) {
                log.warn("⚠️ MyTask API Guide not found at: {}", filePath);
                log.info("Attempting to find file in project root...");

                // Try alternative paths
                String[] alternativePaths = {
                    "src/main/java/com/example/taskmanagement_backend/agent/docs/AI_AGENT_MYTASK_API_GUIDE.md",
                    "docs/AI_AGENT_MYTASK_API_GUIDE.md",
                    "AI_AGENT_MYTASK_API_GUIDE.md"
                };

                for (String altPath : alternativePaths) {
                    Path altPathObj = Paths.get(altPath);
                    if (Files.exists(altPathObj)) {
                        filePath = altPath;
                        log.info("✅ Found MyTask API Guide at: {}", filePath);
                        break;
                    }
                }

                if (!Files.exists(Paths.get(filePath))) {
                    log.error("❌ Could not find MyTask API Guide in any expected location");
                    return;
                }
            }

            log.info("📖 Processing MyTask API Guide from: {}", filePath);

            EmbeddingDocument processedDoc = documentProcessingService.processMyTaskAPIGuide(filePath);

            if (processedDoc.isReadyForAI()) {
                log.info("✅ MyTask API Guide successfully indexed for AI Agent");
                log.info("📊 Document stats: {} words, {} topics, AI relevance: {}",
                    processedDoc.getWordCount(),
                    processedDoc.getAiTopics() != null ? processedDoc.getAiTopics().size() : 0,
                    processedDoc.getAiRelevanceScore());
            } else {
                log.warn("⚠️ MyTask API Guide processing completed but document may not be fully ready for AI");
            }

        } catch (Exception e) {
            log.error("❌ Failed to process MyTask API Guide", e);
        }
    }

    /**
     * Check RAG system health
     */
    public boolean isRAGHealthy() {
        try {
            // Basic health check - you could expand this
            return ragEnabled && documentProcessingService != null;
        } catch (Exception e) {
            log.error("RAG health check failed", e);
            return false;
        }
    }

    /**
     * Get RAG initialization status
     */
    public String getInitializationStatus() {
        if (!ragEnabled) {
            return "RAG_DISABLED";
        }

        try {
            // You could store this status in Redis or database
            return "RAG_INITIALIZED_" + LocalDateTime.now().toLocalDate();
        } catch (Exception e) {
            return "RAG_ERROR";
        }
    }

    /**
     * Manually trigger reindexing (useful for API endpoint)
     */
    public void reindexDocuments() {
        log.info("🔄 Manual reindexing triggered...");
        CompletableFuture.runAsync(this::initializeRAGDocuments);
    }
}
