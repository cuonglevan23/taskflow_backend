package com.example.taskmanagement_backend.search.controllers;

import com.example.taskmanagement_backend.search.documents.*;
import com.example.taskmanagement_backend.search.repositories.*;
import com.example.taskmanagement_backend.search.services.SearchService;
import com.example.taskmanagement_backend.search.services.SearchService.*;
import com.example.taskmanagement_backend.search.services.SearchEventPublisher;
import com.example.taskmanagement_backend.search.dto.*;
import com.example.taskmanagement_backend.search.dto.SmartSuggestion;
import com.example.taskmanagement_backend.search.dto.SmartSuggestionsRequest;
import com.example.taskmanagement_backend.search.dto.SaveSearchHistoryRequest;
import com.example.taskmanagement_backend.services.UserService;
import com.example.taskmanagement_backend.dtos.UserDto.UserResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for search functionality
 * Provides comprehensive search capabilities across all entities
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Search", description = "Elasticsearch-powered search and filtering APIs")
public class SearchController {

    private final SearchService searchService;
    private final SearchEventPublisher searchEventPublisher;
    private final UserSearchRepository userSearchRepository;
    private final TeamSearchRepository teamSearchRepository;
    private final ProjectSearchRepository projectSearchRepository;
    private final TaskSearchRepository taskSearchRepository;
    private final UserService userService;
    // 🔧 FIX: Add DirectElasticsearchService for proper task search
    private final com.example.taskmanagement_backend.search.services.DirectElasticsearchService directElasticsearchService;

    // ==================== GLOBAL SEARCH ====================

    @GetMapping("/global")
    @Operation(summary = "Global search", description = "Search across all entities (tasks, projects, users, teams)")
    public ResponseEntity<Map<String, Object>> globalSearch(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);

