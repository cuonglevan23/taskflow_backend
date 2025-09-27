package com.example.taskmanagement_backend.search.services;

import com.example.taskmanagement_backend.search.services.SearchEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * Service for initializing search indices on application startup
 * Uses the documented event-driven approach to populate Elasticsearch
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchIndexInitializer {

    private final SearchEventPublisher searchEventPublisher;

    /**
     * Initialize search indices when application is ready
     * This will populate Elasticsearch with existing database data
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1000) // Run after other initialization
    public void initializeSearchIndices() {
        log.info("🔄 Initializing search indices with existing data...");

        try {
            // Use the documented event-driven approach to populate Elasticsearch
            searchEventPublisher.publishBulkReindexEvent("TASK");
            searchEventPublisher.publishBulkReindexEvent("PROJECT");
            searchEventPublisher.publishBulkReindexEvent("USER");
            searchEventPublisher.publishBulkReindexEvent("TEAM");

            log.info("✅ Search index initialization events published successfully");
            log.info("📝 Note: Index population will happen asynchronously via Kafka consumers");

        } catch (Exception e) {
            log.error("❌ Failed to initialize search indices: {}", e.getMessage());
        }
    }
}
