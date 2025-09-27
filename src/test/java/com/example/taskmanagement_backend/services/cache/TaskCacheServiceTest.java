package com.example.taskmanagement_backend.services.cache;

import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
import com.example.taskmanagement_backend.exceptions.CacheException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Professional Unit Tests for TaskCacheService
 * 
 * Test Coverage:
 * - Cache operations (get, set, evict)
 * - Error handling and fallback
 * - Metrics recording
 * - Cache health checks
 * 
 * @author Task Management Team
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
class TaskCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private CacheMetricsService cacheMetricsService;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private TaskCacheService taskCacheService;

    private TaskResponseDto sampleTask;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        taskCacheService = new TaskCacheService(redisTemplate, cacheMetricsService);
        
        // Create sample task for testing
        sampleTask = TaskResponseDto.builder()
                .id(1L)
                .title("Test Task")
                .description("Test Description")
                .status("TODO")
                .priority("HIGH")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .creatorId(1L)
                .build();
    }

    @Test
    void cacheTask_ShouldCacheSuccessfully() {
        // Given
        Long taskId = 1L;
        String expectedKey = "taskmanagement:task:" + taskId;

        // When
        taskCacheService.cacheTask(taskId, sampleTask);

        // Then
        verify(valueOperations).set(expectedKey, sampleTask, 900L, TimeUnit.SECONDS);
        verify(cacheMetricsService).recordCacheWrite("task");
    }

    @Test
    void cacheTask_ShouldThrowCacheException_WhenRedisThrowsException() {
        // Given
        Long taskId = 1L;
        doThrow(new RuntimeException("Redis error")).when(valueOperations)
                .set(anyString(), any(), anyLong(), any(TimeUnit.class));

        // When & Then
        assertThrows(CacheException.class, () -> taskCacheService.cacheTask(taskId, sampleTask));
    }

    @Test
    void getTask_ShouldReturnCachedTask_WhenCacheHit() {
        // Given
        Long taskId = 1L;
        String expectedKey = "taskmanagement:task:" + taskId;
        when(valueOperations.get(expectedKey)).thenReturn(sampleTask);

        // When
        TaskResponseDto result = taskCacheService.getTask(taskId);

        // Then
        assertNotNull(result);
        assertEquals(sampleTask.getId(), result.getId());
        assertEquals(sampleTask.getTitle(), result.getTitle());
        verify(cacheMetricsService).recordCacheHit("task");
    }

    @Test
    void getTask_ShouldReturnNull_WhenCacheMiss() {
        // Given
        Long taskId = 1L;
        String expectedKey = "taskmanagement:task:" + taskId;
        when(valueOperations.get(expectedKey)).thenReturn(null);

        // When
        TaskResponseDto result = taskCacheService.getTask(taskId);

        // Then
        assertNull(result);
        verify(cacheMetricsService).recordCacheMiss("task");
    }

    @Test
    void getTask_ShouldReturnNull_WhenRedisThrowsException() {
        // Given
        Long taskId = 1L;
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));

        // When
        TaskResponseDto result = taskCacheService.getTask(taskId);

        // Then
        assertNull(result);
        verify(cacheMetricsService).recordCacheMiss("task");
    }

    @Test
    void cacheUserTasks_ShouldCacheSuccessfully() {
        // Given
        Long userId = 1L;
        List<TaskResponseDto> tasks = Arrays.asList(sampleTask);
        String expectedKey = "taskmanagement:user_tasks:" + userId;

        // When
        taskCacheService.cacheUserTasks(userId, tasks);

        // Then
        verify(valueOperations).set(expectedKey, tasks, 600L, TimeUnit.SECONDS);
        verify(cacheMetricsService).recordCacheWrite("user_tasks");
    }

    @Test
    void getUserTasks_ShouldReturnCachedTasks_WhenCacheHit() {
        // Given
        Long userId = 1L;
        List<TaskResponseDto> expectedTasks = Arrays.asList(sampleTask);
        String expectedKey = "taskmanagement:user_tasks:" + userId;
        when(valueOperations.get(expectedKey)).thenReturn(expectedTasks);

        // When
        List<TaskResponseDto> result = taskCacheService.getUserTasks(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(sampleTask.getId(), result.get(0).getId());
        verify(cacheMetricsService).recordCacheHit("user_tasks");
    }

    @Test
    void evictTask_ShouldEvictSuccessfully() {
        // Given
        Long taskId = 1L;
        String expectedKey = "taskmanagement:task:" + taskId;
        when(redisTemplate.delete(expectedKey)).thenReturn(true);

        // When
        taskCacheService.evictTask(taskId);

        // Then
        verify(redisTemplate).delete(expectedKey);
        verify(cacheMetricsService).recordCacheEviction("task");
    }

    @Test
    void evictRelatedCaches_ShouldEvictAllRelatedCaches() {
        // Given
        Long taskId = 1L;
        Long userId = 2L;
        Long teamId = 3L;
        Long projectId = 4L;
        
        when(redisTemplate.delete(anyString())).thenReturn(true);

        // When
        taskCacheService.evictRelatedCaches(taskId, userId, teamId, projectId);

        // Then
        verify(redisTemplate, times(4)).delete(anyString());
        verify(cacheMetricsService, times(4)).recordCacheEviction(anyString());
    }

    @Test
    void isCacheAvailable_ShouldReturnTrue_WhenRedisIsHealthy() {
        // Given
        when(valueOperations.get("health_check")).thenReturn("ok");

        // When
        boolean result = taskCacheService.isCacheAvailable();

        // Then
        assertTrue(result);
        verify(valueOperations).set(eq("health_check"), eq("ok"), any());
        verify(redisTemplate).delete("health_check");
    }

    @Test
    void isCacheAvailable_ShouldReturnFalse_WhenRedisThrowsException() {
        // Given
        doThrow(new RuntimeException("Redis error")).when(valueOperations)
                .set(anyString(), any(), any());

        // When
        boolean result = taskCacheService.isCacheAvailable();

        // Then
        assertFalse(result);
    }

    @Test
    void getCacheStats_ShouldReturnValidStats() {
        // Given
        when(redisTemplate.keys("taskmanagement:task:*")).thenReturn(Set.of("key1", "key2"));
        when(redisTemplate.keys("taskmanagement:user_tasks:*")).thenReturn(Set.of("key3"));
        when(redisTemplate.keys("taskmanagement:team_tasks:*")).thenReturn(Set.of());
        when(redisTemplate.keys("taskmanagement:project_tasks:*")).thenReturn(null);

        // When
        TaskCacheService.CacheStats stats = taskCacheService.getCacheStats();

        // Then
        assertNotNull(stats);
        assertEquals(2, stats.getTaskCacheSize());
        assertEquals(1, stats.getUserTasksCacheSize());
        assertEquals(0, stats.getTeamTasksCacheSize());
        assertEquals(0, stats.getProjectTasksCacheSize());
        assertEquals(3, stats.getTotalCacheSize());
    }
}