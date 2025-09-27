package com.example.taskmanagement_backend.search.repositories;

import com.example.taskmanagement_backend.search.documents.TaskSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch repository for Task search operations
 * Provides full-text search and advanced filtering capabilities
 */
@Repository
public interface TaskSearchRepository extends ElasticsearchRepository<TaskSearchDocument, String> {

    // Full-text search with fuzzy matching
    @Query("{\"bool\": {\"should\": [{\"match\": {\"title\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\"}}}, {\"match\": {\"description\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\"}}}]}}")
    Page<TaskSearchDocument> findByTitleOrDescriptionFuzzy(String searchTerm, Pageable pageable);

    // Search with boosting for title matches
    @Query("{\"bool\": {\"should\": [{\"match\": {\"title\": {\"query\": \"?0\", \"boost\": 2.0}}}, {\"match\": {\"description\": \"?0\"}}]}}")
    Page<TaskSearchDocument> findByTitleOrDescriptionWithBoost(String searchTerm, Pageable pageable);

    // Multi-field search with autocomplete - Fixed version
    @Query("{\"bool\": {\"should\": [{\"match_phrase_prefix\": {\"title\": {\"query\": \"?0\", \"max_expansions\": 10}}}, {\"match_phrase_prefix\": {\"description\": {\"query\": \"?0\", \"max_expansions\": 10}}}, {\"match\": {\"assigneeName\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\"}}}, {\"match\": {\"projectName\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\"}}}], \"minimum_should_match\": 1}}")
    Page<TaskSearchDocument> findByMultiFieldAutocomplete(String searchTerm, Pageable pageable);

    // Safer autocomplete method without complex matching
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"title^3\", \"description^1\", \"assigneeName^2\", \"projectName^2\"], \"type\": \"phrase_prefix\", \"max_expansions\": 10}}")
    Page<TaskSearchDocument> findBySimpleAutocomplete(String searchTerm, Pageable pageable);

    // Filter by assignee
    Page<TaskSearchDocument> findByAssigneeId(Long assigneeId, Pageable pageable);

    // Filter by project
    Page<TaskSearchDocument> findByProjectId(Long projectId, Pageable pageable);

    // Filter by status
    Page<TaskSearchDocument> findByStatus(String status, Pageable pageable);

    // Filter by priority
    Page<TaskSearchDocument> findByPriority(String priority, Pageable pageable);

    // Filter by due date range
    Page<TaskSearchDocument> findByDueDateBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Filter by tags
    Page<TaskSearchDocument> findByTagsIn(List<String> tags, Pageable pageable);

    // Filter by visibility (privacy filtering)
    Page<TaskSearchDocument> findByVisibleToUserIdsContains(Long userId, Pageable pageable);

    // Filter by completion status
    Page<TaskSearchDocument> findByIsCompleted(Boolean isCompleted, Pageable pageable);

    // Filter by pinned status
    Page<TaskSearchDocument> findByIsPinned(Boolean isPinned, Pageable pageable);

    // Complex search with multiple filters
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"title^2\", \"description\", \"assigneeName\", \"projectName\"]}}, {\"terms\": {\"status\": ?1}}, {\"range\": {\"dueDate\": {\"gte\": \"?2\", \"lte\": \"?3\"}}}], \"filter\": [{\"terms\": {\"visibleToUserIds\": [?4]}}]}}")
    Page<TaskSearchDocument> findBySearchTermAndFilters(
        String searchTerm,
        List<String> statuses,
        LocalDateTime dueDateStart,
        LocalDateTime dueDateEnd,
        Long userId,
        Pageable pageable
    );

    // Search for tasks assigned to user with text search
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"title^2\", \"description\"]}}, {\"term\": {\"assigneeId\": ?1}}]}}")
    Page<TaskSearchDocument> findMyTasksWithSearch(String searchTerm, Long userId, Pageable pageable);

    // Get overdue tasks
    @Query("{\"bool\": {\"must\": [{\"range\": {\"dueDate\": {\"lt\": \"now\"}}}, {\"term\": {\"isCompleted\": false}}, {\"terms\": {\"visibleToUserIds\": [?0]}}]}}")
    Page<TaskSearchDocument> findOverdueTasks(Long userId, Pageable pageable);

    // Search with custom scoring
    @Query("{\"function_score\": {\"query\": {\"multi_match\": {\"query\": \"?0\", \"fields\": [\"title^2\", \"description\"]}}, \"functions\": [{\"filter\": {\"term\": {\"isPinned\": true}}, \"weight\": 2}, {\"filter\": {\"range\": {\"searchScore\": {\"gte\": 0.8}}}, \"weight\": 1.5}]}}")
    Page<TaskSearchDocument> findWithCustomScoring(String searchTerm, Pageable pageable);

    // Basic search methods that work reliably without complex queries
    Page<TaskSearchDocument> findAll(Pageable pageable);

    // Simple text search without complex conversions
    @Query("{\"match_all\": {}}")
    Page<TaskSearchDocument> findAllTasks(Pageable pageable);

    // Very basic title search that should work
    @Query("{\"match\": {\"title\": \"?0\"}}")
    Page<TaskSearchDocument> findByTitleSimple(String searchTerm, Pageable pageable);

    // Basic description search
    @Query("{\"match\": {\"description\": \"?0\"}}")
    Page<TaskSearchDocument> findByDescriptionSimple(String searchTerm, Pageable pageable);
}
