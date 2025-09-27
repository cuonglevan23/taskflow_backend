package com.example.taskmanagement_backend.search.services;

import com.example.taskmanagement_backend.entities.*;
import com.example.taskmanagement_backend.search.documents.*;
import com.example.taskmanagement_backend.search.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core service for managing Elasticsearch search indexing
 * Handles CRUD operations for all search documents
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchIndexingService {

    private final TaskSearchRepository taskSearchRepository;
    private final ProjectSearchRepository projectSearchRepository;
    private final UserSearchRepository userSearchRepository;
    private final TeamSearchRepository teamSearchRepository;

    // ==================== TASK INDEXING ====================

    /**
     * Index or update a task in Elasticsearch
     */
    public void indexTask(Task task) {
        try {
            TaskSearchDocument document = mapTaskToSearchDocument(task);
            taskSearchRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to index task {}: {}", task.getId(), e.getMessage());
        }
    }

    /**
     * Remove task from search index
     */
    public void deleteTaskFromIndex(Long taskId) {
        try {
            taskSearchRepository.deleteById(taskId.toString());
        } catch (Exception e) {
            log.error("Failed to delete task {} from index: {}", taskId, e.getMessage());
        }
    }

    /**
     * Bulk index multiple tasks
     */
    public void bulkIndexTasks(List<Task> tasks) {
        try {
            List<TaskSearchDocument> documents = tasks.stream()
                    .map(this::mapTaskToSearchDocument)
                    .collect(Collectors.toList());
            taskSearchRepository.saveAll(documents);
        } catch (Exception e) {
            log.error("Failed to bulk index tasks: {}", e.getMessage());
        }
    }

    // ==================== PROJECT INDEXING ====================

    /**
     * Index or update a project in Elasticsearch
     */
    public void indexProject(Project project) {
        try {
            ProjectSearchDocument document = mapProjectToSearchDocument(project);
            projectSearchRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to index project {}: {}", project.getId(), e.getMessage());
        }
    }

    /**
     * Remove project from search index
     */
    public void deleteProjectFromIndex(Long projectId) {
        try {
            projectSearchRepository.deleteById(projectId.toString());
        } catch (Exception e) {
            log.error("Failed to delete project {} from index: {}", projectId, e.getMessage());
        }
    }

    /**
     * Bulk index multiple projects
     */
    public void bulkIndexProjects(List<Project> projects) {
        try {
            List<ProjectSearchDocument> documents = projects.stream()
                    .map(this::mapProjectToSearchDocument)
                    .collect(Collectors.toList());
            projectSearchRepository.saveAll(documents);
        } catch (Exception e) {
            log.error("Failed to bulk index projects: {}", e.getMessage());
        }
    }

    // ==================== USER INDEXING ====================

    /**
     * Index or update a user in Elasticsearch
     */
    public void indexUser(User user) {
        try {
            UserSearchDocument document = mapUserToSearchDocument(user);
            userSearchRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to index user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Remove user from search index
     */
    public void deleteUserFromIndex(Long userId) {
        try {
            userSearchRepository.deleteById(userId.toString());
        } catch (Exception e) {
            log.error("Failed to delete user {} from index: {}", userId, e.getMessage());
        }
    }

    /**
     * Bulk index multiple users
     */
    public void bulkIndexUsers(List<User> users) {
        try {
            List<UserSearchDocument> documents = users.stream()
                    .map(this::mapUserToSearchDocument)
                    .collect(Collectors.toList());
            userSearchRepository.saveAll(documents);
        } catch (Exception e) {
            log.error("Failed to bulk index users: {}", e.getMessage());
        }
    }

    // ==================== TEAM INDEXING ====================

    /**
     * Index or update a team in Elasticsearch
     */
    public void indexTeam(Team team) {
        try {
            TeamSearchDocument document = mapTeamToSearchDocument(team);
            teamSearchRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to index team {}: {}", team.getId(), e.getMessage());
        }
    }

    /**
     * Remove team from search index
     */
    public void deleteTeamFromIndex(Long teamId) {
        try {
            teamSearchRepository.deleteById(teamId.toString());
        } catch (Exception e) {
            log.error("Failed to delete team {} from index: {}", teamId, e.getMessage());
        }
    }

    /**
     * Bulk index multiple teams
     */
    public void bulkIndexTeams(List<Team> teams) {
        try {
            List<TeamSearchDocument> documents = teams.stream()
                    .map(this::mapTeamToSearchDocument)
                    .collect(Collectors.toList());
            teamSearchRepository.saveAll(documents);
        } catch (Exception e) {
            log.error("Failed to bulk index teams: {}", e.getMessage());
        }
    }

    // ==================== MAPPING METHODS ====================

    /**
     * Map Task entity to TaskSearchDocument
     * 🔧 FIX: Properly map authorization fields for search
     */
    private TaskSearchDocument mapTaskToSearchDocument(Task task) {
        // 🔒 CRITICAL: Get creator information
        Long creatorId = null;
        String creatorName = null;
        if (task.getCreator() != null) {
            creatorId = task.getCreator().getId();
            creatorName = task.getCreator().getEmail();
        }

        // 🔒 CRITICAL: Get assignee information from task assignees
        Long primaryAssigneeId = null;
        String primaryAssigneeName = null;
        List<Long> assigneeIds = new ArrayList<>();

        if (task.getAssignees() != null && !task.getAssignees().isEmpty()) {
            // Get primary assignee (first one from Set)
            TaskAssignee primaryAssignee = task.getAssignees().iterator().next();
            if (primaryAssignee.getUser() != null) {
                primaryAssigneeId = primaryAssignee.getUser().getId();
                primaryAssigneeName = primaryAssignee.getUser().getEmail();
            }

            // Get all assignee IDs for comprehensive search
            assigneeIds = task.getAssignees().stream()
                .map(assignee -> assignee.getUser() != null ? assignee.getUser().getId() : null)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        }

        // 🔒 CRITICAL: Get project information
        Long projectId = null;
        String projectName = null;
        if (task.getProject() != null) {
            projectId = task.getProject().getId();
            projectName = task.getProject().getName();
        }

        // 🔒 CRITICAL: Get team information
        Long teamId = null;
        if (task.getTeam() != null) {
            teamId = task.getTeam().getId();
        }

        return TaskSearchDocument.builder()
                .id(task.getId().toString())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus() != null ? task.getStatus().name() : null)
                .priority(task.getPriority() != null ? task.getPriority().name() : null)
                // 🔒 AUTHORIZATION FIELDS - CRITICAL FOR SEARCH
                .creatorId(creatorId)
                .creatorName(creatorName)
                .assigneeId(primaryAssigneeId)
                .assigneeName(primaryAssigneeName)
                .visibleToUserIds(assigneeIds)  // Use existing field for assignee IDs
                .projectId(projectId)
                .projectName(projectName)
                .tags(task.getTags())
                .dueDate(task.getDueDate())
                .createdAt(task.getCreatedAt())
                .isCompleted(task.getIsCompleted())
                .build();
    }

    /**
     * Map Project entity to ProjectSearchDocument
     */
    private ProjectSearchDocument mapProjectToSearchDocument(Project project) {
        return ProjectSearchDocument.builder()
                .id(project.getId().toString())
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus() != null ? project.getStatus().name() : null)
                .privacy(project.getPrivacy())  // Remove .name() since getPrivacy() already returns String
                .ownerId(project.getOwnerId())
                .ownerName(project.getOwnerName())
                .memberIds(project.getMemberIds())
                .memberNames(project.getMemberNames())
                .tags(project.getTags())
                .startDate(project.getStartDate() != null ? project.getStartDate().atStartOfDay() : null)  // Convert LocalDate to LocalDateTime
                .endDate(project.getEndDate() != null ? project.getEndDate().atStartOfDay() : null)  // Convert LocalDate to LocalDateTime
                .createdAt(project.getCreatedAt())
                .isActive(project.getIsActive())
                .totalTasks(project.getTotalTasks())
                .completedTasks(project.getCompletedTasks())
                .completionPercentage(project.getCompletionPercentage())
                .build();
    }

    /**
     * Map User entity to UserSearchDocument
     */
    private UserSearchDocument mapUserToSearchDocument(User user) {
        return UserSearchDocument.builder()
                .id(user.getId().toString())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFirstName() + " " + user.getLastName())
                .username(user.getUsername())
                .jobTitle(user.getJobTitle())
                .department(user.getDepartment())
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .skills(user.getSkills())
                .location(user.getLocation())
                .company(user.getCompany())
                .isActive(user.getIsActive())
                .isOnline(user.getIsOnline())
                .isPremium(user.getIsPremium())
                .premiumPlanType(user.getPremiumPlanType())
                .profileVisibility(user.getProfileVisibility())
                .searchable(user.getSearchable())
                .friendIds(user.getFriendIds())
                .teamIds(user.getTeamIds())
                .teamNames(user.getTeamNames())
                .connectionsCount(user.getConnectionsCount())
                .completedTasksCount(user.getCompletedTasksCount())
                .build();
    }

    /**
     * Map Team entity to TeamSearchDocument
     */
    private TeamSearchDocument mapTeamToSearchDocument(Team team) {
        return TeamSearchDocument.builder()
                .id(team.getId().toString())
                .name(team.getName())
                .description(team.getDescription())
                .createdAt(team.getCreatedAt())
                .leaderId(team.getLeaderId())
                .leaderName(team.getLeaderName())
                .memberIds(team.getMemberIds())
                .memberNames(team.getMemberNames())
                .memberCount(team.getMemberCount())
                .activeProjectsCount(team.getActiveProjectsCount())
                .totalTasksCount(team.getTotalTasksCount())
                .completedTasksCount(team.getCompletedTasksCount())
                .teamPerformanceScore(team.getTeamPerformanceScore())
                .isActive(team.getIsActive())
                .teamType(team.getTeamType())
                .privacy(team.getPrivacy())
                .searchable(team.getSearchable())
                .tags(team.getTags())
                .department(team.getDepartment())
                .organization(team.getOrganization() != null ? team.getOrganization().getName() : null)  // Convert Organization entity to String
                .build();
    }
}