            GlobalSearchResult result = searchService.globalSearch(q != null ? q : "", currentUserId, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("query", q);
            response.put("totalResults", result.getTotalResults());
            response.put("data", Map.of(
                "tasks", createPageResponse(result.getTasks()),
                "projects", createPageResponse(result.getProjects()),
                "users", createPageResponse(result.getUsers()),
                "teams", createPageResponse(result.getTeams())
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Global search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "Search service unavailable"));
        }
    }

    // ==================== TASK SEARCH ====================

    @GetMapping("/tasks")
    @Operation(summary = "Search tasks", description = "Advanced task search with filters")
    public ResponseEntity<Map<String, Object>> searchTasks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) String dueDateFrom,
            @RequestParam(required = false) String dueDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);

            // 🔧 FIX: Use DirectElasticsearchService instead of old SearchService
            // This matches the logic from /my-tasks exactly
            Map<String, Object> result = directElasticsearchService.searchTasks(q, currentUserId, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", q != null ? q : "",
                "data", result.get("content"),
                "pagination", Map.of(
                    "currentPage", result.get("number"),
                    "totalPages", result.get("totalPages"),
                    "totalElements", result.get("totalElements"),
                    "hasNext", result.get("hasNext"),
                    "hasPrevious", result.get("hasPrevious"),
                    "size", result.get("size")
                )
            ));
        } catch (Exception e) {
            log.error("❌ Task search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "Task search failed: " + e.getMessage()));
        }
    }

    @GetMapping("/tasks/autocomplete")
    @Operation(summary = "Task autocomplete", description = "Autocomplete suggestions for task search")
    public ResponseEntity<Map<String, Object>> autocompleteTask(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {

        try {
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);

            Page<TaskSearchDocument> suggestions = searchService.autocompleteTask(q, currentUserId, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", q,
                "suggestions", suggestions.getContent()
            ));
        } catch (Exception e) {
            log.error("❌ Task autocomplete failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", true, "suggestions", List.of()));
        }
    }

    @GetMapping("/tasks/my")
    @Operation(summary = "Search my tasks", description = "Search tasks assigned to current user")
    public ResponseEntity<Map<String, Object>> searchMyTasks(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);

            Page<TaskSearchDocument> tasks = searchService.searchMyTasks(q, currentUserId, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", q != null ? q : "",
                "data", tasks.getContent(),
                "pagination", createPaginationInfo(tasks)
            ));
        } catch (Exception e) {
            log.error("❌ My tasks search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "My tasks search failed"));
        }
    }

    @GetMapping("/tasks/overdue")
    @Operation(summary = "Get overdue tasks", description = "Find overdue tasks for current user")
    public ResponseEntity<Map<String, Object>> getOverdueTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);

            Page<TaskSearchDocument> tasks = searchService.getOverdueTasks(currentUserId, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", tasks.getContent(),
                "pagination", createPaginationInfo(tasks)
            ));
        } catch (Exception e) {
            log.error("❌ Overdue tasks search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "Overdue tasks search failed"));
        }
    }

    // ==================== PROJECT SEARCH ====================

    @GetMapping("/projects")
    @Operation(summary = "Search projects", description = "Advanced project search with filters")
    public ResponseEntity<Map<String, Object>> searchProjects(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);

            ProjectSearchCriteria criteria = ProjectSearchCriteria.builder()
                .searchTerm(q)
                .statuses(status)
                .tags(tags)
                .userId(currentUserId)
                .build();

            Page<ProjectSearchDocument> projects = searchService.searchProjects(criteria, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", q != null ? q : "",
                "data", projects.getContent(),
                "pagination", createPaginationInfo(projects)
            ));
        } catch (Exception e) {
            log.error("❌ Project search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "Project search failed"));
        }
    }

    @GetMapping("/projects/autocomplete")
    @Operation(summary = "Project autocomplete", description = "Autocomplete suggestions for project search")
    public ResponseEntity<Map<String, Object>> autocompleteProject(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {

        try {
            // 🔒 SECURITY FIX: Add userId for proper filtering
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);
            Page<ProjectSearchDocument> suggestions = searchService.autocompleteProject(q, currentUserId, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", q,
                "suggestions", suggestions.getContent()
            ));
        } catch (Exception e) {
            log.error("❌ Project autocomplete failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", true, "suggestions", List.of()));
        }
    }

    @GetMapping("/projects/my")
    @Operation(summary = "Search my projects", description = "Search projects where user is owner or member")
    public ResponseEntity<Map<String, Object>> searchMyProjects(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);

            // 🔒 SECURITY FIX: Use secure method instead of old repository method
            Page<ProjectSearchDocument> projects = searchService.searchMyProjectsSecure(q, currentUserId, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", q != null ? q : "",
                "data", projects.getContent(),
                "pagination", createPaginationInfo(projects)
            ));
        } catch (Exception e) {
            log.error("❌ My projects search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "My projects search failed"));
        }
    }

    // ==================== USER SEARCH ====================

    @GetMapping("/users")
    @Operation(summary = "Search users", description = "Search for users, friends, and colleagues")
    public ResponseEntity<Map<String, Object>> searchUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> departments,
            @RequestParam(required = false) List<String> skills,
            @RequestParam(required = false) Boolean premium,
            @RequestParam(required = false) Boolean online,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size);

            UserSearchCriteria criteria = UserSearchCriteria.builder()
                .searchTerm(q)
                .departments(departments)
                .skills(skills)
                .isPremium(premium)
                .isOnline(online)
                .build();

            Page<UserSearchDocument> users = searchService.searchUsers(criteria, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", q != null ? q : "",
                "data", users.getContent(),
                "pagination", createPaginationInfo(users)
            ));
        } catch (Exception e) {
            log.error("❌ User search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "User search failed"));
        }
    }

    @GetMapping("/users/autocomplete")
    @Operation(summary = "User autocomplete", description = "Autocomplete suggestions for user search")
    public ResponseEntity<Map<String, Object>> autocompleteUser(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {

        try {
            // 🔒 SECURITY FIX: Add userId for proper filtering (though user search can be public)
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);

            // Use secure user search for autocomplete with basic privacy filtering
            UserSearchCriteria criteria = UserSearchCriteria.builder()
                .searchTerm(q)
                .build();
            Page<UserSearchDocument> suggestions = searchService.searchUsers(criteria, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", q,
                "suggestions", suggestions.getContent()
            ));
        } catch (Exception e) {
            log.error("❌ User autocomplete failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", true, "suggestions", List.of()));
        }
    }

    // ==================== TEAM SEARCH ====================

    @GetMapping("/teams")
    @Operation(summary = "Search teams", description = "Search for teams to join or collaborate with")
    public ResponseEntity<Map<String, Object>> searchTeams(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) List<String> departments,
            @RequestParam(required = false) Integer minMembers,
            @RequestParam(required = false) Integer maxMembers,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size);

            TeamSearchCriteria criteria = TeamSearchCriteria.builder()
                .searchTerm(q)
                .teamTypes(types)
                .departments(departments)
                .minMembers(minMembers)
                .maxMembers(maxMembers)
                .build();

            Page<TeamSearchDocument> teams = searchService.searchTeams(criteria, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", q != null ? q : "",
                "data", teams.getContent(),
                "pagination", createPaginationInfo(teams)
            ));
        } catch (Exception e) {
            log.error("❌ Team search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "Team search failed"));
        }
    }

    @GetMapping("/teams/joinable")
    @Operation(summary = "Find joinable teams", description = "Find teams that user can join")
    public ResponseEntity<Map<String, Object>> findJoinableTeams(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);

            Page<TeamSearchDocument> teams = searchService.findJoinableTeams(q, currentUserId, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", q != null ? q : "",
                "data", teams.getContent(),
                "pagination", createPaginationInfo(teams)
            ));
        } catch (Exception e) {
            log.error("❌ Joinable teams search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "Joinable teams search failed"));
        }
    }

    // ==================== UNIFIED SEARCH API ====================

    @PostMapping
    @Operation(summary = "Unified search", description = "Single endpoint for all search needs with advanced filtering")
    public ResponseEntity<Map<String, Object>> unifiedSearch(@RequestBody UnifiedSearchRequest request) {
        try {
            Long currentUserId = getCurrentUserId();

            UnifiedSearchResult result = searchService.unifiedSearch(request, currentUserId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", Map.of(
                "tasks", createPageResponse(result.getTasks()),
                "projects", createPageResponse(result.getProjects()),
                "users", createPageResponse(result.getUsers()),
                "teams", createPageResponse(result.getTeams())
            ));
            response.put("meta", Map.of(
                "query", request.getQuery(),
                "totalResults", result.getTotalResults(),
                "searchTime", result.getSearchTime() + "ms",
                "suggestions", result.getSuggestions()
            ));

            // Save to search history if query is not empty
            if (request.getQuery() != null && !request.getQuery().trim().isEmpty()) {
                searchService.saveSearchHistory(currentUserId, request.getQuery());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Unified search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "Search service unavailable"));
        }
    }

    @GetMapping("/quick")
    @Operation(summary = "Quick search", description = "Quick search across all entities")
    public ResponseEntity<Map<String, Object>> quickSearch(
            @RequestParam String q,
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);

            QuickSearchResult result = searchService.quickSearch(q, scope, currentUserId, pageable);

            // Save to search history
            searchService.saveSearchHistory(currentUserId, q);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "tasks", createPageResponse(result.getTasks()),
                    "projects", createPageResponse(result.getProjects()),
                    "users", createPageResponse(result.getUsers()),
                    "teams", createPageResponse(result.getTeams())
                ),
                "totalResults", result.getTotalResults(),
                "query", q,
                "scope", scope != null ? scope : "all"
            ));
        } catch (Exception e) {
            log.error("❌ Quick search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "Quick search failed"));
        }
    }

    @GetMapping("/my")
    @Operation(summary = "Search my content", description = "Search user's own content across entities")
    public ResponseEntity<Map<String, Object>> searchMyContent(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> entities,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Long currentUserId = getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size);

            MyContentSearchResult result = searchService.searchMyContent(q, entities, currentUserId, pageable);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "tasks", createPageResponse(result.getTasks()),
                    "projects", createPageResponse(result.getProjects())
                ),
                "totalResults", result.getTotalResults(),
                "query", q != null ? q : "",
                "entities", entities != null ? entities : List.of("tasks", "projects")
            ));
        } catch (Exception e) {
            log.error("❌ My content search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "My content search failed"));
        }
    }

    @GetMapping("/autocomplete")
    @Operation(summary = "Universal autocomplete", description = "Autocomplete suggestions for any entity")
    public ResponseEntity<Map<String, Object>> universalAutocomplete(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "tasks") String entity,
            @RequestParam(defaultValue = "5") int limit) {

        try {
            Long currentUserId = getCurrentUserId();
            List<String> suggestions = searchService.getAutocompleteSuggestions(q, entity, currentUserId, limit);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", q,
                "entity", entity,
                "suggestions", suggestions
            ));
        } catch (Exception e) {
            log.error("❌ Universal autocomplete failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "suggestions", List.of()
            ));
        }
    }

    // ==================== SEARCH HISTORY ====================

    @GetMapping("/history")
    @Operation(summary = "Get search history", description = "Get user's search history")
    public ResponseEntity<Map<String, Object>> getSearchHistory(
            @RequestParam(defaultValue = "10") int limit) {

        try {
            Long currentUserId = getCurrentUserId();
            List<String> history = searchService.getSearchHistory(currentUserId, limit);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "history", history
            ));
        } catch (Exception e) {
            log.error("❌ Get search history failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "history", List.of()
            ));
        }
    }

    @PostMapping("/history")
    @Operation(summary = "Save search term", description = "Save a search term to history")
    public ResponseEntity<Map<String, Object>> saveSearchHistory(@RequestBody SaveSearchHistoryRequest request) {
        try {
            Long currentUserId = getCurrentUserId();
            String query = request.getQuery();

            if (query != null && !query.trim().isEmpty()) {
                searchService.saveSearchHistory(currentUserId, query);
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("❌ Save search history failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", true));
        }
    }

    @DeleteMapping("/history")
    @Operation(summary = "Clear search history", description = "Clear user's search history")
    public ResponseEntity<Map<String, Object>> clearSearchHistory() {
        try {
            Long currentUserId = getCurrentUserId();
            searchService.clearSearchHistory(currentUserId);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("❌ Clear search history failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", true));
        }
    }

    @DeleteMapping("/history/{query}")
    @Operation(summary = "Remove search term", description = "Remove a specific search term from history")
    public ResponseEntity<Map<String, Object>> removeSearchHistoryItem(@PathVariable String query) {
        try {
            Long currentUserId = getCurrentUserId();
            searchService.removeSearchHistoryItem(currentUserId, query);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("❌ Remove search history item failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", true));
        }
    }

    // ==================== SMART SUGGESTIONS ====================

    @PostMapping("/smart-suggestions")
    @Operation(summary = "Get smart suggestions", description = "AI-powered search suggestions based on context")
    public ResponseEntity<Map<String, Object>> getSmartSuggestions(@RequestBody SmartSuggestionsRequest request) {
        try {
            Long currentUserId = getCurrentUserId();
            List<SmartSuggestion> suggestions = searchService.getSmartSuggestions(request, currentUserId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "suggestions", suggestions
            ));
        } catch (Exception e) {
            log.error("Smart suggestions failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "suggestions", List.of()
            ));
        }
    }


    // ==================== HELPER METHODS ====================

    /**
     * Get current authenticated user's ID
     * @return Current user ID
     * @throws RuntimeException if user not authenticated or not found
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("❌ User not authenticated when accessing search endpoints");
            throw new RuntimeException("User not authenticated");
        }

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            UserResponseDto currentUser = userService.getUserByEmail(userDetails.getUsername());

            if (currentUser == null || currentUser.getId() == null) {
                log.error("❌ User not found for email: {}", userDetails.getUsername());
                throw new RuntimeException("User not found");
            }

            log.debug("✅ Current authenticated user ID: {}", currentUser.getId());
            return currentUser.getId();
        } catch (Exception e) {
            log.error("❌ Failed to get current user ID: {}", e.getMessage());
            throw new RuntimeException("Failed to get current user: " + e.getMessage());
        }
    }

    /**
     * Validate that the current user has access to the requested data
     * This adds an extra layer of security validation
     */
    private void validateUserAccess(Long userId) {
        Long currentUserId = getCurrentUserId();
        if (!currentUserId.equals(userId)) {
            log.warn("⚠️ User {} attempted to access data for user {}", currentUserId, userId);
            throw new RuntimeException("Access denied: Cannot access other user's data");
        }
    }

    private Map<String, Object> createPageResponse(Page<?> page) {
        return Map.of(
            "content", page.getContent(),
            "pagination", createPaginationInfo(page)
        );
    }

    private Map<String, Object> createPaginationInfo(Page<?> page) {
        return Map.of(
            "currentPage", page.getNumber(),
            "totalPages", page.getTotalPages(),
            "totalElements", page.getTotalElements(),
            "hasNext", page.hasNext(),
            "hasPrevious", page.hasPrevious(),
            "size", page.getSize()
        );
    }

    // ==================== BULK REINDEX ENDPOINT ====================

    @PostMapping("/reindex")
    @Operation(summary = "Trigger bulk reindex", description = "Reindex all entities from database to Elasticsearch")
    public ResponseEntity<Map<String, Object>> triggerBulkReindex(
            @RequestParam(required = false) List<String> entities) {

        try {
            log.info("🔄 Starting bulk reindex for entities: {}",
                entities != null ? entities : List.of("all"));

            Map<String, Object> results = new HashMap<>();

            if (entities == null || entities.contains("tasks")) {
                // Trigger task reindex via Kafka
                searchEventPublisher.publishBulkReindexEvent("TASK");
                results.put("tasks", "reindex triggered");
            }

            if (entities == null || entities.contains("projects")) {
                // Trigger project reindex via Kafka
                searchEventPublisher.publishBulkReindexEvent("PROJECT");
                results.put("projects", "reindex triggered");
            }

            if (entities == null || entities.contains("users")) {
                // Trigger user reindex via Kafka
                searchEventPublisher.publishBulkReindexEvent("USER");
                results.put("users", "reindex triggered");
            }

            if (entities == null || entities.contains("teams")) {
                // Trigger team reindex via Kafka
                searchEventPublisher.publishBulkReindexEvent("TEAM");
                results.put("teams", "reindex triggered");
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Bulk reindex triggered successfully",
                "results", results,
                "note", "Check Kafka logs for indexing progress"
            ));

        } catch (Exception e) {
            log.error("❌ Failed to trigger bulk reindex: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "success", false,
                    "message", "Failed to trigger bulk reindex",
                    "error", e.getMessage()
                ));
        }
    }

    // ==================== INDEX MANAGEMENT ====================

    @PostMapping("/admin/reindex")
    @Operation(summary = "Manually reindex all data", description = "Populate Elasticsearch indices with existing database data")
    public ResponseEntity<Map<String, Object>> reindexAllData() {
        try {
            log.info("🔄 Starting manual reindex of all search data...");

            // This will be implemented by calling the SearchIndexingService directly
            // instead of relying on Kafka events
            searchService.manualReindexAllData();

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Reindexing started successfully",
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Manual reindex failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "Reindex failed: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/index-status")
    @Operation(summary = "Check index status", description = "Get the status of all search indices")
    public ResponseEntity<Map<String, Object>> getIndexStatus() {
        try {
            Map<String, Object> status = searchService.getIndexStatus();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", status
            ));
        } catch (Exception e) {
            log.error("❌ Failed to get index status: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "Failed to get index status"));
        }
    }
}
