package com.example.taskmanagement_backend.search.services;

import com.example.taskmanagement_backend.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for publishing search index events to Kafka
 * Handles real-time event publishing for all search entities
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Kafka topic names
    private static final String TASK_SEARCH_TOPIC = "search.task.events";
    private static final String PROJECT_SEARCH_TOPIC = "search.project.events";
    private static final String USER_SEARCH_TOPIC = "search.user.events";
    private static final String TEAM_SEARCH_TOPIC = "search.team.events";

    // ==================== TASK EVENTS ====================

    /**
     * Publish task created event
     */
    public void publishTaskCreated(Long taskId, Long userId) {
        TaskSearchIndexEvent event = new TaskSearchIndexEvent("CREATE", taskId, userId);
        publishEvent(TASK_SEARCH_TOPIC, taskId.toString(), event);
        log.debug("📤 Published TASK_CREATED event: {}", taskId);
    }

    /**
     * Publish task updated event
     */
    public void publishTaskUpdated(Long taskId, Long userId) {
        TaskSearchIndexEvent event = new TaskSearchIndexEvent("UPDATE", taskId, userId);
        publishEvent(TASK_SEARCH_TOPIC, taskId.toString(), event);
        log.debug("📤 Published TASK_UPDATED event: {}", taskId);
    }

    /**
     * Publish task deleted event
     */
    public void publishTaskDeleted(Long taskId, Long userId) {
        TaskSearchIndexEvent event = new TaskSearchIndexEvent("DELETE", taskId, userId);
        publishEvent(TASK_SEARCH_TOPIC, taskId.toString(), event);
        log.debug("📤 Published TASK_DELETED event: {}", taskId);
    }

    /**
     * Publish task assignment changed event
     */
    public void publishTaskAssignmentChanged(Long taskId, Long userId) {
        TaskSearchIndexEvent event = new TaskSearchIndexEvent("UPDATE", taskId, userId);
        publishEvent(TASK_SEARCH_TOPIC, taskId.toString(), event);
        log.debug("📤 Published TASK_ASSIGNMENT_CHANGED event: {}", taskId);
    }

    /**
     * Publish task status changed event
     */
    public void publishTaskStatusChanged(Long taskId, Long userId) {
        TaskSearchIndexEvent event = new TaskSearchIndexEvent("UPDATE", taskId, userId);
        publishEvent(TASK_SEARCH_TOPIC, taskId.toString(), event);
        log.debug("📤 Published TASK_STATUS_CHANGED event: {}", taskId);
    }

    // ==================== PROJECT EVENTS ====================

    /**
     * Publish project created event
     */
    public void publishProjectCreated(Long projectId, Long userId) {
        ProjectSearchIndexEvent event = new ProjectSearchIndexEvent("CREATE", projectId, userId);
        publishEvent(PROJECT_SEARCH_TOPIC, projectId.toString(), event);
        log.debug("📤 Published PROJECT_CREATED event: {}", projectId);
    }

    /**
     * Publish project updated event
     */
    public void publishProjectUpdated(Long projectId, Long userId) {
        ProjectSearchIndexEvent event = new ProjectSearchIndexEvent("UPDATE", projectId, userId);
        publishEvent(PROJECT_SEARCH_TOPIC, projectId.toString(), event);
        log.debug("📤 Published PROJECT_UPDATED event: {}", projectId);
    }

    /**
     * Publish project deleted event
     */
    public void publishProjectDeleted(Long projectId, Long userId) {
        ProjectSearchIndexEvent event = new ProjectSearchIndexEvent("DELETE", projectId, userId);
        publishEvent(PROJECT_SEARCH_TOPIC, projectId.toString(), event);
        log.debug("📤 Published PROJECT_DELETED event: {}", projectId);
    }

    /**
     * Publish project member changed event
     */
    public void publishProjectMemberChanged(Long projectId, Long userId) {
        ProjectSearchIndexEvent event = new ProjectSearchIndexEvent("UPDATE", projectId, userId);
        publishEvent(PROJECT_SEARCH_TOPIC, projectId.toString(), event);
        log.debug("📤 Published PROJECT_MEMBER_CHANGED event: {}", projectId);
    }

    // ==================== USER EVENTS ====================

    /**
     * Publish user profile updated event
     */
    public void publishUserProfileUpdated(Long userIdEntity, Long triggeredByUserId) {
        try {
            // Create UserSearchIndexEvent using reflection since it's package-private
            SearchIndexEvent event = new SearchIndexEvent("UPDATE", "USER", userIdEntity.toString(), triggeredByUserId);
            publishEvent(USER_SEARCH_TOPIC, userIdEntity.toString(), event);
            log.debug("📤 Published USER_PROFILE_UPDATED event: {}", userIdEntity);
        } catch (Exception e) {
            log.error("❌ Failed to publish USER_PROFILE_UPDATED event: {}", e.getMessage());
        }
    }

    /**
     * Publish user created event
     */
    public void publishUserCreated(Long userIdEntity, Long triggeredByUserId) {
        try {
            SearchIndexEvent event = new SearchIndexEvent("CREATE", "USER", userIdEntity.toString(), triggeredByUserId);
            publishEvent(USER_SEARCH_TOPIC, userIdEntity.toString(), event);
            log.debug("📤 Published USER_CREATED event: {}", userIdEntity);
        } catch (Exception e) {
            log.error("❌ Failed to publish USER_CREATED event: {}", e.getMessage());
        }
    }

    /**
     * Publish user deleted event
     */
    public void publishUserDeleted(Long userIdEntity, Long triggeredByUserId) {
        try {
            SearchIndexEvent event = new SearchIndexEvent("DELETE", "USER", userIdEntity.toString(), triggeredByUserId);
            publishEvent(USER_SEARCH_TOPIC, userIdEntity.toString(), event);
            log.debug("📤 Published USER_DELETED event: {}", userIdEntity);
        } catch (Exception e) {
            log.error("❌ Failed to publish USER_DELETED event: {}", e.getMessage());
        }
    }

    // ==================== TEAM EVENTS ====================

    /**
     * Publish team created event
     */
    public void publishTeamCreated(Long teamId, Long userId) {
        try {
            SearchIndexEvent event = new SearchIndexEvent("CREATE", "TEAM", teamId.toString(), userId);
            publishEvent(TEAM_SEARCH_TOPIC, teamId.toString(), event);
            log.debug("📤 Published TEAM_CREATED event: {}", teamId);
        } catch (Exception e) {
            log.error("❌ Failed to publish TEAM_CREATED event: {}", e.getMessage());
        }
    }

    /**
     * Publish team updated event
     */
    public void publishTeamUpdated(Long teamId, Long userId) {
        try {
            SearchIndexEvent event = new SearchIndexEvent("UPDATE", "TEAM", teamId.toString(), userId);
            publishEvent(TEAM_SEARCH_TOPIC, teamId.toString(), event);
            log.debug("📤 Published TEAM_UPDATED event: {}", teamId);
        } catch (Exception e) {
            log.error("❌ Failed to publish TEAM_UPDATED event: {}", e.getMessage());
        }
    }

    /**
     * Publish team deleted event
     */
    public void publishTeamDeleted(Long teamId, Long userId) {
        try {
            SearchIndexEvent event = new SearchIndexEvent("DELETE", "TEAM", teamId.toString(), userId);
            publishEvent(TEAM_SEARCH_TOPIC, teamId.toString(), event);
            log.debug("📤 Published TEAM_DELETED event: {}", teamId);
        } catch (Exception e) {
            log.error("❌ Failed to publish TEAM_DELETED event: {}", e.getMessage());
        }
    }

    /**
     * Publish team member changed event
     */
    public void publishTeamMemberChanged(Long teamId, Long userId) {
        try {
            SearchIndexEvent event = new SearchIndexEvent("UPDATE", "TEAM", teamId.toString(), userId);
            publishEvent(TEAM_SEARCH_TOPIC, teamId.toString(), event);
            log.debug("📤 Published TEAM_MEMBER_CHANGED event: {}", teamId);
        } catch (Exception e) {
            log.error("❌ Failed to publish TEAM_MEMBER_CHANGED event: {}", e.getMessage());
        }
    }

    // ==================== SEARCH HISTORY EVENTS ====================

    /**
     * Publish search event for history tracking
     */
    public void publishSearchEvent(Long userId, String query) {
        try {
            SearchHistoryEvent event = new SearchHistoryEvent(userId, query, System.currentTimeMillis());
            publishEvent("search.history.events", userId.toString(), event);
            log.debug("📤 Published SEARCH_HISTORY event: user={}, query='{}'", userId, query);
        } catch (Exception e) {
            log.error("❌ Failed to publish search history event: {}", e.getMessage());
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generic method to publish events to Kafka
     */
    private void publishEvent(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("❌ Failed to publish event to topic {}: {}", topic, ex.getMessage());
                    } else {
                        log.trace("✅ Successfully published event to topic {}", topic);
                    }
                });
        } catch (Exception e) {
            log.error("❌ Exception while publishing event to topic {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Publish bulk re-index event for initial data loading
     */
    public void publishBulkReindexEvent(String entityType) {
        try {
            SearchIndexEvent event = new SearchIndexEvent("BULK_REINDEX", entityType, "ALL", null);
            String topic = switch (entityType) {
                case "TASK" -> TASK_SEARCH_TOPIC;
                case "PROJECT" -> PROJECT_SEARCH_TOPIC;
                case "USER" -> USER_SEARCH_TOPIC;
                case "TEAM" -> TEAM_SEARCH_TOPIC;
                default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
            };
            publishEvent(topic, "BULK_REINDEX", event);
            log.info("📤 Published BULK_REINDEX event for: {}", entityType);
        } catch (Exception e) {
            log.error("❌ Failed to publish BULK_REINDEX event: {}", e.getMessage());
        }
    }
}
