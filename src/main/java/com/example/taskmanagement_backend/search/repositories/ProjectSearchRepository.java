package com.example.taskmanagement_backend.search.repositories;

import com.example.taskmanagement_backend.search.documents.ProjectSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch repository for Project search operations
 */
@Repository
public interface ProjectSearchRepository extends ElasticsearchRepository<ProjectSearchDocument, String> {

    // Full-text search with fuzzy matching
    @Query("{\"bool\": {\"should\": [{\"match\": {\"name\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\", \"boost\": 2.0}}}, {\"match\": {\"description\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\"}}}]}}")
    Page<ProjectSearchDocument> findByNameOrDescriptionFuzzy(String searchTerm, Pageable pageable);

    // Autocomplete search
    @Query("{\"bool\": {\"should\": [{\"match_phrase_prefix\": {\"name\": \"?0\"}}, {\"match_phrase_prefix\": {\"description\": \"?0\"}}, {\"match\": {\"ownerName\": \"?0\"}}]}}")
    Page<ProjectSearchDocument> findByAutocomplete(String searchTerm, Pageable pageable);

    // Filter by owner
    Page<ProjectSearchDocument> findByOwnerId(Long ownerId, Pageable pageable);

    // Filter by status
    Page<ProjectSearchDocument> findByStatus(String status, Pageable pageable);

    // Filter by member
    Page<ProjectSearchDocument> findByMemberIdsContains(Long memberId, Pageable pageable);

    // Filter by active status
    Page<ProjectSearchDocument> findByIsActive(Boolean isActive, Pageable pageable);

    // Filter by date range
    Page<ProjectSearchDocument> findByStartDateBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Filter by tags
    Page<ProjectSearchDocument> findByTagsIn(List<String> tags, Pageable pageable);

    // Complex search with filters
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^2\", \"description\", \"ownerName\"]}}, {\"terms\": {\"status\": ?1}}, {\"term\": {\"isActive\": true}}], \"filter\": [{\"terms\": {\"visibleToUserIds\": [?2]}}]}}")
    Page<ProjectSearchDocument> findBySearchTermAndFilters(String searchTerm, List<String> statuses, Long userId, Pageable pageable);

    // Search user's projects
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^2\", \"description\"]}}, {\"bool\": {\"should\": [{\"term\": {\"ownerId\": ?1}}, {\"terms\": {\"memberIds\": [?1]}}]}}]}}")
    Page<ProjectSearchDocument> findMyProjectsWithSearch(String searchTerm, Long userId, Pageable pageable);

    // High-performing projects
    @Query("{\"bool\": {\"must\": [{\"range\": {\"completionPercentage\": {\"gte\": ?0}}}, {\"term\": {\"isActive\": true}}], \"filter\": [{\"terms\": {\"visibleToUserIds\": [?1]}}]}}")
    Page<ProjectSearchDocument> findHighPerformingProjects(Float minCompletionPercentage, Long userId, Pageable pageable);
}
