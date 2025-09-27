package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ProfileDto.TaskPriorityDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.TaskStatusDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.UpdateTaskPriorityRequestDto;
import com.example.taskmanagement_backend.dtos.ProfileDto.UpdateTaskStatusRequestDto;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserTaskPriority;
import com.example.taskmanagement_backend.entities.UserTaskStatus;
import com.example.taskmanagement_backend.enums.TaskPriority;
import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.repositories.UserTaskPriorityRepository;
import com.example.taskmanagement_backend.repositories.UserTaskStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserTaskConfigService {

    private final UserTaskStatusRepository userTaskStatusRepository;
    private final UserTaskPriorityRepository userTaskPriorityRepository;
    private final UserJpaRepository userRepository;

    // Default colors for statuses
    private static final Map<String, String> DEFAULT_STATUS_COLORS = Map.of(
        "TODO", "#6B7280",
        "IN_PROGRESS", "#3B82F6", 
        "DONE", "#10B981",
        "TESTING", "#F59E0B",
        "BLOCKED", "#EF4444",
        "REVIEW", "#8B5CF6"
    );

    // Default colors for priorities
    private static final Map<String, String> DEFAULT_PRIORITY_COLORS = Map.of(
        "LOW", "#10B981",
        "MEDIUM", "#F59E0B", 
        "HIGH", "#EF4444"
    );

    /**
     * Get user's task statuses. If user hasn't customized, return defaults.
     */
    public List<TaskStatusDto> getUserTaskStatuses(Long userId) {
        List<UserTaskStatus> userStatuses = userTaskStatusRepository.findByUserIdAndIsActiveTrueOrderBySortOrder(userId);
        
        if (userStatuses.isEmpty()) {
            // Return default statuses
            return getDefaultTaskStatuses();
        }
        
        return userStatuses.stream()
            .map(this::convertToTaskStatusDto)
            .collect(Collectors.toList());
    }

    /**
     * Get user's task priorities. If user hasn't customized, return defaults.
     */
    public List<TaskPriorityDto> getUserTaskPriorities(Long userId) {
        List<UserTaskPriority> userPriorities = userTaskPriorityRepository.findByUserIdAndIsActiveTrueOrderBySortOrder(userId);
        
        if (userPriorities.isEmpty()) {
            // Return default priorities
            return getDefaultTaskPriorities();
        }
        
        return userPriorities.stream()
            .map(this::convertToTaskPriorityDto)
            .collect(Collectors.toList());
    }

    /**
     * Update user's task statuses
     */
    @Transactional
    public List<TaskStatusDto> updateUserTaskStatuses(Long userId, UpdateTaskStatusRequestDto request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Delete existing statuses for this user
        userTaskStatusRepository.deleteByUserId(userId);

        // Create new statuses
        List<UserTaskStatus> newStatuses = request.getStatuses().stream()
            .map(dto -> UserTaskStatus.builder()
                .statusKey(dto.getStatusKey())
                .displayName(dto.getDisplayName())
                .color(dto.getColor())
                .sortOrder(dto.getSortOrder())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .isDefault(false)
                .user(user)
                .build())
            .collect(Collectors.toList());

        List<UserTaskStatus> savedStatuses = userTaskStatusRepository.saveAll(newStatuses);
        
        return savedStatuses.stream()
            .map(this::convertToTaskStatusDto)
            .collect(Collectors.toList());
    }

    /**
     * Update user's task priorities
     */
    @Transactional
    public List<TaskPriorityDto> updateUserTaskPriorities(Long userId, UpdateTaskPriorityRequestDto request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Delete existing priorities for this user
        userTaskPriorityRepository.deleteByUserId(userId);

        // Create new priorities
        List<UserTaskPriority> newPriorities = request.getPriorities().stream()
            .map(dto -> UserTaskPriority.builder()
                .priorityKey(dto.getPriorityKey())
                .displayName(dto.getDisplayName())
                .color(dto.getColor())
                .sortOrder(dto.getSortOrder())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .isDefault(false)
                .user(user)
                .build())
            .collect(Collectors.toList());

        List<UserTaskPriority> savedPriorities = userTaskPriorityRepository.saveAll(newPriorities);
        
        return savedPriorities.stream()
            .map(this::convertToTaskPriorityDto)
            .collect(Collectors.toList());
    }

    /**
     * Reset user's task statuses to defaults
     */
    @Transactional
    public List<TaskStatusDto> resetUserTaskStatuses(Long userId) {
        userTaskStatusRepository.deleteByUserId(userId);
        return getDefaultTaskStatuses();
    }

    /**
     * Reset user's task priorities to defaults
     */
    @Transactional
    public List<TaskPriorityDto> resetUserTaskPriorities(Long userId) {
        userTaskPriorityRepository.deleteByUserId(userId);
        return getDefaultTaskPriorities();
    }

    private List<TaskStatusDto> getDefaultTaskStatuses() {
        return List.of(
            TaskStatusDto.builder()
                .statusKey("TODO")
                .displayName("To Do")
                .color(DEFAULT_STATUS_COLORS.get("TODO"))
                .sortOrder(1)
                .isDefault(true)
                .isActive(true)
                .build(),
            TaskStatusDto.builder()
                .statusKey("IN_PROGRESS")
                .displayName("In Progress")
                .color(DEFAULT_STATUS_COLORS.get("IN_PROGRESS"))
                .sortOrder(2)
                .isDefault(true)
                .isActive(true)
                .build(),
            TaskStatusDto.builder()
                .statusKey("REVIEW")
                .displayName("Review")
                .color(DEFAULT_STATUS_COLORS.get("REVIEW"))
                .sortOrder(3)
                .isDefault(true)
                .isActive(true)
                .build(),
            TaskStatusDto.builder()
                .statusKey("TESTING")
                .displayName("Testing")
                .color(DEFAULT_STATUS_COLORS.get("TESTING"))
                .sortOrder(4)
                .isDefault(true)
                .isActive(true)
                .build(),
            TaskStatusDto.builder()
                .statusKey("DONE")
                .displayName("Done")
                .color(DEFAULT_STATUS_COLORS.get("DONE"))
                .sortOrder(5)
                .isDefault(true)
                .isActive(true)
                .build(),
            TaskStatusDto.builder()
                .statusKey("BLOCKED")
                .displayName("Blocked")
                .color(DEFAULT_STATUS_COLORS.get("BLOCKED"))
                .sortOrder(6)
                .isDefault(true)
                .isActive(true)
                .build()
        );
    }

    private List<TaskPriorityDto> getDefaultTaskPriorities() {
        return List.of(
            TaskPriorityDto.builder()
                .priorityKey("LOW")
                .displayName("Low")
                .color(DEFAULT_PRIORITY_COLORS.get("LOW"))
                .sortOrder(1)
                .isDefault(true)
                .isActive(true)
                .build(),
            TaskPriorityDto.builder()
                .priorityKey("MEDIUM")
                .displayName("Medium")
                .color(DEFAULT_PRIORITY_COLORS.get("MEDIUM"))
                .sortOrder(2)
                .isDefault(true)
                .isActive(true)
                .build(),
            TaskPriorityDto.builder()
                .priorityKey("HIGH")
                .displayName("High")
                .color(DEFAULT_PRIORITY_COLORS.get("HIGH"))
                .sortOrder(3)
                .isDefault(true)
                .isActive(true)
                .build()
        );
    }

    private TaskStatusDto convertToTaskStatusDto(UserTaskStatus status) {
        return TaskStatusDto.builder()
            .id(status.getId())
            .statusKey(status.getStatusKey())
            .displayName(status.getDisplayName())
            .color(status.getColor())
            .sortOrder(status.getSortOrder())
            .isDefault(status.getIsDefault())
            .isActive(status.getIsActive())
            .build();
    }

    private TaskPriorityDto convertToTaskPriorityDto(UserTaskPriority priority) {
        return TaskPriorityDto.builder()
            .id(priority.getId())
            .priorityKey(priority.getPriorityKey())
            .displayName(priority.getDisplayName())
            .color(priority.getColor())
            .sortOrder(priority.getSortOrder())
            .isDefault(priority.getIsDefault())
            .isActive(priority.getIsActive())
            .build();
    }
}