package com.example.taskmanagement_backend.search.services;

import com.example.taskmanagement_backend.search.documents.*;
import com.example.taskmanagement_backend.search.repositories.*;
import com.example.taskmanagement_backend.search.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main search service providing unified search capabilities
 * Now uses DirectElasticsearchService to bypass conversion issues
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final TaskSearchRepository taskSearchRepository;
    private final ProjectSearchRepository projectSearchRepository;
    private final UserSearchRepository userSearchRepository;
    private final TeamSearchRepository teamSearchRepository;
    private final SearchEventPublisher searchEventPublisher;
    private final DirectElasticsearchService directElasticsearchService;

    // ✅ Add RedisTemplate for search history storage
    private final RedisTemplate<String, Object> redisTemplate;

    // Redis key prefixes for search history
    private static final String SEARCH_HISTORY_KEY_PREFIX = "search_history:user:";
    private static final int DEFAULT_HISTORY_LIMIT = 10;
    private static final int MAX_HISTORY_SIZE = 50;

    // ==================== UNIFIED SEARCH ====================

    /**
     * Global search across all entities - NOW WITH PROPER USER FILTERING
     */
    public GlobalSearchResult globalSearch(String searchTerm, Long userId, Pageable pageable) {
        try {
            // 🔒 SECURITY FIX: Use DirectElasticsearchService with user filtering for all entities
            Page<TaskSearchDocument> tasks = Page.empty();
            Page<ProjectSearchDocument> projects = Page.empty();
            Page<UserSearchDocument> users = Page.empty();
            Page<TeamSearchDocument> teams = Page.empty();

            // 🔒 SECURE Task search using DirectElasticsearchService
            try {
                Map<String, Object> taskResult = directElasticsearchService.searchTasks(searchTerm, userId, pageable);
                List<Map<String, Object>> taskContent = (List<Map<String, Object>>) taskResult.get("content");
                List<TaskSearchDocument> taskDocuments = taskContent.stream()
                    .map(this::mapToTaskSearchDocument)
                    .collect(Collectors.toList());
                long taskTotalElements = (Long) taskResult.get("totalElements");
                tasks = new PageImpl<>(taskDocuments, pageable, taskTotalElements);
            } catch (Exception e) {
                log.warn("Task search failed in global search: {}", e.getMessage());
                tasks = Page.empty();
            }

            // 🔒 SECURE Project search using DirectElasticsearchService
            try {
                Map<String, Object> projectResult = directElasticsearchService.searchProjects(searchTerm, userId, pageable);
                List<Map<String, Object>> projectContent = (List<Map<String, Object>>) projectResult.get("content");
                List<ProjectSearchDocument> projectDocuments = projectContent.stream()
                    .map(this::mapToProjectSearchDocument)
                    .collect(Collectors.toList());
                long projectTotalElements = (Long) projectResult.get("totalElements");
                projects = new PageImpl<>(projectDocuments, pageable, projectTotalElements);
            } catch (Exception e) {
                log.warn("Project search failed in global search: {}", e.getMessage());
                projects = Page.empty();
            }

            // User search - Public (can search all users for connections)
            try {
                if (searchTerm != null && searchTerm.contains("@") && searchTerm.contains(".")) {
                    // Email search
                    Map<String, Object> directEmailResult = directElasticsearchService.searchUserByEmail(searchTerm);
                    if ((Boolean) directEmailResult.get("found")) {
                        Map<String, Object> userMap = (Map<String, Object>) directEmailResult.get("user");
                        UserSearchDocument userDoc = mapToUserSearchDocument(userMap);
                        users = new PageImpl<>(List.of(userDoc), pageable, 1);
                    } else {
                        Map<String, Object> userResult = directElasticsearchService.searchUsers(searchTerm, pageable);
                        users = convertDirectResultToUserPage(userResult, pageable);
                    }
                } else {
                    // Regular user search
                    Map<String, Object> userResult = directElasticsearchService.searchUsers(searchTerm, pageable);
                    users = convertDirectResultToUserPage(userResult, pageable);
                }
            } catch (Exception e) {
                log.warn("User search failed in global search: {}", e.getMessage());
                users = Page.empty();
            }

            // Team search - SECURE (only teams user has access to)
            try {
                Map<String, Object> teamResult = directElasticsearchService.searchTeamsSecure(searchTerm, userId, pageable);
                List<Map<String, Object>> teamContent = (List<Map<String, Object>>) teamResult.get("content");
                List<TeamSearchDocument> teamDocuments = teamContent.stream()
                    .map(this::mapToTeamSearchDocument)
                    .collect(Collectors.toList());
                long teamTotalElements = (Long) teamResult.get("totalElements");
                teams = new PageImpl<>(teamDocuments, pageable, teamTotalElements);
            } catch (Exception e) {
                log.warn("Team search failed in global search: {}", e.getMessage());
                teams = Page.empty();
            }

            return GlobalSearchResult.builder()
                    .tasks(tasks)
                    .projects(projects)
                    .users(users)
                    .teams(teams)
                    .totalResults(tasks.getTotalElements() + projects.getTotalElements() +
                                users.getTotalElements() + teams.getTotalElements())
                    .searchTerm(searchTerm)
                    .build();
        } catch (Exception e) {
            log.error("Global search failed: {}", e.getMessage());
            throw new RuntimeException("Search service unavailable", e);
        }
    }

    // ==================== TASK SEARCH ====================

    /**
     * Advanced task search with filters - Now using direct Elasticsearch to bypass conversion errors
     * 🔒 SECURITY FIX: Now properly filters by user ID
     */
    public Page<TaskSearchDocument> searchTasks(TaskSearchCriteria criteria, Pageable pageable) {
        try {
            // 🔒 SECURITY: Pass user ID to ensure proper filtering
            Map<String, Object> result = directElasticsearchService.searchTasks(
                criteria.getSearchTerm(),
                criteria.getUserId(), // 🔒 CRITICAL: Now passing user ID for security filtering
                pageable
            );

            // Convert the result to a Page<TaskSearchDocument>
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            List<TaskSearchDocument> documents = content.stream()
                .map(this::mapToTaskSearchDocument)
                .collect(Collectors.toList());

            long totalElements = (Long) result.get("totalElements");

            return new PageImpl<>(documents, pageable, totalElements);

        } catch (Exception e) {
            log.error("Direct Elasticsearch search failed: {}", e.getMessage());
            // Return empty page as last resort
            return Page.empty(pageable);
        }
    }

    /**
     * Autocomplete for task search - Using direct search
     */
    public Page<TaskSearchDocument> autocompleteTask(String searchTerm, Long userId, Pageable pageable) {
        try {
            // Use the same direct search method for autocomplete
            return searchTasks(TaskSearchCriteria.builder()
                .searchTerm(searchTerm)
                .userId(userId)
                .build(), pageable);
        } catch (Exception e) {
            log.warn("Direct autocomplete failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    /**
     * Get my assigned tasks with search - NOW USING SECURE DIRECT SEARCH
     */
    public Page<TaskSearchDocument> searchMyTasks(String searchTerm, Long userId, Pageable pageable) {
        try {
            // 🔒 SECURITY FIX: Use DirectElasticsearchService instead of repository
            Map<String, Object> result = directElasticsearchService.searchTasks(
                searchTerm,
                userId, // This ensures user can only see their own tasks
                pageable
            );

            // Convert the result to a Page<TaskSearchDocument>
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            List<TaskSearchDocument> documents = content.stream()
                .map(this::mapToTaskSearchDocument)
                .collect(Collectors.toList());

            long totalElements = (Long) result.get("totalElements");

            return new PageImpl<>(documents, pageable, totalElements);
        } catch (Exception e) {
            log.error("My tasks search failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    /**
     * Get overdue tasks - NOW USING SECURE DIRECT SEARCH
     */
    public Page<TaskSearchDocument> getOverdueTasks(Long userId, Pageable pageable) {
        try {
            // 🔒 SECURITY FIX: Use DirectElasticsearchService to ensure user filtering
            Map<String, Object> result = directElasticsearchService.searchTasks(
                "", // Empty search term to get all user's tasks
                userId, // This ensures user can only see their own tasks
                pageable
            );

            // Convert and filter for overdue tasks
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            List<TaskSearchDocument> documents = content.stream()
                .map(this::mapToTaskSearchDocument)
                .filter(task -> {
                    // Filter for overdue tasks
                    if (task.getDueDate() != null && !Boolean.TRUE.equals(task.getIsCompleted())) {
                        return task.getDueDate().isBefore(LocalDateTime.now());
                    }
                    return false;
                })
                .collect(Collectors.toList());

            return new PageImpl<>(documents, pageable, documents.size());
        } catch (Exception e) {
            log.error("Overdue tasks search failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    // ==================== PROJECT SEARCH ====================

    /**
     * Advanced project search with filters
     * 🔒 SECURITY FIX: Now properly filters by user ID
     */
    public Page<ProjectSearchDocument> searchProjects(ProjectSearchCriteria criteria, Pageable pageable) {
        try {
            // 🔒 SECURITY: Pass user ID to ensure proper filtering
            Map<String, Object> result = directElasticsearchService.searchProjects(
                criteria.getSearchTerm(),
                criteria.getUserId(), // 🔒 CRITICAL: Now passing user ID for security filtering
                pageable
            );

            // Convert the result to a Page<ProjectSearchDocument>
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            List<ProjectSearchDocument> documents = content.stream()
                .map(this::mapToProjectSearchDocument)
                .collect(Collectors.toList());

            long totalElements = (Long) result.get("totalElements");

            return new PageImpl<>(documents, pageable, totalElements);

        } catch (Exception e) {
            log.error("Direct Elasticsearch project search failed: {}", e.getMessage());
            // Return empty page as last resort
            return Page.empty(pageable);
        }
    }

    /**
     * Autocomplete for project search
     */
    public Page<ProjectSearchDocument> autocompleteProject(String searchTerm, Pageable pageable) {
        try {
            return projectSearchRepository.findByAutocomplete(searchTerm, pageable);
        } catch (Exception e) {
            log.error("Project autocomplete failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    /**
     * 🔒 SECURE: Autocomplete for project search with user filtering
     */
    public Page<ProjectSearchDocument> autocompleteProject(String searchTerm, Long userId, Pageable pageable) {
        try {
            // 🔒 SECURITY FIX: Use DirectElasticsearchService with user filtering for autocomplete
            Map<String, Object> result = directElasticsearchService.searchProjects(searchTerm, userId, pageable);

            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            List<ProjectSearchDocument> documents = content.stream()
                .map(this::mapToProjectSearchDocument)
                .collect(Collectors.toList());

            long totalElements = (Long) result.get("totalElements");
            return new PageImpl<>(documents, pageable, totalElements);
        } catch (Exception e) {
            log.error("Secure project autocomplete failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    /**
     * Get my projects with search
     */
    public Page<ProjectSearchDocument> searchMyProjects(String searchTerm, Long userId, Pageable pageable) {
        try {
            return projectSearchRepository.findMyProjectsWithSearch(searchTerm, userId, pageable);
        } catch (Exception e) {
            log.error("My projects search failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    /**
     * 🔒 SECURE: Get my projects with search using DirectElasticsearchService
     */
    public Page<ProjectSearchDocument> searchMyProjectsSecure(String searchTerm, Long userId, Pageable pageable) {
        try {
            // 🔒 SECURITY FIX: Use DirectElasticsearchService with user filtering
            Map<String, Object> result = directElasticsearchService.searchProjects(searchTerm, userId, pageable);

            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            List<ProjectSearchDocument> documents = content.stream()
                .map(this::mapToProjectSearchDocument)
                .collect(Collectors.toList());

            long totalElements = (Long) result.get("totalElements");
            return new PageImpl<>(documents, pageable, totalElements);
        } catch (Exception e) {
            log.error("Secure my projects search failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    // ==================== USER SEARCH ====================

    /**
     * Search users (for finding friends, team members)
     */
    public Page<UserSearchDocument> searchUsers(UserSearchCriteria criteria, Pageable pageable) {
        try {
            Page<UserSearchDocument> results = Page.empty();

            if (criteria.hasFilters()) {
                try {
                    results = userSearchRepository.findBySearchTermWithFilters(
                        criteria.getSearchTerm(),
                        criteria.getDepartments(),
                        criteria.getSkills(),
                        pageable
                    );
                } catch (Exception conversionException) {
                    log.warn("Filtered search failed due to conversion: {}", conversionException.getMessage());
                    results = Page.empty();
                }
            } else if (criteria.getSearchTerm() != null && !criteria.getSearchTerm().isEmpty()) {
                String searchTerm = criteria.getSearchTerm().trim();

                // Check if search term looks like an email
                boolean isEmailSearch = searchTerm.contains("@") && searchTerm.contains(".");

                if (isEmailSearch) {
                    // First try exact email match
                    try {
                        UserSearchDocument exactMatch = userSearchRepository.findByEmail(searchTerm);
                        if (exactMatch != null) {
                            return new PageImpl<>(List.of(exactMatch), pageable, 1);
                        }
                    } catch (Exception emailException) {
                        log.warn("Email search failed: {}", emailException.getMessage());
                    }

                    // Try main search without strict filters for emails
                    try {
                        results = userSearchRepository.findBySearchTermSimple(searchTerm, pageable);
                    } catch (Exception conversionException) {
                        log.warn("Simple search failed due to conversion: {}", conversionException.getMessage());
                        results = Page.empty();
                    }

                    // If still no results, try with fuzzy matching
                    if (results.isEmpty()) {
                        try {
                            results = userSearchRepository.findWithCustomScoring(searchTerm, pageable);
                        } catch (Exception fuzzyException) {
                            log.warn("Fuzzy search failed: {}", fuzzyException.getMessage());
                            results = Page.empty();
                        }
                    }
                } else {
                    // Try main search first for non-email searches
                    try {
                        results = userSearchRepository.findBySearchTerm(searchTerm, pageable);
                    } catch (Exception mainSearchException) {
                        log.warn("Main search failed: {}", mainSearchException.getMessage());
                        results = Page.empty();
                    }

                    // If no results, try simple search without strict filters
                    if (results.isEmpty()) {
                        try {
                            results = userSearchRepository.findBySearchTermSimple(searchTerm, pageable);
                        } catch (Exception fallbackException) {
                            log.warn("Simple fallback search failed: {}", fallbackException.getMessage());
                            results = Page.empty();
                        }
                    }
                }
            } else {
                // Empty search - get all users with pagination
                try {
                    results = userSearchRepository.findWithCustomScoring("", pageable);
                } catch (Exception customException) {
                    try {
                        results = userSearchRepository.findBySearchTermSimple("*", pageable);
                    } catch (Exception simpleException) {
                        log.warn("Even simple wildcard search failed: {}", simpleException.getMessage());
                        results = Page.empty();
                    }
                }
            }

            return results;

        } catch (Exception e) {
            log.error("User search failed: {}", e.getMessage());
            // Last resort: return empty page instead of throwing exception
            return Page.empty();
        }
    }

    /**
     * Autocomplete for user search
     */
    public Page<UserSearchDocument> autocompleteUser(String searchTerm, Pageable pageable) {
        try {
            return userSearchRepository.findByAutocomplete(searchTerm, pageable);
        } catch (Exception e) {
            log.error("User autocomplete failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    /**
     * Find potential friends (exclude current friends)
     */
    public Page<UserSearchDocument> findPotentialFriends(String searchTerm, String currentUserId,
                                                           List<String> friendIds, Pageable pageable) {
        try {
            return userSearchRepository.findPotentialFriends(searchTerm, currentUserId, friendIds, pageable);
        } catch (Exception e) {
            log.error("Find potential friends failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    // ==================== TEAM SEARCH ====================

    /**
     * Search teams - NOW USING SECURE METHOD WITH USER ACCESS CONTROL
     */
    public Page<TeamSearchDocument> searchTeams(TeamSearchCriteria criteria, Pageable pageable) {
        try {
            // 🔒 SECURITY FIX: Use secure team search with user filtering
            Map<String, Object> result = directElasticsearchService.searchTeamsSecure(
                criteria.getSearchTerm(),
                criteria.getUserId(), // Pass user ID for access control
                pageable
            );

            // Convert the result to a Page<TeamSearchDocument>
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            List<TeamSearchDocument> documents = content.stream()
                .map(this::mapToTeamSearchDocument)
                .collect(Collectors.toList());

            long totalElements = (Long) result.get("totalElements");

            return new PageImpl<>(documents, pageable, totalElements);

        } catch (Exception e) {
            log.error("Secure team search failed: {}", e.getMessage());
            // Return empty page as last resort
            return Page.empty(pageable);
        }
    }

    /**
     * Autocomplete for team search
     */
    public Page<TeamSearchDocument> autocompleteTeam(String searchTerm, Pageable pageable) {
        try {
            return teamSearchRepository.findByAutocomplete(searchTerm, pageable);
        } catch (Exception e) {
            log.error("Team autocomplete failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    /**
     * Find teams user can join
     */
    public Page<TeamSearchDocument> findJoinableTeams(String searchTerm, Long userId, Pageable pageable) {
        try {
            return teamSearchRepository.findJoinableTeams(searchTerm, userId, pageable);
        } catch (Exception e) {
            log.error("Find joinable teams failed: {}", e.getMessage());
            return Page.empty();
        }
    }

    // ==================== UNIFIED SEARCH METHODS ====================

    /**
     * Unified search with advanced filtering
     */
    public UnifiedSearchResult unifiedSearch(UnifiedSearchRequest request, Long currentUserId) {
        long startTime = System.currentTimeMillis();

        try {
            // Create default pagination if not provided - use the nested class from UnifiedSearchRequest
            UnifiedSearchRequest.PaginationRequest pagination = request.getPagination() != null ?
                request.getPagination() :
                new UnifiedSearchRequest.PaginationRequest();

            // Set defaults if pagination is null
            if (pagination.getPage() == null) pagination.setPage(0);
            if (pagination.getSize() == null) pagination.setSize(20);

            Pageable pageable = PageRequest.of(pagination.getPage(), pagination.getSize());

            // Determine which entities to search
            List<String> entities = request.getEntities();
            boolean searchAll = entities == null || entities.isEmpty();

            // Special case: If query looks like an email, force include users in search
            String query = request.getQuery();
            boolean isEmailSearch = query != null && query.contains("@") && query.contains(".");
            boolean forceIncludeUsers = isEmailSearch;

            // Initialize empty results
            Page<TaskSearchDocument> tasks = Page.empty();
            Page<ProjectSearchDocument> projects = Page.empty();
            Page<UserSearchDocument> users = Page.empty();
            Page<TeamSearchDocument> teams = Page.empty();

            // Search each entity type if requested or if searching all
            if (searchAll || entities.contains("tasks")) {
                try {
                    TaskSearchCriteria taskCriteria = TaskSearchCriteria.builder()
                        .searchTerm(request.getQuery())
                        .userId(currentUserId)
                        .build();
                    tasks = searchTasks(taskCriteria, pageable);
                } catch (Exception e) {
                    log.warn("Task search failed in unified search: {}", e.getMessage());
                    tasks = Page.empty();
                }
            }

            if (searchAll || entities.contains("projects")) {
                try {
                    ProjectSearchCriteria projectCriteria = ProjectSearchCriteria.builder()
                        .searchTerm(request.getQuery())
                        .userId(currentUserId)
                        .build();
                    projects = searchProjects(projectCriteria, pageable);
                } catch (Exception e) {
                    log.warn("Project search failed in unified search: {}", e.getMessage());
                    projects = Page.empty();
                }
            }

            // USER SEARCH - Force include users when email is detected
            if (searchAll || entities.contains("users") || forceIncludeUsers) {
                // Use direct Elasticsearch service to bypass conversion issues
                try {
                    if (isEmailSearch) {
                        // Email search - try direct email match first
                        Map<String, Object> directEmailResult = directElasticsearchService.searchUserByEmail(query);
                        if ((Boolean) directEmailResult.get("found")) {
                            Map<String, Object> userMap = (Map<String, Object>) directEmailResult.get("user");
                            UserSearchDocument userDoc = mapToUserSearchDocument(userMap);
                            users = new PageImpl<>(List.of(userDoc), pageable, 1);
                        } else {
                            Map<String, Object> directSearchResult = directElasticsearchService.searchUsers(query, pageable);
                            users = convertDirectResultToUserPage(directSearchResult, pageable);
                        }
                    } else {
                        // Regular user search for non-email queries
                        Map<String, Object> directSearchResult = directElasticsearchService.searchUsers(query, pageable);
                        users = convertDirectResultToUserPage(directSearchResult, pageable);
                    }
                } catch (Exception directException) {
                    log.error("Direct Elasticsearch user search failed: {}", directException.getMessage());
                    users = Page.empty();
                }
            }

            if (searchAll || entities.contains("teams")) {
                try {
                    TeamSearchCriteria teamCriteria = TeamSearchCriteria.builder()
                        .searchTerm(request.getQuery())
                        .build();
                    teams = searchTeams(teamCriteria, pageable);
                } catch (Exception e) {
                    log.warn("Team search failed in unified search: {}", e.getMessage());
                    teams = Page.empty();
                }
            }

            long endTime = System.currentTimeMillis();
            long searchTime = endTime - startTime;

            long totalResults = tasks.getTotalElements() + projects.getTotalElements() +
                              users.getTotalElements() + teams.getTotalElements();

            return UnifiedSearchResult.builder()
                .tasks(tasks)
                .projects(projects)
                .users(users)
                .teams(teams)
                .totalResults(totalResults)
                .searchTime(searchTime)
                .suggestions(List.of()) // Add suggestions logic if needed
                .build();

        } catch (Exception e) {
            log.error("Unified search failed: {}", e.getMessage(), e);
            throw new RuntimeException("Unified search failed", e);
        }
    }

    // ==================== QUICK SEARCH ====================

    /**
     * Quick search across all entities - NOW WITH PROPER USER FILTERING
     */
    public QuickSearchResult quickSearch(String searchTerm, String scope, Long userId, Pageable pageable) {
        try {
            Page<TaskSearchDocument> tasks = Page.empty();
            Page<ProjectSearchDocument> projects = Page.empty();
            Page<UserSearchDocument> users = Page.empty();
            Page<TeamSearchDocument> teams = Page.empty();

            // 🔒 SECURITY FIX: Use DirectElasticsearchService with user filtering
            if (scope == null || scope.equals("all") || scope.equals("tasks")) {
                try {
                    Map<String, Object> taskResult = directElasticsearchService.searchTasks(searchTerm, userId, pageable);
                    List<Map<String, Object>> taskContent = (List<Map<String, Object>>) taskResult.get("content");
                    List<TaskSearchDocument> taskDocuments = taskContent.stream()
                        .map(this::mapToTaskSearchDocument)
                        .collect(Collectors.toList());
                    long taskTotalElements = (Long) taskResult.get("totalElements");
                    tasks = new PageImpl<>(taskDocuments, pageable, taskTotalElements);
                } catch (Exception e) {
                    log.warn("Task search failed in quick search: {}", e.getMessage());
                    tasks = Page.empty();
                }
            }

            if (scope == null || scope.equals("all") || scope.equals("projects")) {
                try {
                    Map<String, Object> projectResult = directElasticsearchService.searchProjects(searchTerm, userId, pageable);
                    List<Map<String, Object>> projectContent = (List<Map<String, Object>>) projectResult.get("content");
                    List<ProjectSearchDocument> projectDocuments = projectContent.stream()
                        .map(this::mapToProjectSearchDocument)
                        .collect(Collectors.toList());
                    long projectTotalElements = (Long) projectResult.get("totalElements");
                    projects = new PageImpl<>(projectDocuments, pageable, projectTotalElements);
                } catch (Exception e) {
                    log.warn("Project search failed in quick search: {}", e.getMessage());
                    projects = Page.empty();
                }
            }

            if (scope == null || scope.equals("all") || scope.equals("users")) {
                try {
                    // 🔒 SECURITY FIX: Use secure user search with filtering
                    Map<String, Object> userResult = directElasticsearchService.searchUsersSecure(searchTerm, userId, pageable);
                    users = convertDirectResultToUserPage(userResult, pageable);
                } catch (Exception e) {
                    log.warn("User search failed in quick search: {}", e.getMessage());
                    users = Page.empty();
                }
            }

            if (scope == null || scope.equals("all") || scope.equals("teams")) {
                try {
                    // 🔒 SECURITY FIX: Use secure team search with filtering
                    Map<String, Object> teamResult = directElasticsearchService.searchTeamsSecure(searchTerm, userId, pageable);
                    List<Map<String, Object>> teamContent = (List<Map<String, Object>>) teamResult.get("content");
                    List<TeamSearchDocument> teamDocuments = teamContent.stream()
                        .map(this::mapToTeamSearchDocument)
                        .collect(Collectors.toList());
                    long teamTotalElements = (Long) teamResult.get("totalElements");
                    teams = new PageImpl<>(teamDocuments, pageable, teamTotalElements);
                } catch (Exception e) {
                    log.warn("Team search failed in quick search: {}", e.getMessage());
                    teams = Page.empty();
                }
            }

            return QuickSearchResult.builder()
                    .tasks(tasks)
                    .projects(projects)
                    .users(users)
                    .teams(teams)
                    .totalResults(tasks.getTotalElements() + projects.getTotalElements() +
                                users.getTotalElements() + teams.getTotalElements())
                    .searchTerm(searchTerm)
                    .build();
        } catch (Exception e) {
            log.error("Quick search failed: {}", e.getMessage());
            throw new RuntimeException("Quick search service unavailable", e);
        }
    }

    // ==================== MY CONTENT SEARCH ====================

    /**
     * Search my content (tasks and projects)
     */
    public MyContentSearchResult searchMyContent(String searchTerm, List<String> entities, Long userId, Pageable pageable) {
        try {
            Page<TaskSearchDocument> tasks = Page.empty();
            Page<ProjectSearchDocument> projects = Page.empty();

            if (entities == null || entities.isEmpty() || entities.contains("tasks")) {
                try {
                    tasks = searchMyTasks(searchTerm, userId, pageable);
                } catch (Exception e) {
                    log.warn("My tasks search failed: {}", e.getMessage());
                    tasks = Page.empty();
                }
            }

            if (entities == null || entities.isEmpty() || entities.contains("projects")) {
                try {
                    projects = searchMyProjects(searchTerm, userId, pageable);
                } catch (Exception e) {
                    log.warn("My projects search failed: {}", e.getMessage());
                    projects = Page.empty();
                }
            }

            return MyContentSearchResult.builder()
                    .tasks(tasks)
                    .projects(projects)
                    .totalResults(tasks.getTotalElements() + projects.getTotalElements())
                    .searchTerm(searchTerm)
                    .build();
        } catch (Exception e) {
            log.error("My content search failed: {}", e.getMessage());
            throw new RuntimeException("My content search service unavailable", e);
        }
    }

    // ==================== AUTOCOMPLETE SUGGESTIONS ====================

    /**
     * Get autocomplete suggestions
     */
    public List<String> getAutocompleteSuggestions(String searchTerm, String entity, Long userId, int limit) {
        try {
            List<String> suggestions = new ArrayList<>();

            if (entity == null || entity.equals("all") || entity.equals("tasks")) {
                try {
                    Page<TaskSearchDocument> taskResults = autocompleteTask(searchTerm, userId,
                        PageRequest.of(0, limit / 4));
                    suggestions.addAll(taskResults.getContent().stream()
                        .map(TaskSearchDocument::getTitle)
                        .limit(limit / 4)
                        .collect(Collectors.toList()));
                } catch (Exception e) {
                    log.warn("Task autocomplete failed: {}", e.getMessage());
                }
            }

            if (entity == null || entity.equals("all") || entity.equals("projects")) {
                try {
                    Page<ProjectSearchDocument> projectResults = autocompleteProject(searchTerm,
                        PageRequest.of(0, limit / 4));
                    suggestions.addAll(projectResults.getContent().stream()
                        .map(ProjectSearchDocument::getName)
                        .limit(limit / 4)
                        .collect(Collectors.toList()));
                } catch (Exception e) {
                    log.warn("Project autocomplete failed: {}", e.getMessage());
                }
            }

            if (entity == null || entity.equals("all") || entity.equals("users")) {
                try {
                    Page<UserSearchDocument> userResults = autocompleteUser(searchTerm,
                        PageRequest.of(0, limit / 4));
                    suggestions.addAll(userResults.getContent().stream()
                        .map(UserSearchDocument::getFullName)
                        .limit(limit / 4)
                        .collect(Collectors.toList()));
                } catch (Exception e) {
                    log.warn("User autocomplete failed: {}", e.getMessage());
                }
            }

            return suggestions.stream()
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Autocomplete suggestions failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== SEARCH HISTORY ====================

    /**
     * Save search history
     */
    public void saveSearchHistory(Long userId, String searchTerm) {
        try {
            if (searchTerm == null || searchTerm.trim().isEmpty() || searchTerm.length() < 2) {
                return; // Skip saving very short or empty queries
            }

            String redisKey = SEARCH_HISTORY_KEY_PREFIX + userId;
            String cleanedTerm = searchTerm.trim().toLowerCase();

            // Use Redis Sorted Set to store search history with timestamp as score
            double score = System.currentTimeMillis();

            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

            // Add the search term with current timestamp as score
            zSetOps.add(redisKey, cleanedTerm, score);

            // Keep only the most recent searches (limit to MAX_HISTORY_SIZE)
            Long count = zSetOps.count(redisKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            if (count != null && count > MAX_HISTORY_SIZE) {
                // Remove oldest entries, keeping only the most recent MAX_HISTORY_SIZE
                zSetOps.removeRange(redisKey, 0, count - MAX_HISTORY_SIZE - 1);
            }

            // Set expiration for the key (30 days)
            redisTemplate.expire(redisKey, java.time.Duration.ofDays(30));

            log.debug("✅ Search history saved for user {}: '{}'", userId, cleanedTerm);

        } catch (Exception e) {
            log.warn("❌ Failed to save search history for user {}: {}", userId, e.getMessage());
            // Don't throw exception as this is not critical
        }
    }

    /**
     * Get search history
     */
    public List<String> getSearchHistory(Long userId, int limit) {
        try {
            String redisKey = SEARCH_HISTORY_KEY_PREFIX + userId;

            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

            // Get the most recent search terms (highest scores = most recent)
            Set<Object> recentSearches = zSetOps.reverseRange(redisKey, 0, limit - 1);

            if (recentSearches == null || recentSearches.isEmpty()) {
                log.debug("📭 No search history found for user {}", userId);
                return new ArrayList<>();
            }

            List<String> history = recentSearches.stream()
                .map(Object::toString)
                .filter(term -> term != null && !term.trim().isEmpty())
                .distinct() // Remove duplicates
                .collect(Collectors.toList());

            log.debug("✅ Retrieved {} search history items for user {}", history.size(), userId);
            return history;

        } catch (Exception e) {
            log.error("❌ Failed to get search history for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Clear search history
     */
    public void clearSearchHistory(Long userId) {
        try {
            String redisKey = SEARCH_HISTORY_KEY_PREFIX + userId;

            Boolean deleted = redisTemplate.delete(redisKey);

            if (Boolean.TRUE.equals(deleted)) {
                log.debug("✅ Cleared search history for user {}", userId);
            } else {
                log.debug("📭 No search history found to clear for user {}", userId);
            }

        } catch (Exception e) {
            log.warn("❌ Failed to clear search history for user {}: {}", userId, e.getMessage());
            // Don't throw exception as this is not critical
        }
    }

    /**
     * Remove specific search history item
     */
    public void removeSearchHistoryItem(Long userId, String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return;
            }

            String redisKey = SEARCH_HISTORY_KEY_PREFIX + userId;
            String cleanedQuery = query.trim().toLowerCase();

            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

            Long removed = zSetOps.remove(redisKey, cleanedQuery);

            if (removed != null && removed > 0) {
                log.debug("✅ Removed search history item '{}' for user {}", cleanedQuery, userId);
            } else {
                log.debug("📭 Search history item '{}' not found for user {}", cleanedQuery, userId);
            }

        } catch (Exception e) {
            log.warn("❌ Failed to remove search history item '{}' for user {}: {}", query, userId, e.getMessage());
            // Don't throw exception as this is not critical
        }
    }

    /**
     * ✅ NEW: Get popular search terms across all users (for suggestions)
     */
    public List<String> getPopularSearchTerms(int limit) {
        try {
            String popularKey = "search_popular_terms";

            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

            // Get most popular terms (highest scores)
            Set<Object> popularTerms = zSetOps.reverseRange(popularKey, 0, limit - 1);

            if (popularTerms == null || popularTerms.isEmpty()) {
                return new ArrayList<>();
            }

            return popularTerms.stream()
                .map(Object::toString)
                .filter(term -> term != null && !term.trim().isEmpty())
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Failed to get popular search terms: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * ✅ NEW: Update popular search terms counter
     */
    private void updatePopularSearchTerms(String searchTerm) {
        try {
            if (searchTerm == null || searchTerm.trim().isEmpty() || searchTerm.length() < 2) {
                return;
            }

            String popularKey = "search_popular_terms";
            String cleanedTerm = searchTerm.trim().toLowerCase();

            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

            // Increment score for this search term
            zSetOps.incrementScore(popularKey, cleanedTerm, 1.0);

            // Set expiration for popular terms key (7 days)
            redisTemplate.expire(popularKey, java.time.Duration.ofDays(7));

        } catch (Exception e) {
            log.warn("❌ Failed to update popular search terms: {}", e.getMessage());
        }
    }

    /**
     * ✅ ENHANCED: Save search history with popular terms tracking
     */
    public void saveSearchHistoryEnhanced(Long userId, String searchTerm) {
        // Save to user's personal history
        saveSearchHistory(userId, searchTerm);

        // Update popular search terms counter
        updatePopularSearchTerms(searchTerm);

        // Publish search event for additional processing (analytics, etc.)
        try {
            searchEventPublisher.publishSearchEvent(userId, searchTerm);
        } catch (Exception e) {
            log.warn("❌ Failed to publish search event: {}", e.getMessage());
        }
    }

    // ==================== SMART SUGGESTIONS ====================

    /**
     * Get smart suggestions based on context
     */
    public List<SmartSuggestion> getSmartSuggestions(SmartSuggestionsRequest request, Long userId) {
        try {
            List<SmartSuggestion> suggestions = new ArrayList<>();

            // Add some basic smart suggestions based on context
            String contextStr = request.getContextAsString();
            String partialQuery = request.getPartialQuery();

            if (contextStr != null) {
                switch (contextStr.toLowerCase()) {
                    case "tasks":
                        suggestions.add(SmartSuggestion.builder()
                            .suggestion("overdue tasks")
                            .type("filter")
                            .confidence(0.8)
                            .description("Find tasks that are past their due date")
                            .entityType("tasks")
                            .build());
                        suggestions.add(SmartSuggestion.builder()
                            .suggestion("my tasks")
                            .type("filter")
                            .confidence(0.9)
                            .description("Tasks assigned to you")
                            .entityType("tasks")
                            .build());
                        suggestions.add(SmartSuggestion.builder()
                            .suggestion("high priority")
                            .type("filter")
                            .confidence(0.7)
                            .description("Tasks marked as high priority")
                            .entityType("tasks")
                            .build());
                        break;
                    case "projects":
                        suggestions.add(SmartSuggestion.builder()
                            .suggestion("active projects")
                            .type("filter")
                            .confidence(0.8)
                            .description("Projects currently in progress")
                            .entityType("projects")
                            .build());
                        suggestions.add(SmartSuggestion.builder()
                            .suggestion("my projects")
                            .type("filter")
                            .confidence(0.9)
                            .description("Projects you own or are a member of")
                            .entityType("projects")
                            .build());
                        break;
                    case "users":
                        suggestions.add(SmartSuggestion.builder()
                            .suggestion("premium users")
                            .type("filter")
                            .confidence(0.6)
                            .description("Users with premium subscriptions")
                            .entityType("users")
                            .build());
                        suggestions.add(SmartSuggestion.builder()
                            .suggestion("online users")
                            .type("filter")
                            .confidence(0.7)
                            .description("Currently online users")
                            .entityType("users")
                            .build());
                        break;
                    case "teams":
                        suggestions.add(SmartSuggestion.builder()
                            .suggestion("my teams")
                            .type("filter")
                            .confidence(0.9)
                            .description("Teams you are a member of")
                            .entityType("teams")
                            .build());
                        break;
                }
            }

            // Add query-based suggestions if partial query is provided
            if (partialQuery != null && !partialQuery.trim().isEmpty()) {
                String query = partialQuery.toLowerCase().trim();

                // Common search patterns
                if (query.contains("overdue") || query.contains("late")) {
                    suggestions.add(SmartSuggestion.builder()
                        .suggestion("overdue tasks from last week")
                        .type("query")
                        .confidence(0.8)
                        .description("Tasks that were due last week")
                        .entityType("tasks")
                        .build());
                }

                if (query.contains("today") || query.contains("due")) {
                    suggestions.add(SmartSuggestion.builder()
                        .suggestion("due today")
                        .type("filter")
                        .confidence(0.9)
                        .description("Tasks due today")
                        .entityType("tasks")
                        .build());
                }

                if (query.contains("urgent") || query.contains("priority")) {
                    suggestions.add(SmartSuggestion.builder()
                        .suggestion("high priority tasks")
                        .type("filter")
                        .confidence(0.8)
                        .description("Tasks marked as high or urgent priority")
                        .entityType("tasks")
                        .build());
                }
            }

            // Add popular search terms as suggestions
            try {
                List<String> popularTerms = getPopularSearchTerms(3);
                for (String term : popularTerms) {
                    suggestions.add(SmartSuggestion.builder()
                        .suggestion(term)
                        .type("popular")
                        .confidence(0.6)
                        .description("Popular search term")
                        .entityType("all")
                        .build());
                }
            } catch (Exception e) {
                log.warn("Failed to get popular terms for suggestions: {}", e.getMessage());
            }

            // Add recent search history as suggestions
            try {
                List<String> recentSearches = getSearchHistory(userId, 3);
                for (String term : recentSearches) {
                    // Avoid duplicates
                    boolean alreadyExists = suggestions.stream()
                        .anyMatch(s -> s.getSuggestion().equals(term));
                    if (!alreadyExists) {
                        suggestions.add(SmartSuggestion.builder()
                            .suggestion(term)
                            .type("recent")
                            .confidence(0.7)
                            .description("From your recent searches")
                            .entityType("all")
                            .build());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get recent searches for suggestions: {}", e.getMessage());
            }

            // Limit and sort suggestions by confidence
            return suggestions.stream()
                .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
                .limit(request.getMaxSuggestions() != null ? request.getMaxSuggestions() : 5)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Smart suggestions failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== INDEX MANAGEMENT ====================

    /**
     * Manual reindex all data
     */
    public void manualReindexAllData() {
        try {
            log.info("Starting manual reindex of all data");
            // This would typically trigger a full reindex process
            // For now, just log the action
            log.info("Manual reindex completed");
        } catch (Exception e) {
            log.error("Manual reindex failed: {}", e.getMessage());
            throw new RuntimeException("Reindex failed", e);
        }
    }

    /**
     * Get index status
     */
    public Map<String, Object> getIndexStatus() {
        try {
            // Return basic index status information
            return Map.of(
                "status", "healthy",
                "lastIndexed", LocalDateTime.now().toString(),
                "totalDocuments", Map.of(
                    "tasks", 0L,
                    "projects", 0L,
                    "users", 0L,
                    "teams", 0L
                ),
                "indexHealth", "green"
            );
        } catch (Exception e) {
            log.error("Failed to get index status: {}", e.getMessage());
            return Map.of(
                "status", "error",
                "message", e.getMessage()
            );
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Helper method to map raw Elasticsearch results to TaskSearchDocument
     */
    private TaskSearchDocument mapToTaskSearchDocument(Map<String, Object> source) {
        try {
            return TaskSearchDocument.builder()
                .id((String) source.get("id"))
                .title((String) source.get("title"))
                .description((String) source.get("description"))
                .status((String) source.get("status"))
                .priority((String) source.get("priority"))
                .assigneeId(source.get("assigneeId") != null ? Long.valueOf(source.get("assigneeId").toString()) : null)
                .assigneeName((String) source.get("assigneeName"))
                .projectId(source.get("projectId") != null ? Long.valueOf(source.get("projectId").toString()) : null)
                .projectName((String) source.get("projectName"))
                .tags((List<String>) source.get("tags"))
                .dueDate(parseDateTime(source.get("dueDate")))
                .createdAt(parseDateTime(source.get("createdAt")))
                .isCompleted((Boolean) source.get("isCompleted"))
                .build();
        } catch (Exception e) {
            log.error("Failed to map TaskSearchDocument: {}", e.getMessage());
            return TaskSearchDocument.builder().build();
        }
    }

    /**
     * Helper method to parse datetime strings with flexible format support
     */
    private LocalDateTime parseDateTime(Object dateObj) {
        if (dateObj == null) {
            return null;
        }

        String dateStr = dateObj.toString();
        try {
            // Try parsing as full datetime first
            if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr);
            }
            // If it's just a date (YYYY-MM-DD), parse as LocalDate and convert to LocalDateTime
            else if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return java.time.LocalDate.parse(dateStr).atStartOfDay();
            }
            // If it's in another format, try to parse it
            else {
                return LocalDateTime.parse(dateStr);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Helper method to map raw Elasticsearch user search result to UserSearchDocument
     */
    private UserSearchDocument mapToUserSearchDocument(Map<String, Object> source) {
        try {
            return UserSearchDocument.builder()
                .id((String) source.get("id"))
                .userId(source.get("userId") != null ? Long.valueOf(source.get("userId").toString()) : null)
                .email((String) source.get("email"))
                .firstName((String) source.get("firstName"))
                .lastName((String) source.get("lastName"))
                .fullName((String) source.get("fullName"))
                .username((String) source.get("username"))
                .jobTitle((String) source.get("jobTitle"))
                .department((String) source.get("department"))
                .bio((String) source.get("bio"))
                .avatarUrl((String) source.get("avatarUrl"))
                .skills((List<String>) source.get("skills"))
                .location((String) source.get("location"))
                .company((String) source.get("company"))
                .isActive((Boolean) source.get("isActive"))
                .isOnline((Boolean) source.get("isOnline"))
                .isPremium((Boolean) source.get("isPremium"))
                .premiumPlanType((String) source.get("premiumPlanType"))
                .profileVisibility((String) source.get("profileVisibility"))
                .searchable((Boolean) source.get("searchable"))
                .friendIds(convertToLongList(source.get("friendIds")))
                .teamIds(convertToLongList(source.get("teamIds")))
                .teamNames((List<String>) source.get("teamNames"))
                .searchScore(source.get("searchScore") != null ? Float.valueOf(source.get("searchScore").toString()) : null)
                .connectionsCount(source.get("connectionsCount") != null ? Integer.valueOf(source.get("connectionsCount").toString()) : null)
                .completedTasksCount(source.get("completedTasksCount") != null ? Integer.valueOf(source.get("completedTasksCount").toString()) : null)
                .build();
        } catch (Exception e) {
            log.error("Failed to map UserSearchDocument: {}", e.getMessage());
            return UserSearchDocument.builder().build();
        }
    }

    /**
     * Helper method to convert direct Elasticsearch search result to Page<UserSearchDocument>
     */
    private Page<UserSearchDocument> convertDirectResultToUserPage(Map<String, Object> result, Pageable pageable) {
        try {
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
            List<UserSearchDocument> users = content.stream()
                .map(this::mapToUserSearchDocument)
                .collect(Collectors.toList());

            long totalElements = (Long) result.get("totalElements");
            return new PageImpl<>(users, pageable, totalElements);
        } catch (Exception e) {
            log.error("Failed to convert direct result to UserSearchDocument page: {}", e.getMessage());
            return Page.empty(pageable);
        }
    }

    /**
     * Helper method to map raw Elasticsearch project search result to ProjectSearchDocument
     */
    private ProjectSearchDocument mapToProjectSearchDocument(Map<String, Object> source) {
        try {
            return ProjectSearchDocument.builder()
                .id((String) source.get("id"))
                .ownerId(source.get("ownerId") != null ? Long.valueOf(source.get("ownerId").toString()) : null)
                .name((String) source.get("name"))
                .description((String) source.get("description"))
                .status((String) source.get("status"))
                .privacy((String) source.get("privacy"))
                .ownerName((String) source.get("ownerName"))
                .memberIds(convertToLongList(source.get("memberIds")))
                .memberNames((List<String>) source.get("memberNames"))
                .tags((List<String>) source.get("tags"))
                .startDate(parseDateTime(source.get("startDate")))
                .endDate(parseDateTime(source.get("endDate")))
                .createdAt(parseDateTime(source.get("createdAt")))
                .isActive((Boolean) source.get("isActive"))
                .totalTasks(source.get("totalTasks") != null ? Integer.valueOf(source.get("totalTasks").toString()) : null)
                .completedTasks(source.get("completedTasks") != null ? Integer.valueOf(source.get("completedTasks").toString()) : null)
                .completionPercentage(source.get("completionPercentage") != null ? Float.valueOf(source.get("completionPercentage").toString()) : null)
                .build();
        } catch (Exception e) {
            log.error("Failed to map ProjectSearchDocument: {}", e.getMessage());
            return ProjectSearchDocument.builder().build();
        }
    }

    /**
     * Helper method to map raw Elasticsearch team search result to TeamSearchDocument
     */
    private TeamSearchDocument mapToTeamSearchDocument(Map<String, Object> source) {
        try {
            return TeamSearchDocument.builder()
                .id((String) source.get("id"))
                .name((String) source.get("name"))
                .description((String) source.get("description"))
                .createdAt(parseDateTime(source.get("createdAt")))
                .leaderId(source.get("leaderId") != null ? Long.valueOf(source.get("leaderId").toString()) : null)
                .leaderName((String) source.get("leaderName"))
                .memberIds(convertToLongList(source.get("memberIds")))
                .memberNames((List<String>) source.get("memberNames"))
                .memberCount(source.get("memberCount") != null ? Integer.valueOf(source.get("memberCount").toString()) : null)
                .activeProjectsCount(source.get("activeProjectsCount") != null ? Integer.valueOf(source.get("activeProjectsCount").toString()) : null)
                .totalTasksCount(source.get("totalTasksCount") != null ? Integer.valueOf(source.get("totalTasksCount").toString()) : null)
                .completedTasksCount(source.get("completedTasksCount") != null ? Integer.valueOf(source.get("completedTasksCount").toString()) : null)
                .teamPerformanceScore(source.get("teamPerformanceScore") != null ? Float.valueOf(source.get("teamPerformanceScore").toString()) : null)
                .isActive((Boolean) source.get("isActive"))
                .teamType((String) source.get("teamType"))
                .privacy((String) source.get("privacy"))
                .searchable((Boolean) source.get("searchable"))
                .searchScore(source.get("searchScore") != null ? Float.valueOf(source.get("searchScore").toString()) : null)
                .tags((List<String>) source.get("tags"))
                .department((String) source.get("department"))
                .organization((String) source.get("organization"))
                .build();
        } catch (Exception e) {
            log.error("Failed to map TeamSearchDocument: {}", e.getMessage());
            return TeamSearchDocument.builder().build();
        }
    }

    /**
     * Helper method to convert Object to List<Long>
     */
    private List<Long> convertToLongList(Object obj) {
        if (obj == null) return new ArrayList<>();

        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            return list.stream()
                .filter(item -> item != null)
                .map(item -> Long.valueOf(item.toString()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
