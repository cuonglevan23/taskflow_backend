package com.example.taskmanagement_backend.search.services;

import com.example.taskmanagement_backend.entities.*;
import com.example.taskmanagement_backend.events.*;
import com.example.taskmanagement_backend.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Kafka consumer service for processing search index events
 * Handles real-time indexing based on entity changes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchEventConsumer {

    private final SearchIndexingService searchIndexingService;
    private final TaskJpaRepository taskRepository;
    private final ProjectJpaRepository projectRepository;
    private final UserJpaRepository userRepository;
    private final TeamJpaRepository teamRepository;

    // ==================== TASK EVENT CONSUMERS ====================

    @KafkaListener(topics = "search.task.events", groupId = "search-indexer-group")
    @Transactional(readOnly = true)
    public void handleTaskEvent(SearchIndexEvent event) {
        try {
            log.debug("📥 Processing task search event: {} for task {}", event.getEventType(), event.getEntityId());

            switch (event.getEventType()) {
                case "BULK_REINDEX" -> {
                    List<Task> allTasks = taskRepository.findAll();
                    searchIndexingService.bulkIndexTasks(allTasks);
                    log.info("🔄 Bulk re-indexed {} tasks", allTasks.size());
                }
                case "CREATE", "UPDATE" -> {
                    Long taskId = Long.parseLong(event.getEntityId());
                    Task task = taskRepository.findById(taskId).orElse(null);
                    if (task != null) {
                        searchIndexingService.indexTask(task);
                        log.debug("✅ Indexed task: {}", taskId);
                    } else {
                        log.warn("⚠️ Task not found for indexing: {}", taskId);
                    }
                }
                case "DELETE" -> {
                    Long taskId = Long.parseLong(event.getEntityId());
                    searchIndexingService.deleteTaskFromIndex(taskId);
                    log.debug("🗑️ Deleted task from index: {}", taskId);
                }
                default -> log.warn("⚠️ Unknown task event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("❌ Failed to process task search event: {}", e.getMessage(), e);
        }
    }

    // ==================== PROJECT EVENT CONSUMERS ====================

    @KafkaListener(topics = "search.project.events", groupId = "search-indexer-group")
    @Transactional(readOnly = true)
    public void handleProjectEvent(SearchIndexEvent event) {
        try {
            log.debug("📥 Processing project search event: {} for project {}", event.getEventType(), event.getEntityId());

            switch (event.getEventType()) {
                case "BULK_REINDEX" -> {
                    List<Project> allProjects = projectRepository.findAll();
                    searchIndexingService.bulkIndexProjects(allProjects);
                    log.info("🔄 Bulk re-indexed {} projects", allProjects.size());
                }
                case "CREATE", "UPDATE" -> {
                    Long projectId = Long.parseLong(event.getEntityId());
                    Project project = projectRepository.findById(projectId).orElse(null);
                    if (project != null) {
                        searchIndexingService.indexProject(project);
                        log.debug("✅ Indexed project: {}", projectId);
                    } else {
                        log.warn("⚠️ Project not found for indexing: {}", projectId);
                    }
                }
                case "DELETE" -> {
                    Long projectId = Long.parseLong(event.getEntityId());
                    searchIndexingService.deleteProjectFromIndex(projectId);
                    log.debug("🗑️ Deleted project from index: {}", projectId);
                }
                default -> log.warn("⚠️ Unknown project event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("❌ Failed to process project search event: {}", e.getMessage(), e);
        }
    }

    // ==================== USER EVENT CONSUMERS ====================

    @KafkaListener(topics = "search.user.events", groupId = "search-indexer-group")
    @Transactional(readOnly = true)
    public void handleUserEvent(SearchIndexEvent event) {
        try {
            log.debug("📥 Processing user search event: {} for user {}", event.getEventType(), event.getEntityId());

            switch (event.getEventType()) {
                case "BULK_REINDEX" -> {
                    List<User> allUsers = userRepository.findAll();
                    searchIndexingService.bulkIndexUsers(allUsers);
                    log.info("🔄 Bulk re-indexed {} users", allUsers.size());
                }
                case "CREATE", "UPDATE" -> {
                    Long userId = Long.parseLong(event.getEntityId());
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        searchIndexingService.indexUser(user);
                        log.debug("✅ Indexed user: {}", userId);
                    } else {
                        log.warn("⚠️ User not found for indexing: {}", userId);
                    }
                }
                case "DELETE" -> {
                    Long userId = Long.parseLong(event.getEntityId());
                    searchIndexingService.deleteUserFromIndex(userId);
                    log.debug("🗑️ Deleted user from index: {}", userId);
                }
                default -> log.warn("⚠️ Unknown user event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("❌ Failed to process user search event: {}", e.getMessage(), e);
        }
    }

    // ==================== TEAM EVENT CONSUMERS ====================

    @KafkaListener(topics = "search.team.events", groupId = "search-indexer-group")
    @Transactional(readOnly = true)
    public void handleTeamEvent(SearchIndexEvent event) {
        try {
            log.debug("📥 Processing team search event: {} for team {}", event.getEventType(), event.getEntityId());

            switch (event.getEventType()) {
                case "BULK_REINDEX" -> {
                    List<Team> allTeams = teamRepository.findAll();
                    searchIndexingService.bulkIndexTeams(allTeams);
                    log.info("🔄 Bulk re-indexed {} teams", allTeams.size());
                }
                case "CREATE", "UPDATE" -> {
                    Long teamId = Long.parseLong(event.getEntityId());
                    Team team = teamRepository.findById(teamId).orElse(null);
                    if (team != null) {
                        searchIndexingService.indexTeam(team);
                        log.debug("✅ Indexed team: {}", teamId);
                    } else {
                        log.warn("⚠️ Team not found for indexing: {}", teamId);
                    }
                }
                case "DELETE" -> {
                    Long teamId = Long.parseLong(event.getEntityId());
                    searchIndexingService.deleteTeamFromIndex(teamId);
                    log.debug("🗑️ Deleted team from index: {}", teamId);
                }
                default -> log.warn("⚠️ Unknown team event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("❌ Failed to process team search event: {}", e.getMessage(), e);
        }
    }

    // ==================== BATCH PROCESSING ====================

    /**
     * Handle batch events for better performance during bulk operations
     */
    @KafkaListener(topics = "search.batch.events", groupId = "search-indexer-group")
    @Transactional(readOnly = true)
    public void handleBatchEvent(SearchIndexEvent event) {
        try {
            log.info("📥 Processing batch search event: {} for {}", event.getEventType(), event.getEntityType());

            switch (event.getEntityType()) {
                case "TASK" -> {
                    List<Task> tasks = taskRepository.findAll();
                    searchIndexingService.bulkIndexTasks(tasks);
                    log.info("🔄 Batch processed {} tasks", tasks.size());
                }
                case "PROJECT" -> {
                    List<Project> projects = projectRepository.findAll();
                    searchIndexingService.bulkIndexProjects(projects);
                    log.info("🔄 Batch processed {} projects", projects.size());
                }
                case "USER" -> {
                    List<User> users = userRepository.findAll();
                    searchIndexingService.bulkIndexUsers(users);
                    log.info("🔄 Batch processed {} users", users.size());
                }
                case "TEAM" -> {
                    List<Team> teams = teamRepository.findAll();
                    searchIndexingService.bulkIndexTeams(teams);
                    log.info("🔄 Batch processed {} teams", teams.size());
                }
                default -> log.warn("⚠️ Unknown entity type for batch processing: {}", event.getEntityType());
            }
        } catch (Exception e) {
            log.error("❌ Failed to process batch search event: {}", e.getMessage(), e);
        }
    }
}
