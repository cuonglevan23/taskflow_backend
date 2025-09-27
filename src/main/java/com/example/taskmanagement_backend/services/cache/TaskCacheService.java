package com.example.taskmanagement_backend.services.cache;

import com.example.taskmanagement_backend.dtos.ProjectTaskDto.ProjectTaskResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.exceptions.CacheException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Professional Task Cache Service
 * 
 * Responsibilities:
 * - Manage task-related cache operations
 * - Handle cache invalidation strategies
 * - Provide cache warming capabilities
 * - Monitor cache performance
 * 
 * @author Task Management Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheMetricsService cacheMetricsService;

    // Cache key patterns
    private static final String TASK_KEY_PREFIX = "taskmanagement:task:";
    private static final String USER_TASKS_KEY_PREFIX = "taskmanagement:user_tasks:";
    private static final String TEAM_TASKS_KEY_PREFIX = "taskmanagement:team_tasks:";
    private static final String PROJECT_TASKS_KEY_PREFIX = "taskmanagement:project_tasks:";
    private static final String TEAM_PROJECTS_TASKS_KEY_PREFIX = "taskmanagement:team_projects_tasks:";
    private static final long TEAM_PROJECTS_TASKS_TTL = 3600; // 1 hour
    private static final String TASK_STATS_KEY_PREFIX = "taskmanagement:task_stats:";

    // TTL configurations (in seconds)
    private static final long TASK_TTL = 900; // 15 minutes
    private static final long USER_TASKS_TTL = 600; // 10 minutes
    private static final long TEAM_TASKS_TTL = 480; // 8 minutes
    private static final long PROJECT_TASKS_TTL = 480; // 8 minutes
    private static final long TASK_STATS_TTL = 300; // 5 minutes

    /**
     * Cache single task
     */
    public void cacheTask(Long taskId, TaskResponseDto task) {
        try {
            String key = TASK_KEY_PREFIX + taskId;
            redisTemplate.opsForValue().set(key, task, TASK_TTL, TimeUnit.SECONDS);
            cacheMetricsService.recordCacheWrite("task");
            log.debug("✅ Cached task: {}", taskId);
        } catch (Exception e) {
            log.error("❌ Failed to cache task: {}", taskId, e);
            throw new CacheException("Failed to cache task", e);
        }
    }

    /**
     * Get single task from cache
     */
    public TaskResponseDto getTask(Long taskId) {
        try {
            String key = TASK_KEY_PREFIX + taskId;
            TaskResponseDto task = (TaskResponseDto) redisTemplate.opsForValue().get(key);
            
            if (task != null) {
                cacheMetricsService.recordCacheHit("task");
                log.debug("🚀 Cache HIT for task: {}", taskId);
            } else {
                cacheMetricsService.recordCacheMiss("task");
                log.debug("💨 Cache MISS for task: {}", taskId);
            }
            
            return task;
        } catch (Exception e) {
            log.error("❌ Failed to get task from cache: {}", taskId, e);
            cacheMetricsService.recordCacheMiss("task");
            return null;
        }
    }

    /**
     * Cache user tasks list
     */
    public void cacheUserTasks(Long userId, List<TaskResponseDto> tasks) {
        try {
            String key = USER_TASKS_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, tasks, USER_TASKS_TTL, TimeUnit.SECONDS);
            cacheMetricsService.recordCacheWrite("user_tasks");
            log.debug("✅ Cached user tasks: {} (count: {})", userId, tasks.size());
        } catch (Exception e) {
            log.error("❌ Failed to cache user tasks: {}", userId, e);
            throw new CacheException("Failed to cache user tasks", e);
        }
    }

    /**
     * Get user tasks from cache
     */
    @SuppressWarnings("unchecked")
    public List<TaskResponseDto> getUserTasks(Long userId) {
        try {
            String key = USER_TASKS_KEY_PREFIX + userId;
            List<TaskResponseDto> tasks = (List<TaskResponseDto>) redisTemplate.opsForValue().get(key);
            
            if (tasks != null) {
                cacheMetricsService.recordCacheHit("user_tasks");
                log.debug("🚀 Cache HIT for user tasks: {} (count: {})", userId, tasks.size());
            } else {
                cacheMetricsService.recordCacheMiss("user_tasks");
                log.debug("💨 Cache MISS for user tasks: {}", userId);
            }
            
            return tasks;
        } catch (Exception e) {
            log.error("❌ Failed to get user tasks from cache: {}", userId, e);
            cacheMetricsService.recordCacheMiss("user_tasks");
            return null;
        }
    }

    /**
     * Cache team tasks list
     */
    public void cacheTeamTasks(Long teamId, List<TaskResponseDto> tasks) {
        try {
            String key = TEAM_TASKS_KEY_PREFIX + teamId;
            redisTemplate.opsForValue().set(key, tasks, TEAM_TASKS_TTL, TimeUnit.SECONDS);
            cacheMetricsService.recordCacheWrite("team_tasks");
            log.debug("✅ Cached team tasks: {} (count: {})", teamId, tasks.size());
        } catch (Exception e) {
            log.error("❌ Failed to cache team tasks: {}", teamId, e);
            throw new CacheException("Failed to cache team tasks", e);
        }
    }

    /**
     * Get team tasks from cache
     */
    @SuppressWarnings("unchecked")
    public List<TaskResponseDto> getTeamTasks(Long teamId) {
        try {
            String key = TEAM_TASKS_KEY_PREFIX + teamId;
            List<TaskResponseDto> tasks = (List<TaskResponseDto>) redisTemplate.opsForValue().get(key);
            
            if (tasks != null) {
                cacheMetricsService.recordCacheHit("team_tasks");
                log.debug("🚀 Cache HIT for team tasks: {} (count: {})", teamId, tasks.size());
            } else {
                cacheMetricsService.recordCacheMiss("team_tasks");
                log.debug("💨 Cache MISS for team tasks: {}", teamId);
            }
            
            return tasks;
        } catch (Exception e) {
            log.error("❌ Failed to get team tasks from cache: {}", teamId, e);
            cacheMetricsService.recordCacheMiss("team_tasks");
            return null;
        }
    }

    /**
     * Cache project tasks list
     */
    public void cacheProjectTasks(Long projectId, List<TaskResponseDto> tasks) {
        try {
            String key = PROJECT_TASKS_KEY_PREFIX + projectId;
            redisTemplate.opsForValue().set(key, tasks, PROJECT_TASKS_TTL, TimeUnit.SECONDS);
            cacheMetricsService.recordCacheWrite("project_tasks");
            log.debug("✅ Cached project tasks: {} (count: {})", projectId, tasks.size());
        } catch (Exception e) {
            log.error("❌ Failed to cache project tasks: {}", projectId, e);
            throw new CacheException("Failed to cache project tasks", e);
        }
    }

    /**
     * Get project tasks from cache
     */
    @SuppressWarnings("unchecked")
    public List<TaskResponseDto> getProjectTasks(Long projectId) {
        try {
            String key = PROJECT_TASKS_KEY_PREFIX + projectId;
            List<TaskResponseDto> tasks = (List<TaskResponseDto>) redisTemplate.opsForValue().get(key);
            
            if (tasks != null) {
                cacheMetricsService.recordCacheHit("project_tasks");
                log.debug("🚀 Cache HIT for project tasks: {} (count: {})", projectId, tasks.size());
            } else {
                cacheMetricsService.recordCacheMiss("project_tasks");
                log.debug("💨 Cache MISS for project tasks: {}", projectId);
            }
            
            return tasks;
        } catch (Exception e) {
            log.error("❌ Failed to get project tasks from cache: {}", projectId, e);
            cacheMetricsService.recordCacheMiss("project_tasks");
            return null;
        }
    }

    /**
     * Invalidate single task cache
     */
    public void evictTask(Long taskId) {
        try {
            String key = TASK_KEY_PREFIX + taskId;
            Boolean deleted = redisTemplate.delete(key);
            cacheMetricsService.recordCacheEviction("task");
            log.debug("🗑️ Evicted task cache: {} (deleted: {})", taskId, deleted);
        } catch (Exception e) {
            log.error("❌ Failed to evict task cache: {}", taskId, e);
        }
    }

    /**
     * Invalidate user tasks cache
     */
    public void evictUserTasks(Long userId) {
        try {
            String key = USER_TASKS_KEY_PREFIX + userId;
            Boolean deleted = redisTemplate.delete(key);
            cacheMetricsService.recordCacheEviction("user_tasks");
            log.debug("🗑️ Evicted user tasks cache: {} (deleted: {})", userId, deleted);
        } catch (Exception e) {
            log.error("❌ Failed to evict user tasks cache: {}", userId, e);
        }
    }

    /**
     * Invalidate team tasks cache
     */
    public void evictTeamTasks(Long teamId) {
        try {
            String key = TEAM_TASKS_KEY_PREFIX + teamId;
            Boolean deleted = redisTemplate.delete(key);
            cacheMetricsService.recordCacheEviction("team_tasks");
            log.debug("🗑️ Evicted team tasks cache: {} (deleted: {})", teamId, deleted);
        } catch (Exception e) {
            log.error("❌ Failed to evict team tasks cache: {}", teamId, e);
        }
    }

    /**
     * Get team projects tasks from cache
     */
    @SuppressWarnings("unchecked")
    public List<ProjectTaskResponseDto> getTeamProjectsTasks(Long teamId) {
        try {
            String key = TEAM_PROJECTS_TASKS_KEY_PREFIX + teamId;
            List<ProjectTaskResponseDto> tasks = (List<ProjectTaskResponseDto>) redisTemplate.opsForValue().get(key);
            
            if (tasks != null) {
                cacheMetricsService.recordCacheHit("team_projects_tasks");
                log.debug("🚀 Cache HIT for team projects tasks: {} (count: {})", teamId, tasks.size());
            } else {
                cacheMetricsService.recordCacheMiss("team_projects_tasks");
                log.debug("💨 Cache MISS for team projects tasks: {}", teamId);
            }
            
            return tasks;
        } catch (Exception e) {
            log.error("❌ Failed to get team projects tasks from cache: {}", teamId, e);
            cacheMetricsService.recordCacheMiss("team_projects_tasks");
            return null;
        }
    }

    /**
     * Cache team projects tasks
     */
    public void cacheTeamProjectsTasks(Long teamId, List<ProjectTaskResponseDto> tasks) {
        try {
            String key = TEAM_PROJECTS_TASKS_KEY_PREFIX + teamId;
            redisTemplate.opsForValue().set(key, tasks, TEAM_PROJECTS_TASKS_TTL, TimeUnit.SECONDS);
            cacheMetricsService.recordCacheWrite("team_projects_tasks");
            log.debug("✅ Cached team projects tasks: {} (count: {})", teamId, tasks.size());
        } catch (Exception e) {
            log.error("❌ Failed to cache team projects tasks: {}", teamId, e);
            throw new CacheException("Failed to cache team projects tasks", e);
        }
    }

    /**
     * Invalidate team projects tasks cache
     */
    public void evictTeamProjectsTasks(Long teamId) {
        try {
            String key = TEAM_PROJECTS_TASKS_KEY_PREFIX + teamId;
            Boolean deleted = redisTemplate.delete(key);
            cacheMetricsService.recordCacheEviction("team_projects_tasks");
            log.debug("🗑️ Evicted team projects tasks cache: {} (deleted: {})", teamId, deleted);
        } catch (Exception e) {
            log.error("❌ Failed to evict team projects tasks cache: {}", teamId, e);
        }
    }

    /**
     * Invalidate project tasks cache
     */
    public void evictProjectTasks(Long projectId) {
        try {
            String key = PROJECT_TASKS_KEY_PREFIX + projectId;
            Boolean deleted = redisTemplate.delete(key);
            cacheMetricsService.recordCacheEviction("project_tasks");
            log.debug("🗑️ Evicted project tasks cache: {} (deleted: {})", projectId, deleted);
        } catch (Exception e) {
            log.error("❌ Failed to evict project tasks cache: {}", projectId, e);
        }
    }

    /**
     * Invalidate user tasks summary cache (paginated data)
     * This evicts ALL paginated cache entries for a user
     */
    public void evictUserTasksSummary(Long userId) {
        try {
            // Pattern to match all user_tasks_summary cache keys for this user
            String pattern = "user_tasks_summary:" + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keys);
                cacheMetricsService.recordCacheEviction("user_tasks_summary");
                log.info("🗑️ Evicted user tasks summary cache: {} (deleted {} keys)", userId, deletedCount);
            } else {
                log.debug("🗑️ No user tasks summary cache found for user: {}", userId);
            }
        } catch (Exception e) {
            log.error("❌ Failed to evict user tasks summary cache: {}", userId, e);
        }
    }

    /**
     * Invalidate all related caches when task is modified
     */
    public void evictRelatedCaches(Long taskId, Long userId, Long teamId, Long projectId) {
        log.info("🔄 Evicting related caches for task: {}", taskId);
        
        // Evict single task
        if (taskId != null) {
            evictTask(taskId);
        }
        
        // Evict user tasks
        if (userId != null) {
            evictUserTasks(userId);
            // ✅ FIX: Also evict user tasks summary (paginated cache)
            evictUserTasksSummary(userId);
        }
        
        // Evict team tasks
        if (teamId != null) {
            evictTeamTasks(teamId);
        }
        
        // Evict project tasks
        if (projectId != null) {
            evictProjectTasks(projectId);
        }
    }

    /**
     * Bulk eviction for cache refresh
     */
    public void evictAllTaskCaches() {
        try {
            log.info("🔄 Starting bulk cache eviction...");
            
            // Get all keys for each cache type
            Set<String> taskKeys = redisTemplate.keys(TASK_KEY_PREFIX + "*");
            Set<String> userTasksKeys = redisTemplate.keys(USER_TASKS_KEY_PREFIX + "*");
            Set<String> teamTasksKeys = redisTemplate.keys(TEAM_TASKS_KEY_PREFIX + "*");
            Set<String> projectTasksKeys = redisTemplate.keys(PROJECT_TASKS_KEY_PREFIX + "*");
            
            // Delete all keys
            long deletedCount = 0;
            if (taskKeys != null && !taskKeys.isEmpty()) {
                deletedCount += redisTemplate.delete(taskKeys);
            }
            if (userTasksKeys != null && !userTasksKeys.isEmpty()) {
                deletedCount += redisTemplate.delete(userTasksKeys);
            }
            if (teamTasksKeys != null && !teamTasksKeys.isEmpty()) {
                deletedCount += redisTemplate.delete(teamTasksKeys);
            }
            if (projectTasksKeys != null && !projectTasksKeys.isEmpty()) {
                deletedCount += redisTemplate.delete(projectTasksKeys);
            }
            
            log.info("✅ Bulk cache eviction completed. Deleted {} keys", deletedCount);
            cacheMetricsService.recordBulkEviction(deletedCount);
            
        } catch (Exception e) {
            log.error("❌ Failed to perform bulk cache eviction", e);
            throw new CacheException("Failed to perform bulk cache eviction", e);
        }
    }

    /**
     * Check if cache is available
     */
    public boolean isCacheAvailable() {
        try {
            redisTemplate.opsForValue().set("health_check", "ok", Duration.ofSeconds(10));
            String result = (String) redisTemplate.opsForValue().get("health_check");
            redisTemplate.delete("health_check");
            return "ok".equals(result);
        } catch (Exception e) {
            log.warn("⚠️ Cache health check failed", e);
            return false;
        }
    }

    /**
     * Get cache statistics
     */
    public CacheStats getCacheStats() {
        try {
            // Get key counts for each cache type
            Set<String> taskKeys = redisTemplate.keys(TASK_KEY_PREFIX + "*");
            Set<String> userTasksKeys = redisTemplate.keys(USER_TASKS_KEY_PREFIX + "*");
            Set<String> teamTasksKeys = redisTemplate.keys(TEAM_TASKS_KEY_PREFIX + "*");
            Set<String> projectTasksKeys = redisTemplate.keys(PROJECT_TASKS_KEY_PREFIX + "*");
            
            return CacheStats.builder()
                    .taskCacheSize(taskKeys != null ? taskKeys.size() : 0)
                    .userTasksCacheSize(userTasksKeys != null ? userTasksKeys.size() : 0)
                    .teamTasksCacheSize(teamTasksKeys != null ? teamTasksKeys.size() : 0)
                    .projectTasksCacheSize(projectTasksKeys != null ? projectTasksKeys.size() : 0)
                    .isAvailable(isCacheAvailable())
                    .build();
                    
        } catch (Exception e) {
            log.error("❌ Failed to get cache stats", e);
            return CacheStats.builder()
                    .isAvailable(false)
                    .build();
        }
    }

    /**
     * Cache statistics data class
     */
    public static class CacheStats {
        private final int taskCacheSize;
        private final int userTasksCacheSize;
        private final int teamTasksCacheSize;
        private final int projectTasksCacheSize;
        private final boolean isAvailable;

        private CacheStats(Builder builder) {
            this.taskCacheSize = builder.taskCacheSize;
            this.userTasksCacheSize = builder.userTasksCacheSize;
            this.teamTasksCacheSize = builder.teamTasksCacheSize;
            this.projectTasksCacheSize = builder.projectTasksCacheSize;
            this.isAvailable = builder.isAvailable;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public int getTaskCacheSize() { return taskCacheSize; }
        public int getUserTasksCacheSize() { return userTasksCacheSize; }
        public int getTeamTasksCacheSize() { return teamTasksCacheSize; }
        public int getProjectTasksCacheSize() { return projectTasksCacheSize; }
        public boolean isAvailable() { return isAvailable; }
        public int getTotalCacheSize() { 
            return taskCacheSize + userTasksCacheSize + teamTasksCacheSize + projectTasksCacheSize; 
        }

        public static class Builder {
            private int taskCacheSize;
            private int userTasksCacheSize;
            private int teamTasksCacheSize;
            private int projectTasksCacheSize;
            private boolean isAvailable;

            public Builder taskCacheSize(int taskCacheSize) {
                this.taskCacheSize = taskCacheSize;
                return this;
            }

            public Builder userTasksCacheSize(int userTasksCacheSize) {
                this.userTasksCacheSize = userTasksCacheSize;
                return this;
            }

            public Builder teamTasksCacheSize(int teamTasksCacheSize) {
                this.teamTasksCacheSize = teamTasksCacheSize;
                return this;
            }

            public Builder projectTasksCacheSize(int projectTasksCacheSize) {
                this.projectTasksCacheSize = projectTasksCacheSize;
                return this;
            }

            public Builder isAvailable(boolean isAvailable) {
                this.isAvailable = isAvailable;
                return this;
            }

            public CacheStats build() {
                return new CacheStats(this);
            }
        }
    }
}

