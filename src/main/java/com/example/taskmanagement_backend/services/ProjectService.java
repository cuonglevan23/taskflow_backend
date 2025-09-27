package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ProjectDto.CreateProjectRequestDto;
import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectResponseDto;
import com.example.taskmanagement_backend.dtos.ProjectDto.UpdateProjectRequestDto;

import com.example.taskmanagement_backend.entities.Organization;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.ProjectMember;
import com.example.taskmanagement_backend.entities.Team;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.ProjectRole;
import com.example.taskmanagement_backend.enums.ProjectStatus;
import com.example.taskmanagement_backend.repositories.OrganizationJpaRepository;
import com.example.taskmanagement_backend.repositories.ProjectJpaRepository;
import com.example.taskmanagement_backend.repositories.ProjectMemberJpaRepository;
import com.example.taskmanagement_backend.repositories.ProjectTaskJpaRepository;
import com.example.taskmanagement_backend.repositories.ProjectTaskActivityRepository;
import com.example.taskmanagement_backend.repositories.ProjectTaskCommentRepository;
import com.example.taskmanagement_backend.repositories.TaskAttachmentRepository;
import com.example.taskmanagement_backend.repositories.TeamJpaRepository;
import com.example.taskmanagement_backend.repositories.TeamMemberJpaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.GrantedAuthority;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProjectService {
    @Autowired
    private ProjectJpaRepository projectJpaRepository;

    @Autowired
    UserJpaRepository userRepo;
    @Autowired
    OrganizationJpaRepository orgRepo;
    
    @Autowired
    TeamJpaRepository teamRepo;

    @Autowired
    private ProjectMemberJpaRepository projectMemberJpaRepository;

    @Autowired
    private TeamMemberJpaRepository teamMemberJpaRepository;

    @Autowired
    private ProjectTaskJpaRepository projectTaskRepository;

    @Autowired
    private ProjectTaskActivityRepository projectTaskActivityRepository;

    @Autowired
    private ProjectTaskCommentRepository projectTaskCommentRepository;

    @Autowired
    private TaskAttachmentRepository taskAttachmentRepository;

    @Autowired
    private com.example.taskmanagement_backend.repositories.ProjectProgressRepository projectProgressRepository;

    @Autowired
    private com.example.taskmanagement_backend.repositories.TeamProjectProgressRepository teamProjectProgressRepository;

    @Autowired
    private com.example.taskmanagement_backend.services.S3Service s3Service;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private com.example.taskmanagement_backend.search.services.SearchEventPublisher searchEventPublisher; // ✅ NEW: Add SearchEventPublisher for Kafka indexing

    @Autowired
    private ProjectTimelineService projectTimelineService; // NEW: Inject ProjectTimelineService

    // ✅ NEW: Add AuditLogger for automatic audit logging
    @Autowired
    private AuditLogger auditLogger;

    public List<ProjectResponseDto> getAllProjects() {
        return projectJpaRepository.findAll().stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public ProjectResponseDto getProjectById(Long id) {
        Project project = projectJpaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        // ✅ FIX: Thêm kiểm tra authorization trước khi trả về project
        checkProjectPermission(project, "view");

        return convertToDto(project);
    }

    public ProjectResponseDto createProject(CreateProjectRequestDto dto) {
        // Get current user as creator
        User currentUser = getCurrentUser();
        
        Project project = Project.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .owner(dto.getOwnerId() != null ? getUser(dto.getOwnerId()) : currentUser)
                .organization(dto.getOrganizationId() != null ? getOrg(dto.getOrganizationId()) : null)
                .team(dto.getTeamId() != null ? getTeam(dto.getTeamId()) : null)
                .isPersonal(dto.isPersonal())
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(ProjectStatus.PLANNED)
                .build();
        Project savedProject = projectJpaRepository.save(project);

        // ✅ FIX: Tự động thêm người tạo project vào project_members với role OWNER
        ProjectMember creatorMember = ProjectMember.builder()
                .project(savedProject)
                .user(currentUser)
                .role(ProjectRole.OWNER)  // Set role OWNER cho người tạo
                .joinedAt(LocalDateTime.now())
                .build();
        projectMemberJpaRepository.save(creatorMember);

        System.out.println("✅ Created project '" + savedProject.getName() + "' and added creator " +
                          currentUser.getEmail() + " as OWNER to project_members table");

        // 📅 NEW: Add timeline event for project creation
        try {
            projectTimelineService.addProjectCreatedEvent(savedProject, currentUser);
            System.out.println("📅 Added timeline event for project creation: " + savedProject.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to add timeline event for project creation: " + e.getMessage());
        }

        // ✅ NEW: Publish Kafka event for search indexing
        try {
            searchEventPublisher.publishProjectCreated(savedProject.getId(), currentUser.getId());
            System.out.println("📤 Published PROJECT_CREATED event to Kafka for project: " + savedProject.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to publish PROJECT_CREATED event for project " + savedProject.getId() + ": " + e.getMessage());
            // Don't throw exception to avoid blocking project creation
        }

        // ✅ NEW: Log audit trail for project creation
        try {
            auditLogger.logProjectCreated(currentUser.getId(), savedProject.getId(), savedProject.getName());
            System.out.println("📝 Logged audit trail for project creation: " + savedProject.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to log audit trail for project creation: " + e.getMessage());
        }

        return convertToDto(savedProject);
    }

    public  ProjectResponseDto updateProject(Long id,UpdateProjectRequestDto dto) {
        Project project = convertToEntity(getProjectById(id));
        Project originalProject = Project.builder()
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus())
                .build();

        // Check permission before updating
        checkProjectPermission(project, "update");

        User currentUser = getCurrentUser();

        // Track changes and add timeline events
        if (dto.getName() != null && !dto.getName().equals(project.getName())) {
            String oldName = project.getName();
            project.setName(dto.getName());
            try {
                projectTimelineService.addProjectNameChangedEvent(project, oldName, dto.getName(), currentUser);
            } catch (Exception e) {
                System.err.println("⚠️ Failed to add name change timeline event: " + e.getMessage());
            }
        }

        if (dto.getDescription() != null && !dto.getDescription().equals(project.getDescription())) {
            String oldDesc = project.getDescription();
            project.setDescription(dto.getDescription());
            try {
                projectTimelineService.addProjectDescriptionChangedEvent(project, oldDesc, dto.getDescription(), currentUser);
            } catch (Exception e) {
                System.err.println("⚠️ Failed to add description change timeline event: " + e.getMessage());
            }
        }

        if (dto.getStatus() != null && !dto.getStatus().equals(project.getStatus())) {
            String oldStatus = project.getStatus() != null ? project.getStatus().toString() : "null";
            project.setStatus(dto.getStatus());
            try {
                projectTimelineService.addProjectStatusChangedEvent(project, oldStatus, dto.getStatus().toString(), currentUser);
            } catch (Exception e) {
                System.err.println("⚠️ Failed to add status change timeline event: " + e.getMessage());
            }
        }

        if (dto.getStartDate() != null) project.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) project.setEndDate(dto.getEndDate());
        if (dto.getOwnerId() != null) project.setOwner(getUser(dto.getOwnerId()));
        if (dto.getOrganizationId() != null) project.setOrganization(getOrg(dto.getOrganizationId()));
        if (dto.getTeamId() != null) project.setTeam(getTeam(dto.getTeamId()));
        project.setIsPersonal(dto.isPersonal());
        project.setUpdatedAt(LocalDateTime.now());

        ProjectResponseDto result = convertToDto(projectJpaRepository.save(project));

        // Add general update timeline event if there were any changes
        try {
            projectTimelineService.addProjectUpdatedEvent(project, currentUser);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to add general update timeline event: " + e.getMessage());
        }

        // ✅ NEW: Publish Kafka event for search indexing after project update
        try {
            searchEventPublisher.publishProjectUpdated(project.getId(), currentUser.getId());
            System.out.println("📤 Published PROJECT_UPDATED event to Kafka for project: " + project.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to publish PROJECT_UPDATED event for project " + project.getId() + ": " + e.getMessage());
            // Don't throw exception to avoid blocking project update
        }

        // ✅ NEW: Log audit trail for project update
        try {
            String changes = String.format("Project updated - Name: %s, Status: %s",
                project.getName(), project.getStatus());
            auditLogger.logProjectUpdated(currentUser.getId(), project.getId(), project.getName(), changes);
            System.out.println("📝 Logged audit trail for project update: " + project.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to log audit trail for project update: " + e.getMessage());
        }

        return result;
    }

    @Transactional
    public void deleteProjectById(Long id) {
        Project project = convertToEntity(getProjectById(id));

        // Check permission before deleting
        checkProjectPermission(project, "delete");

        System.out.println("🗑️ Starting comprehensive project deletion process for project ID: " + id);

        // ✅ STEP 1: Delete all project task files from S3 first
        try {
            List<com.example.taskmanagement_backend.entities.ProjectTask> projectTasks =
                projectTaskRepository.findByProjectId(id);

            if (!projectTasks.isEmpty()) {
                System.out.println("🗑️ Found " + projectTasks.size() + " project tasks, cleaning up files and data...");

                for (com.example.taskmanagement_backend.entities.ProjectTask task : projectTasks) {
                    // Delete task attachments and files from S3
                    deleteProjectTaskFiles(task.getId());

                    // Delete task activities, comments, and other related data
                    deleteProjectTaskRelatedData(task.getId());

                    // Clear Redis cache for this task
                    clearProjectTaskCache(task.getId());

                    // Publish Kafka event for task deletion
                    publishTaskDeletionEvent(task.getId(), project.getId());
                }

                // Finally delete all project tasks
                projectTaskRepository.deleteAll(projectTasks);
                System.out.println("✅ Successfully deleted all project tasks and their related data");
            } else {
                System.out.println("ℹ️ No project tasks found for this project");
            }
        } catch (Exception e) {
            System.err.println("❌ Error deleting project tasks and related data: " + e.getMessage());
            throw new RuntimeException("Failed to delete project tasks and related data: " + e.getMessage(), e);
        }

        // ✅ STEP 2: Delete all project members
        try {
            List<ProjectMember> projectMembers = projectMemberJpaRepository.findByProjectId(id);

            if (!projectMembers.isEmpty()) {
                System.out.println("🗑️ Found " + projectMembers.size() + " project members to delete");
                projectMemberJpaRepository.deleteAll(projectMembers);
                System.out.println("✅ Successfully deleted all project members");
            } else {
                System.out.println("ℹ️ No project members found for this project");
            }
        } catch (Exception e) {
            System.err.println("❌ Error deleting project members: " + e.getMessage());
            throw new RuntimeException("Failed to delete project members: " + e.getMessage(), e);
        }

        // ✅ STEP 3: Delete project progress records
        try {
            projectProgressRepository.deleteByProjectId(id);
            System.out.println("✅ Successfully deleted project progress records");
        } catch (Exception e) {
            System.err.println("❌ Error deleting project progress: " + e.getMessage());
            throw new RuntimeException("Failed to delete project progress: " + e.getMessage(), e);
        }

        // ✅ STEP 4: Delete team project progress records
        try {
            teamProjectProgressRepository.deleteByProjectId(id);
            System.out.println("✅ Successfully deleted team project progress records");
        } catch (Exception e) {
            System.err.println("❌ Error deleting team project progress: " + e.getMessage());
            throw new RuntimeException("Failed to delete team project progress: " + e.getMessage(), e);
        }

        // ✅ STEP 5: Clear Redis cache for project
        try {
            clearProjectCache(id);
            System.out.println("✅ Successfully cleared Redis cache for project");
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Failed to clear Redis cache for project: " + e.getMessage());
            // Don't throw exception, continue deletion
        }

        // ✅ STEP 6: Publish Kafka event for search indexing before project deletion
        try {
            User currentUser = getCurrentUser();
            searchEventPublisher.publishProjectDeleted(project.getId(), currentUser.getId());
            System.out.println("📤 Published PROJECT_DELETED event to Kafka for project: " + project.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to publish PROJECT_DELETED event for project " + project.getId() + ": " + e.getMessage());
            // Don't throw exception since the project is about to be deleted anyway
        }

        // ✅ STEP 7: Finally delete the project itself
        try {
            projectJpaRepository.deleteById(id);
            System.out.println("✅ Successfully deleted project with ID: " + id);
            System.out.println("🎉 Project deletion completed successfully with full cleanup!");
        } catch (Exception e) {
            System.err.println("❌ Error deleting project: " + e.getMessage());
            throw new RuntimeException("Failed to delete project: " + e.getMessage(), e);
        }
    }

    /**
     * Delete all files related to a project task from S3
     */
    private void deleteProjectTaskFiles(Long taskId) {
        try {
            // Get all task attachments for this task - use existing method
            List<com.example.taskmanagement_backend.entities.TaskAttachment> attachments =
                taskAttachmentRepository.findByTaskIdAndNotDeleted(taskId);

            if (!attachments.isEmpty()) {
                System.out.println("🗑️ Deleting " + attachments.size() + " file attachments for task: " + taskId);

                for (com.example.taskmanagement_backend.entities.TaskAttachment attachment : attachments) {
                    // Delete file from S3 - use correct field name
                    if (attachment.getFileKey() != null) {
                        try {
                            s3Service.deleteFile(attachment.getFileKey());
                            System.out.println("✅ Deleted S3 file: " + attachment.getFileKey());
                        } catch (Exception e) {
                            System.err.println("⚠️ Failed to delete S3 file " + attachment.getFileKey() + ": " + e.getMessage());
                        }
                    }
                }

                // Delete attachment records from database using bulk delete
                taskAttachmentRepository.deleteByTaskId(taskId);
                System.out.println("✅ Deleted all attachment records for task: " + taskId);
            }
        } catch (Exception e) {
            System.err.println("❌ Error deleting files for task " + taskId + ": " + e.getMessage());
            // Don't throw exception, continue with other cleanup
        }
    }

    /**
     * Delete all related data for a project task (activities, comments, etc.)
     */
    private void deleteProjectTaskRelatedData(Long taskId) {
        try {
            // Delete project task activities - use correct method
            if (projectTaskActivityRepository != null) {
                try {
                    projectTaskActivityRepository.deleteByProjectTaskId(taskId);
                    System.out.println("✅ Deleted activities for task: " + taskId);
                } catch (Exception e) {
                    System.out.println("ℹ️ No activities found or error deleting activities for task: " + taskId);
                }
            }

            // Delete project task comments - use correct method
            if (projectTaskCommentRepository != null) {
                try {
                    projectTaskCommentRepository.deleteByProjectTaskId(taskId);
                    System.out.println("✅ Deleted comments for task: " + taskId);
                } catch (Exception e) {
                    System.out.println("ℹ️ No comments found or error deleting comments for task: " + taskId);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error deleting related data for task " + taskId + ": " + e.getMessage());
            // Don't throw exception, continue with other cleanup
        }
    }

    /**
     * Clear Redis cache for a project task
     */
    private void clearProjectTaskCache(Long taskId) {
        try {
            if (redisTemplate != null) {
                // Clear various cache patterns for this task
                String[] cacheKeys = {
                    "project_task:" + taskId,
                    "project_task:*:" + taskId,
                    "task_attachments:" + taskId,
                    "task_activities:" + taskId,
                    "task_comments:" + taskId
                };

                for (String pattern : cacheKeys) {
                    Set<String> keys = redisTemplate.keys(pattern);
                    if (keys != null && !keys.isEmpty()) {
                        redisTemplate.delete(keys);
                        System.out.println("✅ Cleared Redis cache keys: " + keys.size() + " for pattern: " + pattern);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Failed to clear Redis cache for task " + taskId + ": " + e.getMessage());
        }
    }

    /**
     * Clear Redis cache for a project
     */
    private void clearProjectCache(Long projectId) {
        try {
            if (redisTemplate != null) {
                // Clear various cache patterns for this project
                String[] cacheKeys = {
                    "project:" + projectId,
                    "project:*:" + projectId,
                    "project_members:" + projectId,
                    "project_tasks:" + projectId,
                    "project_progress:" + projectId,
                    "project_stats:" + projectId
                };

                for (String pattern : cacheKeys) {
                    Set<String> keys = redisTemplate.keys(pattern);
                    if (keys != null && !keys.isEmpty()) {
                        redisTemplate.delete(keys);
                        System.out.println("✅ Cleared Redis cache keys: " + keys.size() + " for pattern: " + pattern);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Failed to clear Redis cache for project " + projectId + ": " + e.getMessage());
        }
    }

    /**
     * Publish Kafka event for task deletion
     */
    private void publishTaskDeletionEvent(Long taskId, Long projectId) {
        try {
            if (searchEventPublisher != null) {
                User currentUser = getCurrentUser();
                // Use the existing publishTaskDeleted method for task deletion
                searchEventPublisher.publishTaskDeleted(taskId, currentUser.getId());
                System.out.println("📤 Published TASK_DELETED event to Kafka for task: " + taskId);

                // Also publish project updated event since the project content changed
                searchEventPublisher.publishProjectUpdated(projectId, currentUser.getId());
                System.out.println("📤 Published PROJECT_UPDATED event to Kafka for project: " + projectId + " after deleting task: " + taskId);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Failed to publish task deletion event for task " + taskId + ": " + e.getMessage());
        }
    }

    /**
     * Get all projects that a user either created, owns or joined as a member
     * @param userId The ID of the user
     * @return List of projects the user is associated with
     * @throws RuntimeException if user not found
     */
    public List<ProjectResponseDto> getProjectsByUserId(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        List<Project> projects = projectJpaRepository.findProjectsByUserCreatedOwnedOrJoined(user);
        return projects.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get projects created by a specific user
     * @param userId The ID of the user who created the projects
     * @return List of projects created by the user
     * @throws RuntimeException if user not found
     */
    public List<ProjectResponseDto> getProjectsCreatedByUser(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        List<Project> projects = projectJpaRepository.findByCreatedBy(user);
        return projects.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get projects owned by a specific user
     * @param userId The ID of the user who owns the projects
     * @return List of projects owned by the user
     * @throws RuntimeException if user not found
     */
    public List<ProjectResponseDto> getProjectsOwnedByUser(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        List<Project> projects = projectJpaRepository.findByOwner(user);
        return projects.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get projects assigned to a specific team
     * @param teamId The ID of the team
     * @return List of projects assigned to the team
     * @throws RuntimeException if team not found
     */
    public List<ProjectResponseDto> getProjectsByTeamId(Long teamId) {
        Team team = teamRepo.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

        List<Project> projects = projectJpaRepository.findByTeam(team);
        return projects.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private ProjectResponseDto convertToDto(Project project) {
        // ✅ FIX: Get current user's role in this project
        String currentUserRole = null;
        boolean isCurrentUserMember = false;

        try {
            User currentUser = getCurrentUser();

            // Query để lấy role của user hiện tại sử dụng repository method đơn giản hơn
            List<ProjectMember> projectMembers = projectMemberJpaRepository.findByProjectId(project.getId());

            for (ProjectMember member : projectMembers) {
                if (member.getUser().getId().equals(currentUser.getId())) {
                    currentUserRole = member.getRole().name();
                    isCurrentUserMember = true;
                    break;
                }
            }
        } catch (Exception e) {
            // User not authenticated or not found - skip role info
            System.out.println("Could not get current user role for project: " + e.getMessage());
        }

        return ProjectResponseDto.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus() != null ? project.getStatus() : null)
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .ownerId(project.getOwner() != null ? project.getOwner().getId() : null)
                .organizationId(project.getOrganization() != null ? project.getOrganization().getId() : null)
                .teamId(project.getTeam() != null ? project.getTeam().getId() : null)
                .createdById(project.getCreatedBy() != null ? project.getCreatedBy().getId() : null)
                .isPersonal(project.getIsPersonal())
                .currentUserRole(currentUserRole)
                .isCurrentUserMember(isCurrentUserMember)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private Project convertToEntity(ProjectResponseDto dto) {
        return Project.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .status(dto.getStatus())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .owner(getUser(dto.getOwnerId()))
                .organization(getOrg(dto.getOrganizationId()))
                .team(getTeam(dto.getTeamId()))
                .createdBy(getUser(dto.getCreatedById()))
                .isPersonal(dto.isPersonal())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    private User getUser(Long id) {
        return id != null ? userRepo.findById(id).orElse(null) : null;
    }

    private Organization getOrg(Long id) {
        return id != null ? orgRepo.findById(id).orElse(null) : null;
    }

    private Team getTeam(Long id) {
        return id != null ? teamRepo.findById(id).orElse(null) : null;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepo.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    private String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(role -> role.startsWith("ROLE_"))
                .map(role -> role.substring(5)) // Remove "ROLE_" prefix
                .findFirst()
                .orElse("MEMBER");
    }

    private void checkProjectPermission(Project project, String operation) {
        User currentUser = getCurrentUser();
        String userRole = getCurrentUserRole();

        // ADMIN có full quyền với tất cả projects
        if ("ADMIN".equals(userRole)) {
            return;
        }

        // 1. Kiểm tra creator của project
        boolean isCreator = project.getCreatedBy() != null &&
                           project.getCreatedBy().getId().equals(currentUser.getId());

        // 2. Kiểm tra owner của project
        boolean isOwner = project.getOwner() != null &&
                         project.getOwner().getId().equals(currentUser.getId());

        // 3. Kiểm tra project member (user được add trực tiếp vào project)
        boolean isProjectMember = projectMemberJpaRepository.findByProjectId(project.getId())
                .stream()
                .anyMatch(member -> member.getUser().getId().equals(currentUser.getId()));

        // 4. Kiểm tra team member (nếu project thuộc về team)
        boolean isTeamMember = false;
        if (project.getTeam() != null) {
            isTeamMember = teamMemberJpaRepository.existsByTeamIdAndUserId(
                project.getTeam().getId(), currentUser.getId());
        }

        // 5. Cho phép truy cập nếu user có bất kỳ quyền nào
        if (isCreator || isOwner || isProjectMember || isTeamMember) {
            return;
        }

        // 6. Từ chối truy cập với thông báo rõ ràng
        throw new RuntimeException("You don't have permission to " + operation + " this project. " +
                                 "You must be the creator, owner, project member, or team member to access this project.");
    }

    /**
     * 🔒 AUTHORIZATION: Get project IDs where user is owner
     * Used for search authorization at database layer
     */
    public List<Long> getProjectIdsByOwnerId(Long userId) {
        try {
            List<Project> projects = projectJpaRepository.findByOwner_Id(userId);
            List<Long> projectIds = projects.stream()
                .map(Project::getId)
                .collect(Collectors.toList());

            System.out.println("Found " + projectIds.size() + " projects owned by user " + userId);
            return projectIds;
        } catch (Exception e) {
            System.err.println("Failed to get project IDs by owner " + userId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 🔒 AUTHORIZATION: Get project IDs where user is member
     * Used for search authorization at database layer
     */
    public List<Long> getProjectIdsByMemberId(Long userId) {
        try {
            List<ProjectMember> memberships = projectMemberJpaRepository.findByUser_Id(userId);
            List<Long> projectIds = memberships.stream()
                .map(member -> member.getProject().getId())
                .collect(Collectors.toList());

            System.out.println("Found " + projectIds.size() + " projects where user " + userId + " is member");
            return projectIds;
        } catch (Exception e) {
            System.err.println("Failed to get project IDs by member " + userId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 🔒 AUTHORIZATION: Get all project IDs user has access to (owner + member)
     * This is the main method for search authorization
     */
    public List<Long> getAllAccessibleProjectIds(Long userId) {
        try {
            Set<Long> allProjectIds = new HashSet<>();

            // Add projects where user is owner
            allProjectIds.addAll(getProjectIdsByOwnerId(userId));

            // Add projects where user is member
            allProjectIds.addAll(getProjectIdsByMemberId(userId));

            List<Long> result = new ArrayList<>(allProjectIds);
            System.out.println("User " + userId + " has access to total " + result.size() + " unique projects");
            return result;
        } catch (Exception e) {
            System.err.println("Failed to get all accessible project IDs for user " + userId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // Add paginated projects by user method to support the new controller endpoint
    public Object getProjectsByUserIdPaginated(Long userId, int page, int size) {
        // Verify user exists
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // Get projects where user is a direct member
        Set<Long> projectIds = projectMemberJpaRepository.findByUser_Id(userId).stream()
                .map(member -> member.getProject().getId())
                .collect(Collectors.toSet());

        // Get projects from teams the user belongs to
        Set<Long> teamIds = teamMemberJpaRepository.findByUser_Id(userId).stream()
                .map(member -> member.getTeam().getId())
                .collect(Collectors.toSet());

        // Add team projects to the set
        if (!teamIds.isEmpty()) {
            projectJpaRepository.findByTeamIdIn(new ArrayList<>(teamIds)).forEach(project ->
                projectIds.add(project.getId())
            );
        }

        // Create paginated result
        List<Project> projectsList = projectJpaRepository.findByIdIn(new ArrayList<>(projectIds));
        int start = page * size;
        int end = Math.min(start + size, projectsList.size());

        // Handle pagination manually
        if (start >= projectsList.size()) {
            return new PaginatedProjectsResponse(
                projectsList.stream().map(this::convertToDto).collect(Collectors.toList()),
                page,
                size,
                0,
                projectsList.size()
            );
        }

        List<ProjectResponseDto> paginatedProjects = projectsList.subList(start, end).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return new PaginatedProjectsResponse(
            paginatedProjects,
            page,
            size,
            paginatedProjects.size(),
            projectsList.size()
        );
    }

    // Inner class for paginated response
    public static class PaginatedProjectsResponse {
        private List<ProjectResponseDto> projects;
        private int page;
        private int size;
        private int count;
        private int totalCount;

        public PaginatedProjectsResponse(List<ProjectResponseDto> projects, int page, int size, int count, int totalCount) {
            this.projects = projects;
            this.page = page;
            this.size = size;
            this.count = count;
            this.totalCount = totalCount;
        }

        public List<ProjectResponseDto> getProjects() {
            return projects;
        }

        public int getPage() {
            return page;
        }

        public int getSize() {
            return size;
        }

        public int getCount() {
            return count;
        }

        public int getTotalCount() {
            return totalCount;
        }
    }
}
