package com.example.taskmanagement_backend.search.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.example.taskmanagement_backend.services.ProjectService;
import com.example.taskmanagement_backend.services.TeamService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct Elasticsearch service that bypasses Spring Data Elasticsearch
 * Uses REST client to directly query Elasticsearch without conversion issues
 * NOW WITH PROPER USER-BASED SECURITY FILTERING
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DirectElasticsearchService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String ELASTICSEARCH_BASE_URL = "http://localhost:9200";

    // 🔒 SECURITY: Inject services to get user's accessible IDs
    private final ProjectService projectService;
    private final TeamService teamService;

    /**
     * 🔒 SECURE: Search tasks using direct Elasticsearch REST API with PROPER USER FILTERING
     * Authorization happens at DATABASE QUERY TIME, not in Kafka
     * MATCHES /my-tasks LOGIC: Tasks user created OR assigned to
     */
    public Map<String, Object> searchTasks(String searchTerm, Long userId, Pageable pageable) {
        try {
            String url = ELASTICSEARCH_BASE_URL + "/tasks/_search";

            log.debug("🔒 User {} searching tasks - matching /my-tasks logic", userId);

            Map<String, Object> query;

            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                // 🔒 MATCH /my-tasks LOGIC: Text search + simple user filtering
                query = Map.of(
                    "query", Map.of(
                        "bool", Map.of(
                            "must", List.of(
                                // Text search requirement
                                Map.of("multi_match", Map.of(
                                    "query", searchTerm,
                                    "fields", List.of("title^2", "description", "creatorName")
                                ))
                            ),
                            // 🔒 SIMPLE LOGIC: Match /my-tasks exactly
                            "should", List.of(
                                // 1. User is the creator (matches t.creator = :user)
                                Map.of("term", Map.of("creatorId", userId)),
                                // 2. User is assigned (matches ta.user = :user)
                                Map.of("term", Map.of("assigneeId", userId)),
                                // 3. User is in assignee list (for multiple assignees)
                                Map.of("term", Map.of("visibleToUserIds", userId))
                            ),
                            "minimum_should_match", 1
                        )
                    ),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            } else {
                // 🔒 SIMPLE LOGIC: Match /my-tasks exactly (no text search)
                query = Map.of(
                    "query", Map.of(
                        "bool", Map.of(
                            "should", List.of(
                                // 1. User is the creator
                                Map.of("term", Map.of("creatorId", userId)),
                                // 2. User is assigned
                                Map.of("term", Map.of("assigneeId", userId)),
                                // 3. User is in assignee list
                                Map.of("term", Map.of("visibleToUserIds", userId))
                            ),
                            "minimum_should_match", 1
                        )
                    ),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            }

            log.debug("🔍 Executing SIMPLE task search for user {} (matching /my-tasks logic)", userId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                Map<String, Object> result = parseElasticsearchResponse(jsonResponse, pageable);
                log.debug("✅ SIMPLE task search for user {} returned {} results (matching /my-tasks)",
                    userId, result.get("totalElements"));

                return result;
            } else {
                log.error("❌ Elasticsearch simple task query failed with status: {}", response.getStatusCode());
                return createEmptyResponse(pageable);
            }

        } catch (Exception e) {
            log.error("❌ SIMPLE Elasticsearch task search failed: {}", e.getMessage());
            return createEmptyResponse(pageable);
        }
    }

    /**
     * Search projects using direct Elasticsearch REST API with USER FILTERING
     * ⚠️ SECURITY FIX: Now filters by user ID to prevent data leakage
     */
    public Map<String, Object> searchProjects(String searchTerm, Long userId, Pageable pageable) {
        try {
            String url = ELASTICSEARCH_BASE_URL + "/projects/_search";

            Map<String, Object> query;

            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                // 🔒 SECURITY: Combine text search with user filtering
                query = Map.of(
                    "query", Map.of(
                        "bool", Map.of(
                            "must", List.of(
                                // Text search
                                Map.of("multi_match", Map.of(
                                    "query", searchTerm,
                                    "fields", List.of("name^2", "description", "ownerName")
                                )),
                                // 🔒 CRITICAL: User access filter
                                Map.of("bool", Map.of(
                                    "should", List.of(
                                        // User is the owner
                                        Map.of("term", Map.of("ownerId", userId)),
                                        // User is a member
                                        Map.of("term", Map.of("memberIds", userId))
                                    ),
                                    "minimum_should_match", 1
                                ))
                            )
                        )
                    ),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            } else {
                // 🔒 SECURITY: Match all for user but filter by user access
                query = Map.of(
                    "query", Map.of(
                        "bool", Map.of(
                            "should", List.of(
                                // User is the owner
                                Map.of("term", Map.of("ownerId", userId)),
                                // User is a member
                                Map.of("term", Map.of("memberIds", userId))
                            ),
                            "minimum_should_match", 1
                        )
                    ),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            }

            log.debug("🔍 Searching projects for user {} with query: {}", userId, searchTerm);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                Map<String, Object> result = parseElasticsearchResponse(jsonResponse, pageable);
                log.debug("✅ Project search for user {} returned {} results", userId, result.get("totalElements"));
                return result;
            } else {
                log.error("❌ Elasticsearch project query failed with status: {}", response.getStatusCode());
                return createEmptyResponse(pageable);
            }

        } catch (Exception e) {
            log.error("❌ Direct Elasticsearch project search failed: {}", e.getMessage());
            return createEmptyResponse(pageable);
        }
    }

    /**
     * Get all tasks without any filtering - FOR ADMIN USE ONLY
     * ⚠️ WARNING: This method should only be used for admin/debug purposes
     */
    public Map<String, Object> getAllTasks(Pageable pageable) {
        try {
            String url = ELASTICSEARCH_BASE_URL + "/tasks/_search";

            Map<String, Object> query = Map.of(
                "query", Map.of("match_all", Map.of()),
                "from", pageable.getOffset(),
                "size", pageable.getPageSize()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                return parseElasticsearchResponse(jsonResponse, pageable);
            } else {
                log.error("❌ Elasticsearch getAllTasks failed with status: {}", response.getStatusCode());
                return createEmptyResponse(pageable);
            }

        } catch (Exception e) {
            log.error("❌ Direct Elasticsearch getAllTasks failed: {}", e.getMessage());
            return createEmptyResponse(pageable);
        }
    }

    /**
     * Get index status directly
     */
    public Map<String, Object> getIndexStatus() {
        try {
            Map<String, Object> status = new HashMap<>();

            // Get tasks count
            String tasksUrl = ELASTICSEARCH_BASE_URL + "/tasks/_count";
            ResponseEntity<String> tasksResponse = restTemplate.getForEntity(tasksUrl, String.class);
            if (tasksResponse.getStatusCode().is2xxSuccessful()) {
                JsonNode tasksJson = objectMapper.readTree(tasksResponse.getBody());
                long taskCount = tasksJson.get("count").asLong();
                status.put("tasks", Map.of("count", taskCount, "status", taskCount > 0 ? "populated" : "empty"));
            } else {
                status.put("tasks", Map.of("count", 0, "status", "error"));
            }

            // Get users count
            String usersUrl = ELASTICSEARCH_BASE_URL + "/users/_count";
            ResponseEntity<String> usersResponse = restTemplate.getForEntity(usersUrl, String.class);
            if (usersResponse.getStatusCode().is2xxSuccessful()) {
                JsonNode usersJson = objectMapper.readTree(usersResponse.getBody());
                long userCount = usersJson.get("count").asLong();
                status.put("users", Map.of("count", userCount, "status", userCount > 0 ? "populated" : "empty"));
            } else {
                status.put("users", Map.of("count", 0, "status", "error"));
            }

            // Get teams count
            String teamsUrl = ELASTICSEARCH_BASE_URL + "/teams/_count";
            ResponseEntity<String> teamsResponse = restTemplate.getForEntity(teamsUrl, String.class);
            if (teamsResponse.getStatusCode().is2xxSuccessful()) {
                JsonNode teamsJson = objectMapper.readTree(teamsResponse.getBody());
                long teamCount = teamsJson.get("count").asLong();
                status.put("teams", Map.of("count", teamCount, "status", teamCount > 0 ? "populated" : "empty"));
            } else {
                status.put("teams", Map.of("count", 0, "status", "error"));
            }

            return status;

        } catch (Exception e) {
            log.error("❌ Failed to get index status directly: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Parse Elasticsearch response into our format
     */
    private Map<String, Object> parseElasticsearchResponse(JsonNode response, Pageable pageable) {
        try {
            Map<String, Object> result = new HashMap<>();

            JsonNode hits = response.get("hits");
            long totalHits = hits.get("total").get("value").asLong();

            List<Map<String, Object>> content = new ArrayList<>();
            for (JsonNode hit : hits.get("hits")) {
                Map<String, Object> source = objectMapper.convertValue(hit.get("_source"), Map.class);
                // Remove the problematic _class field
                source.remove("_class");
                content.add(source);
            }

            result.put("content", content);
            result.put("totalElements", totalHits);
            result.put("totalPages", (int) Math.ceil((double) totalHits / pageable.getPageSize()));
            result.put("size", pageable.getPageSize());
            result.put("number", pageable.getPageNumber());
            result.put("hasNext", (pageable.getPageNumber() + 1) * pageable.getPageSize() < totalHits);
            result.put("hasPrevious", pageable.getPageNumber() > 0);

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to parse Elasticsearch response: {}", e.getMessage());
            return createEmptyResponse(pageable);
        }
    }

    /**
     * Create empty response for error cases
     */
    private Map<String, Object> createEmptyResponse(Pageable pageable) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", List.of());
        result.put("totalElements", 0L);
        result.put("totalPages", 0);
        result.put("size", pageable.getPageSize());
        result.put("number", pageable.getPageNumber());
        result.put("hasNext", false);
        result.put("hasPrevious", false);
        return result;
    }

    /**
     * Search users using direct Elasticsearch REST API
     * Bypasses Spring Data conversion issues
     */
    public Map<String, Object> searchUsers(String searchTerm, Pageable pageable) {
        try {
            String url = ELASTICSEARCH_BASE_URL + "/users/_search";

            Map<String, Object> query;
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                // Check if search term looks like an email
                if (searchTerm.contains("@") && searchTerm.contains(".")) {
                    // Email search - use exact match for email field
                    query = Map.of(
                        "query", Map.of(
                            "bool", Map.of(
                                "should", List.of(
                                    Map.of("match", Map.of("email", Map.of("query", searchTerm, "boost", 5.0))),
                                    Map.of("match", Map.of("email.keyword", Map.of("query", searchTerm, "boost", 4.0))),
                                    Map.of("term", Map.of("email.keyword", searchTerm))
                                ),
                                "minimum_should_match", 1
                            )
                        ),
                        "from", pageable.getOffset(),
                        "size", pageable.getPageSize()
                    );
                } else {
                    // Regular user search
                    query = Map.of(
                        "query", Map.of(
                            "bool", Map.of(
                                "should", List.of(
                                    Map.of("match", Map.of("fullName", Map.of("query", searchTerm, "fuzziness", "AUTO", "boost", 2.0))),
                                    Map.of("match", Map.of("firstName", Map.of("query", searchTerm, "boost", 1.5))),
                                    Map.of("match", Map.of("lastName", Map.of("query", searchTerm, "boost", 1.5))),
                                    Map.of("match", Map.of("username", Map.of("query", searchTerm, "boost", 1.0))),
                                    Map.of("match", Map.of("email", Map.of("query", searchTerm, "boost", 1.0))),
                                    Map.of("match", Map.of("jobTitle", searchTerm)),
                                    Map.of("match", Map.of("department", searchTerm))
                                ),
                                "minimum_should_match", 1
                            )
                        ),
                        "from", pageable.getOffset(),
                        "size", pageable.getPageSize()
                    );
                }
            } else {
                // Match all query for empty search
                query = Map.of(
                    "query", Map.of("match_all", Map.of()),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                return parseElasticsearchUserResponse(jsonResponse, pageable);
            } else {
                log.error("❌ Elasticsearch user query failed with status: {}", response.getStatusCode());
                return createEmptyResponse(pageable);
            }

        } catch (Exception e) {
            log.error("❌ Direct Elasticsearch user search failed: {}", e.getMessage(), e);
            return createEmptyResponse(pageable);
        }
    }

    /**
     * 🔒 SECURE: Search users with user filtering for ACL
     * Users can search other users but with appropriate filtering
     */
    public Map<String, Object> searchUsersSecure(String searchTerm, Long userId, Pageable pageable) {
        try {
            String url = ELASTICSEARCH_BASE_URL + "/users/_search";

            Map<String, Object> query;
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                // Check if search term looks like an email
                if (searchTerm.contains("@") && searchTerm.contains(".")) {
                    // Email search with basic filtering
                    query = Map.of(
                        "query", Map.of(
                            "bool", Map.of(
                                "must", List.of(
                                    Map.of("bool", Map.of(
                                        "should", List.of(
                                            Map.of("match", Map.of("email", Map.of("query", searchTerm, "boost", 5.0))),
                                            Map.of("match", Map.of("email.keyword", Map.of("query", searchTerm, "boost", 4.0))),
                                            Map.of("term", Map.of("email.keyword", searchTerm))
                                        ),
                                        "minimum_should_match", 1
                                    ))
                                ),
                                // 🔒 FILTER: Exclude deactivated users or apply privacy settings
                                "must_not", List.of(
                                    Map.of("term", Map.of("isDeactivated", true))
                                )
                            )
                        ),
                        "from", pageable.getOffset(),
                        "size", pageable.getPageSize()
                    );
                } else {
                    // Regular user search with filtering
                    query = Map.of(
                        "query", Map.of(
                            "bool", Map.of(
                                "must", List.of(
                                    Map.of("bool", Map.of(
                                        "should", List.of(
                                            Map.of("match", Map.of("fullName", Map.of("query", searchTerm, "fuzziness", "AUTO", "boost", 2.0))),
                                            Map.of("match", Map.of("firstName", Map.of("query", searchTerm, "boost", 1.5))),
                                            Map.of("match", Map.of("lastName", Map.of("query", searchTerm, "boost", 1.5))),
                                            Map.of("match", Map.of("username", Map.of("query", searchTerm, "boost", 1.0))),
                                            Map.of("match", Map.of("email", Map.of("query", searchTerm, "boost", 1.0))),
                                            Map.of("match", Map.of("jobTitle", searchTerm)),
                                            Map.of("match", Map.of("department", searchTerm))
                                        ),
                                        "minimum_should_match", 1
                                    ))
                                ),
                                // 🔒 FILTER: Basic privacy filtering
                                "must_not", List.of(
                                    Map.of("term", Map.of("isDeactivated", true))
                                )
                            )
                        ),
                        "from", pageable.getOffset(),
                        "size", pageable.getPageSize()
                    );
                }
            } else {
                // Match all with basic filtering for empty search
                query = Map.of(
                    "query", Map.of(
                        "bool", Map.of(
                            "must", Map.of("match_all", Map.of()),
                            "must_not", List.of(
                                Map.of("term", Map.of("isDeactivated", true))
                            )
                        )
                    ),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                return parseElasticsearchUserResponse(jsonResponse, pageable);
            } else {
                log.error("❌ Elasticsearch secure user query failed with status: {}", response.getStatusCode());
                return createEmptyResponse(pageable);
            }

        } catch (Exception e) {
            log.error("❌ Direct Elasticsearch secure user search failed: {}", e.getMessage(), e);
            return createEmptyResponse(pageable);
        }
    }

    /**
     * Search user by exact email using direct Elasticsearch REST API
     */
    public Map<String, Object> searchUserByEmail(String email) {
        try {
            String url = ELASTICSEARCH_BASE_URL + "/users/_search";

            Map<String, Object> query = Map.of(
                "query", Map.of(
                    "bool", Map.of(
                        "should", List.of(
                            Map.of("term", Map.of("email.keyword", email)),
                            Map.of("match", Map.of("email", email))
                        ),
                        "minimum_should_match", 1
                    )
                ),
                "size", 1
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                JsonNode hits = jsonResponse.get("hits");

                if (hits.get("total").get("value").asLong() > 0) {
                    JsonNode hit = hits.get("hits").get(0);
                    Map<String, Object> source = objectMapper.convertValue(hit.get("_source"), Map.class);
                    source.remove("_class");
                    source.put("id", hit.get("_id").asText());

                    return Map.of(
                        "found", true,
                        "user", source
                    );
                } else {
                    return Map.of("found", false);
                }
            } else {
                log.error("❌ Elasticsearch email search failed with status: {}", response.getStatusCode());
                return Map.of("found", false);
            }

        } catch (Exception e) {
            log.error("❌ Direct Elasticsearch email search failed: {}", e.getMessage(), e);
            return Map.of("found", false);
        }
    }

    /**
     * Search projects using direct Elasticsearch REST API
     * Bypasses Spring Data conversion issues
     */
    public Map<String, Object> searchProjects(String searchTerm, Pageable pageable) {
        try {
            String url = ELASTICSEARCH_BASE_URL + "/projects/_search";

            Map<String, Object> query;
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                // Multi-match search for projects
                query = Map.of(
                    "query", Map.of(
                        "bool", Map.of(
                            "should", List.of(
                                Map.of("match", Map.of("name", Map.of("query", searchTerm, "boost", 2.0))),
                                Map.of("match", Map.of("description", Map.of("query", searchTerm, "fuzziness", "AUTO", "boost", 1.5))),
                                Map.of("match", Map.of("creatorName", Map.of("query", searchTerm, "boost", 1.0))),
                                Map.of("match", Map.of("teamName", searchTerm)),
                                Map.of("match", Map.of("tags", searchTerm))
                            ),
                            "minimum_should_match", 1
                        )
                    ),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            } else {
                // Match all query for empty search
                query = Map.of(
                    "query", Map.of("match_all", Map.of()),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                return parseElasticsearchProjectResponse(jsonResponse, pageable);
            } else {
                log.error("❌ Elasticsearch project query failed with status: {}", response.getStatusCode());
                return createEmptyResponse(pageable);
            }

        } catch (Exception e) {
            log.error("❌ Direct Elasticsearch project search failed: {}", e.getMessage(), e);
            return createEmptyResponse(pageable);
        }
    }

    /**
     * Search teams using direct Elasticsearch REST API
     * Bypasses Spring Data conversion issues
     */
    public Map<String, Object> searchTeams(String searchTerm, Pageable pageable) {
        try {
            String url = ELASTICSEARCH_BASE_URL + "/teams/_search";

            Map<String, Object> query;
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                // Multi-match search for teams
                query = Map.of(
                    "query", Map.of(
                        "bool", Map.of(
                            "should", List.of(
                                Map.of("match", Map.of("name", Map.of("query", searchTerm, "boost", 2.0))),
                                Map.of("match", Map.of("description", Map.of("query", searchTerm, "fuzziness", "AUTO", "boost", 1.5))),
                                Map.of("match", Map.of("creatorName", Map.of("query", searchTerm, "boost", 1.0))),
                                Map.of("match", Map.of("department", searchTerm)),
                                Map.of("match", Map.of("memberNames", searchTerm))
                            ),
                            "minimum_should_match", 1
                        )
                    ),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            } else {
                // Match all query for empty search
                query = Map.of(
                    "query", Map.of("match_all", Map.of()),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                return parseElasticsearchTeamResponse(jsonResponse, pageable);
            } else {
                log.error("❌ Elasticsearch team query failed with status: {}", response.getStatusCode());
                return createEmptyResponse(pageable);
            }

        } catch (Exception e) {
            log.error("❌ Direct Elasticsearch team search failed: {}", e.getMessage(), e);
            return createEmptyResponse(pageable);
        }
    }

    /**
     * 🔒 SECURE: Search teams with user filtering for ACL
     * Teams user can see based on membership or public teams
     */
    public Map<String, Object> searchTeamsSecure(String searchTerm, Long userId, Pageable pageable) {
        try {
            String url = ELASTICSEARCH_BASE_URL + "/teams/_search";

            // Check if userId is null and handle it gracefully
            if (userId == null) {
                // If userId is null, log a warning
                System.err.println("⚠️ Warning: userId is null in searchTeamsSecure - limiting to only public teams");
            }

            Map<String, Object> query;
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                // Use HashMap instead of Map.of to handle null values safely
                Map<String, Object> queryMap = new HashMap<>();
                Map<String, Object> boolMap = new HashMap<>();

                // Build the must clause for text search
                List<Map<String, Object>> mustList = new ArrayList<>();
                Map<String, Object> textSearchBool = new HashMap<>();
                List<Map<String, Object>> shouldList = new ArrayList<>();

                // Add search term matches
                shouldList.add(Map.of("match", Map.of("name", Map.of("query", searchTerm, "boost", 2.0))));
                shouldList.add(Map.of("match", Map.of("description", Map.of("query", searchTerm, "fuzziness", "AUTO", "boost", 1.5))));
                shouldList.add(Map.of("match", Map.of("creatorName", Map.of("query", searchTerm, "boost", 1.0))));
                shouldList.add(Map.of("match", Map.of("department", searchTerm)));
                shouldList.add(Map.of("match", Map.of("memberNames", searchTerm)));

                textSearchBool.put("should", shouldList);
                textSearchBool.put("minimum_should_match", 1);

                Map<String, Object> mustBoolQuery = new HashMap<>();
                mustBoolQuery.put("bool", textSearchBool);
                mustList.add(mustBoolQuery);

                // Build the filter clause for access control
                List<Map<String, Object>> filterShouldList = new ArrayList<>();

                // Add creator/owner and member conditions only if userId is not null
                if (userId != null) {
                    filterShouldList.add(Map.of("term", Map.of("creatorId", userId)));
                    filterShouldList.add(Map.of("term", Map.of("memberIds", userId)));
                }

                // Always allow public teams
                filterShouldList.add(Map.of("bool", Map.of("must_not", Map.of("term", Map.of("privacy", "PRIVATE")))));

                // Put all parts together
                boolMap.put("must", mustList);
                boolMap.put("should", filterShouldList);
                boolMap.put("minimum_should_match", 1);

                queryMap.put("bool", boolMap);

                // Build the final query
                Map<String, Object> finalQuery = new HashMap<>();
                finalQuery.put("query", queryMap);
                finalQuery.put("from", pageable.getOffset());
                finalQuery.put("size", pageable.getPageSize());

                query = finalQuery;
            } else {
                // Match all teams user has access to (also using HashMap for safety)
                Map<String, Object> finalQuery = new HashMap<>();
                Map<String, Object> boolQuery = new HashMap<>();
                List<Map<String, Object>> shouldList = new ArrayList<>();

                // Add access control conditions
                if (userId != null) {
                    shouldList.add(Map.of("term", Map.of("creatorId", userId)));
                    shouldList.add(Map.of("term", Map.of("memberIds", userId)));
                }

                // Always include public teams
                shouldList.add(Map.of("bool", Map.of("must_not", Map.of("term", Map.of("privacy", "PRIVATE")))));

                Map<String, Object> bool = new HashMap<>();
                bool.put("should", shouldList);
                bool.put("minimum_should_match", 1);

                boolQuery.put("bool", bool);
                finalQuery.put("query", boolQuery);
                finalQuery.put("from", pageable.getOffset());
                finalQuery.put("size", pageable.getPageSize());

                query = finalQuery;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                return parseElasticsearchTeamResponse(jsonResponse, pageable);
            } else {
                log.error("❌ Elasticsearch secure team query failed with status: {}", response.getStatusCode());
                return createEmptyResponse(pageable);
            }

        } catch (Exception e) {
            log.error("❌ Direct Elasticsearch secure team search failed: {}", e.getMessage(), e);
            return createEmptyResponse(pageable);
        }
    }

    /**
     * Parse Elasticsearch project response into our format
     */
    private Map<String, Object> parseElasticsearchProjectResponse(JsonNode response, Pageable pageable) {
        try {
            Map<String, Object> result = new HashMap<>();

            JsonNode hits = response.get("hits");
            long totalHits = hits.get("total").get("value").asLong();

            List<Map<String, Object>> content = new ArrayList<>();
            for (JsonNode hit : hits.get("hits")) {
                Map<String, Object> source = objectMapper.convertValue(hit.get("_source"), Map.class);
                // Remove the problematic _class field
                source.remove("_class");
                // Add the document ID
                source.put("id", hit.get("_id").asText());

                // Handle date fields safely - convert them to strings if they exist
                if (source.containsKey("createdAt")) {
                    Object createdAt = source.get("createdAt");
                    if (createdAt != null) {
                        source.put("createdAt", createdAt.toString());
                    }
                }
                if (source.containsKey("updatedAt")) {
                    Object updatedAt = source.get("updatedAt");
                    if (updatedAt != null) {
                        source.put("updatedAt", updatedAt.toString());
                    }
                }
                if (source.containsKey("startDate")) {
                    Object startDate = source.get("startDate");
                    if (startDate != null) {
                        source.put("startDate", startDate.toString());
                    }
                }
                if (source.containsKey("endDate")) {
                    Object endDate = source.get("endDate");
                    if (endDate != null) {
                        source.put("endDate", endDate.toString());
                    }
                }

                content.add(source);
            }

            result.put("content", content);
            result.put("totalElements", totalHits);
            result.put("totalPages", (int) Math.ceil((double) totalHits / pageable.getPageSize()));
            result.put("size", pageable.getPageSize());
            result.put("number", pageable.getPageNumber());
            result.put("hasNext", (pageable.getPageNumber() + 1) * pageable.getPageSize() < totalHits);
            result.put("hasPrevious", pageable.getPageNumber() > 0);

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to parse Elasticsearch project response: {}", e.getMessage());
            return createEmptyResponse(pageable);
        }
    }

    /**
     * Parse Elasticsearch team response into our format
     */
    private Map<String, Object> parseElasticsearchTeamResponse(JsonNode response, Pageable pageable) {
        try {
            Map<String, Object> result = new HashMap<>();

            JsonNode hits = response.get("hits");
            long totalHits = hits.get("total").get("value").asLong();

            List<Map<String, Object>> content = new ArrayList<>();
            for (JsonNode hit : hits.get("hits")) {
                Map<String, Object> source = objectMapper.convertValue(hit.get("_source"), Map.class);
                // Remove the problematic _class field
                source.remove("_class");
                // Add the document ID
                source.put("id", hit.get("_id").asText());

                // Handle date fields safely - convert them to strings if they exist
                if (source.containsKey("createdAt")) {
                    Object createdAt = source.get("createdAt");
                    if (createdAt != null) {
                        source.put("createdAt", createdAt.toString());
                    }
                }
                if (source.containsKey("updatedAt")) {
                    Object updatedAt = source.get("updatedAt");
                    if (updatedAt != null) {
                        source.put("updatedAt", updatedAt.toString());
                    }
                }

                content.add(source);
            }

            result.put("content", content);
            result.put("totalElements", totalHits);
            result.put("totalPages", (int) Math.ceil((double) totalHits / pageable.getPageSize()));
            result.put("size", pageable.getPageSize());
            result.put("number", pageable.getPageNumber());
            result.put("hasNext", (pageable.getPageNumber() + 1) * pageable.getPageSize() < totalHits);
            result.put("hasPrevious", pageable.getPageNumber() > 0);

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to parse Elasticsearch team response: {}", e.getMessage());
            return createEmptyResponse(pageable);
        }
    }

    /**
     * Parse Elasticsearch user response into our format
     */
    private Map<String, Object> parseElasticsearchUserResponse(JsonNode response, Pageable pageable) {
        try {
            Map<String, Object> result = new HashMap<>();

            JsonNode hits = response.get("hits");
            long totalHits = hits.get("total").get("value").asLong();

            List<Map<String, Object>> content = new ArrayList<>();
            for (JsonNode hit : hits.get("hits")) {
                Map<String, Object> source = objectMapper.convertValue(hit.get("_source"), Map.class);
                // Remove the problematic _class field
                source.remove("_class");
                // Add the document ID
                source.put("id", hit.get("_id").asText());

                // Handle date fields safely - convert them to strings if they exist
                if (source.containsKey("createdAt")) {
                    Object createdAt = source.get("createdAt");
                    if (createdAt != null) {
                        source.put("createdAt", createdAt.toString());
                    }
                }
                if (source.containsKey("updatedAt")) {
                    Object updatedAt = source.get("updatedAt");
                    if (updatedAt != null) {
                        source.put("updatedAt", updatedAt.toString());
                    }
                }
                if (source.containsKey("lastLogin")) {
                    Object lastLogin = source.get("lastLogin");
                    if (lastLogin != null) {
                        source.put("lastLogin", lastLogin.toString());
                    }
                }

                content.add(source);
            }

            result.put("content", content);
            result.put("totalElements", totalHits);
            result.put("totalPages", (int) Math.ceil((double) totalHits / pageable.getPageSize()));
            result.put("size", pageable.getPageSize());
            result.put("number", pageable.getPageNumber());
            result.put("hasNext", (pageable.getPageNumber() + 1) * pageable.getPageSize() < totalHits);
            result.put("hasPrevious", pageable.getPageNumber() > 0);

            return result;

        } catch (Exception e) {
            log.error("❌ Failed to parse Elasticsearch user response: {}", e.getMessage());
            return createEmptyResponse(pageable);
        }
    }

    /**
     * Search projects directly via Elasticsearch REST API
     */
    public Map<String, Object> searchProjectsDirect(String searchTerm, Pageable pageable) {
        try {
            String url = ELASTICSEARCH_BASE_URL + "/projects/_search";

            Map<String, Object> query;
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                // Multi-match search for projects
                query = Map.of(
                    "query", Map.of(
                        "bool", Map.of(
                            "should", List.of(
                                Map.of("match", Map.of("name", Map.of("query", searchTerm, "boost", 2.0))),
                                Map.of("match", Map.of("description", Map.of("query", searchTerm, "fuzziness", "AUTO", "boost", 1.5))),
                                Map.of("match", Map.of("creatorName", Map.of("query", searchTerm, "boost", 1.0))),
                                Map.of("match", Map.of("teamName", searchTerm)),
                                Map.of("match", Map.of("tags", searchTerm))
                            ),
                            "minimum_should_match", 1
                        )
                    ),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            } else {
                // Match all query for empty search
                query = Map.of(
                    "query", Map.of("match_all", Map.of()),
                    "from", pageable.getOffset(),
                    "size", pageable.getPageSize()
                );
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                return parseElasticsearchProjectResponse(jsonResponse, pageable);
            } else {
                log.error("❌ Elasticsearch project query failed with status: {}", response.getStatusCode());
                return createEmptyResponse(pageable);
            }

        } catch (Exception e) {
            log.error("❌ Direct Elasticsearch project search failed: {}", e.getMessage(), e);
            return createEmptyResponse(pageable);
        }
    }

    /**
     * 🔒 SECURITY: Get all project IDs that user has access to
     * This is where REAL authorization happens - at database query time
     */
    private List<Long> getUserAccessibleProjectIds(Long userId) {
        try {
            List<Long> projectIds = new ArrayList<>();

            // Get projects where user is owner
            projectIds.addAll(projectService.getProjectIdsByOwnerId(userId));

            // Get projects where user is member
            projectIds.addAll(projectService.getProjectIdsByMemberId(userId));

            log.debug("🔒 User {} has access to projects: {}", userId, projectIds);
            return projectIds;
        } catch (Exception e) {
            log.error("❌ Failed to get accessible project IDs for user {}: {}", userId, e.getMessage());
            return new ArrayList<>(); // Return empty list for security
        }
    }

    /**
     * 🔒 SECURITY: Get all team IDs that user has access to
     * This is where REAL authorization happens - at database query time
     */
    private List<Long> getUserAccessibleTeamIds(Long userId) {
        try {
            List<Long> teamIds = new ArrayList<>();

            // Get teams where user is creator
            teamIds.addAll(teamService.getTeamIdsByCreatorId(userId));

            // Get teams where user is member
            teamIds.addAll(teamService.getTeamIdsByMemberId(userId));

            log.debug("🔒 User {} has access to teams: {}", userId, teamIds);
            return teamIds;
        } catch (Exception e) {
            log.error("❌ Failed to get accessible team IDs for user {}: {}", userId, e.getMessage());
            return new ArrayList<>(); // Return empty list for security
        }
    }
}
