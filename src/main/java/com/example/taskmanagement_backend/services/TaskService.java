package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.TaskChecklistDto.TaskChecklistResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.CreateTaskRequestDto;
import com.example.taskmanagement_backend.dtos.TaskDto.CreateTaskWithEmailRequestDto;
import com.example.taskmanagement_backend.dtos.ProjectTaskDto.ProjectTaskResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.UpdateTaskRequestDto;
import com.example.taskmanagement_backend.entities.ProjectTask;
import com.example.taskmanagement_backend.dtos.TaskDto.MyTaskSummaryDto;
import com.example.taskmanagement_backend.entities.*;
import com.example.taskmanagement_backend.enums.TaskPriority;
import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.repositories.*;
import com.example.taskmanagement_backend.services.infrastructure.AutomatedEmailService; // ✅ NEW: Add AutomatedEmailService import
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.example.taskmanagement_backend.dtos.TaskDto.MyTaskSummaryDto;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskJpaRepository taskRepository;
    private final ProjectJpaRepository projectJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final TeamJpaRepository teamJpaRepository;
    private final TasksAssigneeJpaRepository tasksAssigneeJpaRepository;
    private final TeamMemberJpaRepository teamMemberJpaRepository;
    private final ProjectTaskJpaRepository projectTaskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final UserProfileMapper userProfileMapper;
    private final TaskChecklistJpaRepository taskChecklistJpaRepository;
    private final jakarta.persistence.EntityManager entityManager;
    private final TaskAttachmentService taskAttachmentService; // ✅ Add TaskAttachmentService dependency
    private final TaskActivityService taskActivityService; // ✅ NEW: Add TaskActivityService
    private final TaskActivityRepository taskActivityRepository; // ✅ NEW: Add TaskActivityRepository
    private final S3FileUploadService s3FileUploadService; // ✅ NEW: Add S3FileUploadService
    private final com.example.taskmanagement_backend.search.services.SearchEventPublisher searchEventPublisher; // ✅ NEW: Add SearchEventPublisher for Kafka indexing
    private final AutoNotificationService autoNotificationService; // ✅ NEW: Add AutoNotificationService
    private final AutomatedEmailService automatedEmailService; // ✅ NEW: Add AutomatedEmailService for email automation
    private final AuditLogger auditLogger; // ✅ NEW: Add AuditLogger for automatic audit logging


    public TaskResponseDto createTask(CreateTaskRequestDto dto) {
        Project project = null;
        if (dto.getProjectId() != null) {
            project = projectJpaRepository.findById(dto.getProjectId())
                    .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        }

        User creator = userJpaRepository.findById(dto.getCreatorId())
                .orElseThrow(() -> new EntityNotFoundException("Creator not found"));

        Team team = null;
        if (dto.getGroupId() != null) {
            team = teamJpaRepository.findById(dto.getGroupId())
                    .orElseThrow(() -> new EntityNotFoundException("Group (Team) not found"));
        }

        Task task = Task.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .statusKey(dto.getStatus())
                .priorityKey(dto.getPriority())
                .startDate(dto.getStartDate())
                .deadline(dto.getDeadline())
                .comment(dto.getComment())          // ✅ NEW: Add comment field
                .urlFile(dto.getUrlFile())          // ✅ NEW: Add url file field
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .creator(creator)
                .project(project)
                .team(team)
                .build();

        taskRepository.save(task);

        // ✅ NEW: Log task creation activity
        // ✅ NEW: Audit log - Task created
        auditLogger.logTaskCreated(creator.getId(), task.getId(), task.getTitle());

        taskActivityService.logTaskCreated(task);

        // ✅ NEW: Publish Kafka event for search indexing
        try {
            searchEventPublisher.publishTaskCreated(task.getId(), creator.getId());
            log.info("📤 Published TASK_CREATED event to Kafka for task: {}", task.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish TASK_CREATED event for task {}: {}", task.getId(), e.getMessage());
            // Don't throw exception to avoid blocking task creation
        }

        // ✅ NEW: Tạo Google Calendar event nếu được yêu cầu
        if (dto.getCreateCalendarEvent() != null && dto.getCreateCalendarEvent() &&
            dto.getGoogleAccessToken() != null && !dto.getGoogleAccessToken().trim().isEmpty()) {
            try {
                log.info("📅 Creating Google Calendar event for new task: {}", task.getId());
                // Inject GoogleCalendarService dependency
                // Note: Cần thêm GoogleCalendarService vào TaskService constructor
                // googleCalendarService.createCalendarEvent(task, dto.getGoogleAccessToken());
                log.info("📅 Google Calendar event creation will be implemented");
            } catch (Exception e) {
                log.warn("⚠️ Failed to create Google Calendar event for task {}: {}", task.getId(), e.getMessage());
                // Không throw exception để không block việc tạo task
            }
        }

        // Enhanced assignment logic: Support both User ID and Email assignment
        List<String> invalidEmails = new ArrayList<>();
        List<String> validEmails = new ArrayList<>();
        List<Long> validUserIds = new ArrayList<>();

        // Process User ID assignments (existing logic)
        if (dto.getAssignedToIds() != null && !dto.getAssignedToIds().isEmpty()) {
            for (Long userId : dto.getAssignedToIds()) {
                User assignee = userJpaRepository.findById(userId)
                        .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));
                // Check if already assigned
                boolean alreadyAssigned = tasksAssigneeJpaRepository.existsByTaskAndUserId(task, userId);
                if (alreadyAssigned) {
                    System.out.println("⚠️ User " + userId + " is already assigned to task " + task.getId());
                    continue; // Skip duplicate
                }
                TaskAssignee taskAssignee = TaskAssignee.builder()
                        .task(task)
                        .user(assignee)
                        .assignedAt(LocalDateTime.now())
                        .build();
                tasksAssigneeJpaRepository.save(taskAssignee);
                validUserIds.add(userId);
            }
        }

        // Process Email assignments (new enhanced logic)
        if (dto.getAssignedToEmails() != null && !dto.getAssignedToEmails().isEmpty()) {
            // First pass: validate all emails
            for (String email : dto.getAssignedToEmails()) {
                String cleanEmail = email.trim().toLowerCase();

                // Basic email format validation
                if (!cleanEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    invalidEmails.add(email + " (invalid format)");
                    continue;
                }

                // Check if user exists in database
                if (!userJpaRepository.findByEmail(cleanEmail).isPresent()) {
                    invalidEmails.add(email + " (user not found)");
                } else {
                    validEmails.add(cleanEmail);
                }
            }

            // If any emails are invalid, throw exception with detailed information
            if (!invalidEmails.isEmpty()) {
                // Clean up - delete the task since assignment failed
                taskRepository.delete(task);

                String errorMessage = String.format(
                    "Task assignment failed. Invalid emails found: %s. Valid emails: %s",
                    String.join(", ", invalidEmails),
                    validEmails.isEmpty() ? "none" : String.join(", ", validEmails)
                );

                throw new com.example.taskmanagement_backend.exceptions.EmailNotFoundException(errorMessage, invalidEmails);
            }

            // Second pass: assign task to all valid email users
            for (String email : validEmails) {
                User assignee = userJpaRepository.findByEmail(email)
                        .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));
                // Check if already assigned
                boolean alreadyAssigned = tasksAssigneeJpaRepository.existsByTaskAndUserId(task, assignee.getId());
                if (alreadyAssigned) {
                    System.out.println("⚠️ User " + email + " is already assigned to task " + task.getId());
                    continue; // Skip duplicate
                }
                TaskAssignee taskAssignee = TaskAssignee.builder()
                        .task(task)
                        .user(assignee)
                        .assignedAt(LocalDateTime.now())
                        .build();
                tasksAssigneeJpaRepository.save(taskAssignee);

                // ✅ NEW: Gửi thông báo khi task được assign cho user khác
                autoNotificationService.sendTaskAssignedNotification(task, assignee, creator);
            }
        }

        // Log successful assignments
        if (!validUserIds.isEmpty() || !validEmails.isEmpty()) {
            System.out.println("✅ Task successfully assigned to:");
            if (!validUserIds.isEmpty()) {
                System.out.println("   - User IDs: " + validUserIds);
            }
            if (!validEmails.isEmpty()) {
                System.out.println("   - Emails: " + validEmails);
            }
        }

        return mapToDto(task);
    }

    // NEW: Create task and assign by email with enhanced error handling
    public TaskResponseDto createTaskWithEmail(CreateTaskWithEmailRequestDto dto) {
        Project project = null;
        if (dto.getProjectId() != null) {
            project = projectJpaRepository.findById(dto.getProjectId())
                    .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        }

        User creator = userJpaRepository.findById(dto.getCreatorId())
                .orElseThrow(() -> new EntityNotFoundException("Creator not found"));

        Team team = null;
        if (dto.getGroupId() != null) {
            team = teamJpaRepository.findById(dto.getGroupId())
                    .orElseThrow(() -> new EntityNotFoundException("Group (Team) not found"));
        }

        Task task = Task.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .statusKey(dto.getStatus())
                .priorityKey(dto.getPriority())
                .startDate(dto.getStartDate())
                .deadline(dto.getDeadline())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .creator(creator)
                .project(project)
                .team(team)
                .build();

        taskRepository.save(task);

        // Enhanced email validation and assignment with detailed error handling
        if (dto.getAssignedToEmails() != null && !dto.getAssignedToEmails().isEmpty()) {
            List<String> invalidEmails = new ArrayList<>();
            List<String> validEmails = new ArrayList<>();

            // First pass: validate all emails and collect invalid ones
            for (String email : dto.getAssignedToEmails()) {
                // Trim whitespace and convert to lowercase for consistency
                String cleanEmail = email.trim().toLowerCase();

                // Basic email format validation (additional to @Email annotation)
                if (!cleanEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    invalidEmails.add(email + " (invalid format)");
                    continue;
                }

                // Check if user exists in database
                if (!userJpaRepository.findByEmail(cleanEmail).isPresent()) {
                    invalidEmails.add(email + " (user not found)");
                } else {
                    validEmails.add(cleanEmail);
                }
            }

            // If any emails are invalid, throw exception with detailed information
            if (!invalidEmails.isEmpty()) {
                // Clean up - delete the task since assignment failed
                taskRepository.delete(task);

                String errorMessage = String.format(
                    "Task assignment failed. Invalid emails found: %s. Valid emails: %s",
                    String.join(", ", invalidEmails),
                    validEmails.isEmpty() ? "none" : String.join(", ", validEmails)
                );

                throw new com.example.taskmanagement_backend.exceptions.EmailNotFoundException(errorMessage, invalidEmails);
            }

            // Second pass: assign task to all valid users
            for (String email : validEmails) {
                User assignee = userJpaRepository.findByEmail(email)
                        .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));
                // Check if already assigned
                boolean alreadyAssigned = tasksAssigneeJpaRepository.existsByTaskAndUserId(task, assignee.getId());
                if (alreadyAssigned) {
                    System.out.println("⚠️ User " + email + " is already assigned to task " + task.getId());
                    continue; // Skip duplicate
                }
                TaskAssignee taskAssignee = TaskAssignee.builder()
                        .task(task)
                        .user(assignee)
                        .assignedAt(LocalDateTime.now())
                        .build();

                tasksAssigneeJpaRepository.save(taskAssignee);
            }

            System.out.println("✅ Task successfully assigned to " + validEmails.size() + " users: " + String.join(", ", validEmails));
        }

        return mapToDto(task);
    }


    public List<TaskResponseDto> getAllTasks() {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        // Find current user
        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        // Check user role and return appropriate tasks
        boolean isAdminOrOwner = userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") ||
                                auth.getAuthority().equals("ROLE_OWNER") ||
                                auth.getAuthority().equals("ROLE_LEADER"));

        List<Task> tasks;

        if (isAdminOrOwner) {
            // ADMIN, OWNER, LEADER can see all tasks in their organization
            if (currentUser.getOrganization() != null) {
                tasks = taskRepository.findByCreator_OrganizationOrProject_Organization(
                        currentUser.getOrganization(), currentUser.getOrganization());
            } else {
                // If no organization, see all tasks (for system admins)
                tasks = taskRepository.findAll();
            }
            System.out.println("🔒 Admin/Owner user " + currentUserEmail + " accessing " + tasks.size() + " tasks");
        } else {
            // MEMBER, LEADER can only see tasks they created or are assigned to
            tasks = taskRepository.findByCreatorOrAssignees(currentUser, currentUser);
            System.out.println("🔒 Regular user " + currentUserEmail + " accessing " + tasks.size() + " own tasks");
        }

        return tasks.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    // ✅ COMPREHENSIVE: All tasks user participates in with pagination
    public Page<TaskResponseDto> getMyTasks(int page, int size, String sortBy, String sortDir) {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        // Find current user
        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        // Create pageable with sorting
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // ✅ COMPREHENSIVE: Get all tasks user participates in
        Page<Task> myParticipatingTasks = taskRepository.findMyParticipatingTasks(currentUser, pageable);

        System.out.println("🎯 COMPREHENSIVE: User " + currentUserEmail + " accessing " +
                          myParticipatingTasks.getTotalElements() + " participating tasks " +
                          "(page " + (page + 1) + "/" + myParticipatingTasks.getTotalPages() + ")");

        // Convert to DTO
        return myParticipatingTasks.map(this::mapToDto);
    }

    // ✅ PROJECTION: Lightweight summary with participation info and pagination (UPDATED: Combined Task + ProjectTask)
    public Page<MyTaskSummaryDto> getMyTasksSummary(int page, int size, String sortBy, String sortDir) {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        // Find current user
        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        // Create pageable with sorting
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC,
                           mapSortField(sortBy));
        Pageable pageable = PageRequest.of(page, size, sort);

        // ✅ NEW: Get both regular tasks and project tasks
        Page<Task> myTasks = taskRepository.findMyParticipatingTasks(currentUser, pageable);
        Page<ProjectTask> myProjectTasks = projectTaskRepository.findUserProjectTasks(currentUser, pageable);

        // Convert regular tasks to summary DTOs
        List<MyTaskSummaryDto> regularTaskSummaries = myTasks.getContent().stream()
                .map(task -> convertToMyTaskSummaryDto(task, currentUser))
                .collect(Collectors.toList());

        // Convert project tasks to summary DTOs
        List<MyTaskSummaryDto> projectTaskSummaries = myProjectTasks.getContent().stream()
                .map(projectTask -> convertProjectTaskToMyTaskSummaryDto(projectTask, currentUser))
                .collect(Collectors.toList());

        // Combine both lists
        List<MyTaskSummaryDto> combinedSummaries = new ArrayList<>();
        combinedSummaries.addAll(regularTaskSummaries);
        combinedSummaries.addAll(projectTaskSummaries);

        // Sort combined list
        Comparator<MyTaskSummaryDto> comparator = getTaskSummaryComparator(sortBy, sortDir);
        combinedSummaries.sort(comparator);

        // Calculate total elements from both sources
        long totalElements = taskRepository.countMyParticipatingTasks(currentUser) +
                           projectTaskRepository.countUserProjectTasks(currentUser);

        // Apply manual pagination to combined results
        int start = Math.min(page * size, combinedSummaries.size());
        int end = Math.min(start + size, combinedSummaries.size());
        List<MyTaskSummaryDto> paginatedSummaries = combinedSummaries.subList(start, end);

        Page<MyTaskSummaryDto> combinedPage = new PageImpl<>(paginatedSummaries, pageable, totalElements);

        System.out.println("⚡ COMBINED SUMMARY: User " + currentUserEmail + " accessing " +
                          totalElements + " task summaries (REGULAR: " + myTasks.getTotalElements() +
                          " + PROJECT: " + myProjectTasks.getTotalElements() + ") " +
                          "(page " + (page + 1) + "/" + combinedPage.getTotalPages() + ")");

        return combinedPage;
    }

    // ✅ STATISTICS: Get participation statistics (UPDATED: Combined Task + ProjectTask)
    public Map<String, Object> getMyTasksStats() {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        // Count both regular tasks and project tasks
        long totalRegularTasks = taskRepository.countMyParticipatingTasks(currentUser);
        long totalProjectTasks = projectTaskRepository.countUserProjectTasks(currentUser);
        long totalCombinedTasks = totalRegularTasks + totalProjectTasks;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalParticipatingTasks", totalCombinedTasks);
        stats.put("totalRegularTasks", totalRegularTasks);
        stats.put("totalProjectTasks", totalProjectTasks);
        stats.put("userEmail", currentUserEmail);
        stats.put("userId", currentUser.getId());

        System.out.println("📊 COMBINED STATS: User " + currentUserEmail + " has " + totalCombinedTasks +
                          " total tasks (" + totalRegularTasks + " regular + " + totalProjectTasks + " project)");

        return stats;
    }

    public TaskResponseDto getTaskById(Long id) {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        // Check if user has permission to view this task
        boolean hasPermission = canUserAccessTask(currentUser, task, userDetails);

        if (!hasPermission) {
            throw new SecurityException("Access denied: You don't have permission to view this task");
        }

        return mapToDto(task);
    }

    private boolean canUserAccessTask(User currentUser, Task task, UserDetails userDetails) {
        // Check if user is admin/owner/leader
        boolean isAdminOrOwner = userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") ||
                                auth.getAuthority().equals("ROLE_OWNER") ||
                                auth.getAuthority().equals("ROLE_LEADER"));

        if (isAdminOrOwner) {
            // Admin/Owner can see tasks in their organization
            if (currentUser.getOrganization() != null) {
                return task.getCreator().getOrganization() != null &&
                       task.getCreator().getOrganization().equals(currentUser.getOrganization());
            }
            return true; // System admin can see all
        } else {
            // Regular users can see tasks they created or are assigned to
            if (task.getCreator().equals(currentUser)) {
                return true; // User created this task
            }

            // Check if user is assigned to this task
            if (task.getAssignees().stream()
                    .anyMatch(assignee -> assignee.getUser().equals(currentUser))) {
                return true;
            }

            // Check if user is a member of the task's team
            if (task.getTeam() != null) {
                return teamMemberJpaRepository.existsByTeamIdAndUserId(task.getTeam().getId(), currentUser.getId());
            }

            // Check if user is a member of the task's project team
            if (task.getProject() != null && task.getProject().getTeam() != null) {
                return teamMemberJpaRepository.existsByTeamIdAndUserId(task.getProject().getTeam().getId(), currentUser.getId());
            }

            return false;
        }
    }

    public TaskResponseDto updateTask(Long id, UpdateTaskRequestDto dto) {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        // ✅ FIX: Check permission using repository query instead of accessing lazy collection
        boolean isCreator = task.getCreator().getId().equals(currentUser.getId());
        boolean isAssignee = tasksAssigneeJpaRepository.existsByTaskAndUserId(task, currentUser.getId());
        boolean canUpdate = isCreator || isAssignee;

        if (!canUpdate) {
            throw new SecurityException("You don't have permission to update this task");
        }

        // ✅ NEW: Store old values for activity logging
        String oldTitle = task.getTitle();
        String oldDescription = task.getDescription();
        String oldStatus = task.getStatusKey();
        String oldPriority = task.getPriorityKey();
        String oldComment = task.getComment();

        // Update basic task fields and log activities
        if (dto.getTitle() != null && !dto.getTitle().equals(oldTitle)) {
            task.setTitle(dto.getTitle());
            taskActivityService.logTitleChanged(task, oldTitle, dto.getTitle());
            // ✅ NEW: Audit log - Task updated
            auditLogger.logTaskUpdated(currentUser.getId(), task.getId(), task.getTitle(),
                String.format("Title changed from '%s' to '%s'", oldTitle, dto.getTitle()));
        }

        if (dto.getDescription() != null && !dto.getDescription().equals(oldDescription)) {
            task.setDescription(dto.getDescription());
            taskActivityService.logDescriptionChanged(task, oldDescription, dto.getDescription());
        }

        if (dto.getStatus() != null && !dto.getStatus().equals(oldStatus)) {
            task.setStatusKey(dto.getStatus());
            taskActivityService.logStatusChanged(task, oldStatus, dto.getStatus());
            // ✅ NEW: Audit log - Task status changed
            auditLogger.logTaskStatusChanged(currentUser.getId(), task.getId(), task.getTitle(), oldStatus, dto.getStatus());

            // ✅ Log special status changes
            if ("DONE".equals(dto.getStatus()) || "COMPLETED".equals(dto.getStatus())) {
                taskActivityService.logTaskCompleted(task);
            } else if (("TODO".equals(dto.getStatus()) || "IN_PROGRESS".equals(dto.getStatus())) &&
                      ("DONE".equals(oldStatus) || "COMPLETED".equals(oldStatus))) {
                taskActivityService.logTaskReopened(task);
            }
        }

        if (dto.getPriority() != null && !dto.getPriority().equals(oldPriority)) {
            task.setPriorityKey(dto.getPriority());
            taskActivityService.logPriorityChanged(task, oldPriority, dto.getPriority());
        }

        if (dto.getStartDate() != null) task.setStartDate(dto.getStartDate());

        if (dto.getDeadline() != null) {
            String oldDeadline = task.getDeadline() != null ? task.getDeadline().toString() : null;
            String newDeadline = dto.getDeadline().toString();
            if (!newDeadline.equals(oldDeadline)) {
                task.setDeadline(dto.getDeadline());
                taskActivityService.logDeadlineChanged(task, oldDeadline, newDeadline);
            }
        }

        // ✅ NEW: Update comment and url file fields
        if (dto.getComment() != null && !dto.getComment().equals(oldComment)) {
            task.setComment(dto.getComment());
            taskActivityService.logCommentChanged(task, oldComment, dto.getComment());
        }

        if (dto.getUrlFile() != null) task.setUrlFile(dto.getUrlFile());

        if (dto.getGroupId() != null) {
            Team team = teamJpaRepository.findById(dto.getGroupId())
                    .orElseThrow(() -> new EntityNotFoundException("Group (Team) not found"));
            task.setTeam(team);
        }

        // Enhanced assignee management
        handleAssigneeUpdates(task, dto);

        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        // ✅ NEW: Publish Kafka event for search indexing after task update
        try {
            searchEventPublisher.publishTaskUpdated(task.getId(), currentUser.getId());
            log.info("📤 Published TASK_UPDATED event to Kafka for task: {}", task.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish TASK_UPDATED event for task {}: {}", task.getId(), e.getMessage());
            // Don't throw exception to avoid blocking task update
        }

        return mapToDto(task);
    }

    // Helper method to handle assignee updates with comprehensive support
    private void handleAssigneeUpdates(Task task, UpdateTaskRequestDto dto) {
        List<String> invalidEmails = new ArrayList<>();
        List<String> validEmails = new ArrayList<>();
        List<Long> validUserIds = new ArrayList<>();

        // 1. REPLACE ALL ASSIGNEES (if assignedToIds or assignedToEmails is provided)
        if ((dto.getAssignedToIds() != null && !dto.getAssignedToIds().isEmpty()) ||
            (dto.getAssignedToEmails() != null && !dto.getAssignedToEmails().isEmpty())) {

            // Remove all existing assignees
            tasksAssigneeJpaRepository.deleteByTask(task);

            // Add new assignees by User ID
            if (dto.getAssignedToIds() != null) {
                for (Long userId : dto.getAssignedToIds()) {
                    User assignee = userJpaRepository.findById(userId)
                            .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

                    TaskAssignee taskAssignee = TaskAssignee.builder()
                            .task(task)
                            .user(assignee)
                            .assignedAt(LocalDateTime.now())
                            .build();
                    tasksAssigneeJpaRepository.save(taskAssignee);
                    validUserIds.add(userId);
                }
            }

            // Add new assignees by Email
            if (dto.getAssignedToEmails() != null) {
                validateAndAddAssigneesByEmail(task, dto.getAssignedToEmails(), invalidEmails, validEmails);
            }
        }

        // 2. ADD SPECIFIC ASSIGNEES (additive operations)
        if (dto.getAddAssigneeIds() != null && !dto.getAddAssigneeIds().isEmpty()) {
            for (Long userId : dto.getAddAssigneeIds()) {
                // Check if user is already assigned
                boolean alreadyAssigned = task.getAssignees().stream()
                        .anyMatch(assignee -> assignee.getUser().getId().equals(userId));

                if (!alreadyAssigned) {
                    User assignee = userJpaRepository.findById(userId)
                            .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

                    TaskAssignee taskAssignee = TaskAssignee.builder()
                            .task(task)
                            .user(assignee)
                            .assignedAt(LocalDateTime.now())
                            .build();
                    tasksAssigneeJpaRepository.save(taskAssignee);
                    validUserIds.add(userId);
                }
            }
        }

        if (dto.getAddAssigneeEmails() != null && !dto.getAddAssigneeEmails().isEmpty()) {
            List<String> emailsToAdd = new ArrayList<>();
            for (String email : dto.getAddAssigneeEmails()) {
                String cleanEmail = email.trim().toLowerCase();

                // Check if user exists and is already assigned using repository query instead of lazy loading
                User potentialAssignee = userJpaRepository.findByEmail(cleanEmail).orElse(null);
                if (potentialAssignee != null) {
                    boolean alreadyAssigned = tasksAssigneeJpaRepository
                            .existsByTaskAndUserId(task, potentialAssignee.getId());

                    if (!alreadyAssigned) {
                        emailsToAdd.add(email);
                    } else {
                        System.out.println("⚠️ User " + cleanEmail + " is already assigned to task " + task.getId());
                    }
                } else {
                    emailsToAdd.add(email); // Will be validated in validateAndAddAssigneesByEmail
                }
            }

            if (!emailsToAdd.isEmpty()) {
                validateAndAddAssigneesByEmail(task, emailsToAdd, invalidEmails, validEmails);
            }
        }

        // 3. REMOVE SPECIFIC ASSIGNEES (removal operations)
        if (dto.getRemoveAssigneeIds() != null && !dto.getRemoveAssigneeIds().isEmpty()) {
            for (Long userId : dto.getRemoveAssigneeIds()) {
                TaskAssignee assigneeToRemove = tasksAssigneeJpaRepository
                        .findByTaskAndUser_Id(task, userId)
                        .orElse(null);

                if (assigneeToRemove != null) {
                    tasksAssigneeJpaRepository.delete(assigneeToRemove);
                    System.out.println("✅ Removed assignee with ID: " + userId);
                }
            }
        }

        if (dto.getRemoveAssigneeEmails() != null && !dto.getRemoveAssigneeEmails().isEmpty()) {
            for (String email : dto.getRemoveAssigneeEmails()) {
                String cleanEmail = email.trim().toLowerCase();
                User userToRemove = userJpaRepository.findByEmail(cleanEmail).orElse(null);

                if (userToRemove != null) {
                    TaskAssignee assigneeToRemove = tasksAssigneeJpaRepository
                            .findByTaskAndUser_Id(task, userToRemove.getId())
                            .orElse(null);

                    if (assigneeToRemove != null) {
                        tasksAssigneeJpaRepository.delete(assigneeToRemove);
                        System.out.println("✅ Removed assignee with email: " + cleanEmail);
                    }
                }
            }
        }

        // Handle email validation errors
        if (!invalidEmails.isEmpty()) {
            String errorMessage = String.format(
                "Task update failed. Invalid emails found: %s. Valid emails: %s",
                String.join(", ", invalidEmails),
                validEmails.isEmpty() ? "none" : String.join(", ", validEmails)
            );
            throw new com.example.taskmanagement_backend.exceptions.EmailNotFoundException(errorMessage, invalidEmails);
        }

        // Log successful updates
        if (!validUserIds.isEmpty() || !validEmails.isEmpty()) {
            System.out.println("✅ Task assignees updated successfully:");
            if (!validUserIds.isEmpty()) {
                System.out.println("   - User IDs: " + validUserIds);
            }
            if (!validEmails.isEmpty()) {
                System.out.println("   - Emails: " + validEmails);
            }
        }
    }

    // Helper method to validate and add assignees by email
    private void validateAndAddAssigneesByEmail(Task task, List<String> emails, List<String> invalidEmails, List<String> validEmails) {
        for (String email : emails) {
            String cleanEmail = email.trim().toLowerCase();

            // Email format validation
            if (!cleanEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                invalidEmails.add(email + " (invalid format)");
                continue;
            }

            // Check if user exists
            User assignee = userJpaRepository.findByEmail(cleanEmail).orElse(null);
            if (assignee == null) {
                invalidEmails.add(email + " (user not found)");
                continue;
            }

            // Check if already assigned
            boolean alreadyAssigned = tasksAssigneeJpaRepository.existsByTaskAndUserId(task, assignee.getId());
            if (alreadyAssigned) {
                System.out.println("⚠️ User " + cleanEmail + " is already assigned to task " + task.getId());
                continue; // Skip duplicate
            }

            // Add assignee
            TaskAssignee taskAssignee = TaskAssignee.builder()
                    .task(task)
                    .user(assignee)
                    .assignedAt(LocalDateTime.now())
                    .build();
            tasksAssigneeJpaRepository.save(taskAssignee);
            validEmails.add(cleanEmail);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteTask(Long id) {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        // Check if user has permission to delete this task
        boolean isCreator = task.getCreator().getId().equals(currentUser.getId());
        // Use repository query to avoid lazy loading issues
        boolean isAssignee = tasksAssigneeJpaRepository.existsByTaskAndUserId(task, currentUser.getId());
        boolean canDelete = isCreator || isAssignee;

        if (!canDelete) {
            throw new SecurityException("You don't have permission to delete this task");
        }

        try {
            // ✅ NEW: Audit log - Task deleted (before deletion to capture task info)
            auditLogger.logTaskDeleted(currentUser.getId(), task.getId(), task.getTitle());

            // 🔧 FIX: Delete all related data WITHOUT logging activities to avoid session conflicts

            // 1. Delete all task attachments from S3 and database
            try {
                List<com.example.taskmanagement_backend.dtos.TaskAttachmentDto.TaskAttachmentResponseDto> attachments =
                    taskAttachmentService.getTaskAttachments(id);

                if (!attachments.isEmpty()) {
                    log.info("🗑️ Deleting {} attachments for task {}", attachments.size(), id);

                    // Delete each attachment from S3 only (no activity logging during deletion)
                    for (var attachment : attachments) {
                        try {
                            s3FileUploadService.deleteFile(attachment.getFileKey());
                            log.info("✅ Deleted S3 file: {} ({})", attachment.getOriginalFilename(), attachment.getFileKey());
                        } catch (Exception e) {
                            log.warn("⚠️ Failed to delete S3 file: {} - {}", attachment.getFileKey(), e.getMessage());
                        }
                    }

                    // Delete all attachment records from database
                    taskAttachmentService.deleteAllAttachmentsForTask(id);
                    log.info("✅ Deleted all attachment records for task {}", id);
                }
            } catch (Exception e) {
                log.warn("⚠️ Error deleting attachments: {} - continuing with task deletion", e.getMessage());
            }

            // 2. Delete all task activities FIRST to avoid foreign key constraints
            try {
                taskActivityRepository.deleteByTaskId(id);
                log.info("✅ Deleted all activities for task {}", id);
            } catch (Exception e) {
                log.warn("⚠️ Error deleting task activities: {} - continuing", e.getMessage());
            }

            // 3. Delete all task assignees using bulk delete by task ID
            tasksAssigneeJpaRepository.deleteByTaskId(id);

            // 4. Delete all task comments using bulk delete by task ID
            taskCommentRepository.deleteByTaskId(id);

            // 5. Delete all task checklists using bulk delete by task ID
            taskChecklistJpaRepository.deleteByTaskId(id);

            // 6. Flush changes to ensure all related entities are deleted
            entityManager.flush();
            entityManager.clear();

            // 7. Now delete the task itself using deleteById
            taskRepository.deleteById(id);

            // ✅ NEW: Publish Kafka event for search indexing after task deletion
            try {
                searchEventPublisher.publishTaskDeleted(id, currentUser.getId());
                log.info("📤 Published TASK_DELETED event to Kafka for task: {}", id);
            } catch (Exception e) {
                log.warn("⚠️ Failed to publish TASK_DELETED event for task {}: {}", id, e.getMessage());
                // Don't throw exception since the task is already deleted
            }

            log.info("✅ Task {} and all related data deleted successfully by user {}", id, currentUserEmail);

        } catch (Exception e) {
            log.error("❌ Error deleting task {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete task: " + e.getMessage(), e);
        }
    }

    // ✅ REUSABLE: Get all tasks in a project (across all teams)
    public List<TaskResponseDto> getTasksByProjectId(Long projectId) {
        // Get current authenticated user for security check
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        // Get tasks by project
        List<Task> tasks = taskRepository.findByProjectId(projectId);

        // Filter tasks based on user permissions
        List<Task> accessibleTasks = tasks.stream()
                .filter(task -> canUserAccessTask(currentUser, task, userDetails))
                .collect(Collectors.toList());

        System.out.println("🎯 PROJECT TASKS: User " + currentUserEmail + " accessing " +
                          accessibleTasks.size() + " tasks in project " + projectId);

        return accessibleTasks.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    // ✅ REUSABLE: Get all tasks of a team (across all projects)
    public List<TaskResponseDto> getTasksByTeamId(Long teamId) {
        // Get current authenticated user for security check
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        // Get tasks by team
        List<Task> tasks = taskRepository.findByTeamId(teamId);

        // Filter tasks based on user permissions
        List<Task> accessibleTasks = tasks.stream()
                .filter(task -> canUserAccessTask(currentUser, task, userDetails))
                .collect(Collectors.toList());

        System.out.println("🎯 TEAM TASKS: User " + currentUserEmail + " accessing " +
                          accessibleTasks.size() + " tasks in team " + teamId);

        return accessibleTasks.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    // ✅ NEW: Get all tasks from all projects of a team
    public List<ProjectTaskResponseDto> getAllTasksFromAllProjectsOfTeam(Long teamId) {
        // Get current authenticated user for security check
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        // Get all projects of the team
        List<Project> projects = projectJpaRepository.findByTeam(teamJpaRepository.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Team not found")));

        // Get all project tasks from all projects of the team
        List<ProjectTask> allProjectTasks = projects.stream()
                .flatMap(project -> projectTaskRepository.findByProject(project).stream())
                .collect(Collectors.toList());

        // Map to DTOs
        return allProjectTasks.stream()
                .map(projectTask -> ProjectTaskResponseDto.builder()
                        .id(projectTask.getId())
                        .title(projectTask.getTitle())
                        .description(projectTask.getDescription())
                        .status(projectTask.getStatus())
                        .priority(projectTask.getPriority())
                        .startDate(projectTask.getStartDate())
                        .deadline(projectTask.getDeadline())
                        .projectId(projectTask.getProject() != null ? projectTask.getProject().getId() : null)
                        .createdAt(projectTask.getCreatedAt())
                        .updatedAt(projectTask.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ✅ NEW: Get combined my tasks (both Task and ProjectTask) with pagination
    public Page<TaskResponseDto> getMyCombinedTasks(int page, int size, String sortBy, String sortDir) {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        // Find current user
        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        // Create pageable with sorting
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC,
                           mapSortField(sortBy));
        Pageable pageable = PageRequest.of(page, size, sort);

        // Get regular tasks (from Task table)
        Page<Task> myParticipatingTasks = taskRepository.findMyParticipatingTasks(currentUser, pageable);

        // Get project tasks (from ProjectTask table)
        Page<ProjectTask> myProjectTasks = projectTaskRepository.findUserProjectTasks(currentUser, pageable);

        // Convert both to DTOs
        List<TaskResponseDto> regularTaskDtos = myParticipatingTasks.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        List<TaskResponseDto> projectTaskDtos = myProjectTasks.getContent().stream()
                .map(this::mapProjectTaskToDto)
                .collect(Collectors.toList());

        // Combine both lists
        List<TaskResponseDto> combinedTasks = new ArrayList<>();
        combinedTasks.addAll(regularTaskDtos);
        combinedTasks.addAll(projectTaskDtos);

        // Sort combined list
        Comparator<TaskResponseDto> comparator = getTaskComparator(sortBy, sortDir);
        combinedTasks.sort(comparator);

        // Calculate pagination for combined results
        long totalElements = taskRepository.countMyParticipatingTasks(currentUser) +
                           projectTaskRepository.countUserProjectTasks(currentUser);

        // Apply manual pagination to combined results
        int start = Math.min(page * size, combinedTasks.size());
        int end = Math.min(start + size, combinedTasks.size());
        List<TaskResponseDto> paginatedTasks = combinedTasks.subList(start, end);

        Page<TaskResponseDto> combinedPage = new PageImpl<>(paginatedTasks, pageable, totalElements);

        System.out.println("🎯 COMBINED TASKS: User " + currentUserEmail + " accessing " +
                          totalElements + " total tasks (" +
                          myParticipatingTasks.getTotalElements() + " regular + " +
                          myProjectTasks.getTotalElements() + " project tasks) " +
                          "(page " + (page + 1) + "/" + combinedPage.getTotalPages() + ")");

        return combinedPage;
    }

    // ✅ NEW: Get combined my tasks summary with participation info
    public Page<MyTaskSummaryDto> getMyCombinedTasksSummary(int page, int size, String sortBy, String sortDir) {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        // Find current user
        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        // Create pageable with sorting
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC,
                           mapSortField(sortBy));
        Pageable pageable = PageRequest.of(page, size, sort);

        // Get regular tasks
        Page<Task> myTasks = taskRepository.findMyParticipatingTasks(currentUser, pageable);

        // Get project tasks
        Page<ProjectTask> myProjectTasks = projectTaskRepository.findUserProjectTasks(currentUser, pageable);

        // Convert to summary DTOs
        List<MyTaskSummaryDto> regularTaskSummaries = myTasks.getContent().stream()
                .map(task -> convertToMyTaskSummaryDto(task, currentUser))
                .collect(Collectors.toList());

        List<MyTaskSummaryDto> projectTaskSummaries = myProjectTasks.getContent().stream()
                .map(projectTask -> convertProjectTaskToMyTaskSummaryDto(projectTask, currentUser))
                .collect(Collectors.toList());

        // Combine both lists
        List<MyTaskSummaryDto> combinedSummaries = new ArrayList<>();
        combinedSummaries.addAll(regularTaskSummaries);
        combinedSummaries.addAll(projectTaskSummaries);

        // Sort combined list
        Comparator<MyTaskSummaryDto> comparator = getTaskSummaryComparator(sortBy, sortDir);
        combinedSummaries.sort(comparator);

        // Calculate total elements
        long totalElements = taskRepository.countMyParticipatingTasks(currentUser) +
                           projectTaskRepository.countUserProjectTasks(currentUser);

        // Apply manual pagination
        int start = Math.min(page * size, combinedSummaries.size());
        int end = Math.min(start + size, combinedSummaries.size());
        List<MyTaskSummaryDto> paginatedSummaries = combinedSummaries.subList(start, end);

        Page<MyTaskSummaryDto> combinedPage = new PageImpl<>(paginatedSummaries, pageable, totalElements);

        System.out.println("⚡ COMBINED SUMMARIES: User " + currentUserEmail + " accessing " +
                          totalElements + " task summaries with participation info " +
                          "(page " + (page + 1) + "/" + combinedPage.getTotalPages() + ")");

        return combinedPage;
    }

    // ✅ HELPER: Convert Task entity to MyTaskSummaryDto
    private MyTaskSummaryDto convertToMyTaskSummaryDto(Task task, User currentUser) {
        // Determine participation type (simplified)
        String participationType = "OTHER";
        if (task.getCreator().equals(currentUser)) {
            participationType = "CREATOR";
        } else if (task.getAssignees().stream().anyMatch(ta -> ta.getUser().equals(currentUser))) {
            participationType = "ASSIGNEE";
        }

        // Get creator name
        String creatorName = "";
        if (task.getCreator().getUserProfile() != null) {
            String firstName = task.getCreator().getUserProfile().getFirstName();
            String lastName = task.getCreator().getUserProfile().getLastName();
            creatorName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
            creatorName = creatorName.trim();
        }

        return MyTaskSummaryDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatusKey())
                .priority(task.getPriorityKey())
                .startDate(task.getStartDate())
                .deadline(task.getDeadline())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .creatorId(task.getCreator() != null ? task.getCreator().getId() : null)
                .creatorName(creatorName)
                .projectId(task.getProject() != null ? task.getProject().getId() : null)
                .projectName(task.getProject() != null ? task.getProject().getName() : null)
                .teamId(task.getTeam() != null ? task.getTeam().getId() : null)
                .teamName(task.getTeam() != null ? task.getTeam().getName() : null)
                .checklistCount((long) task.getChecklists().size())
                .assigneeCount((long) task.getAssignees().size())
                .participationType(participationType)

                // ✅ ENHANCED: Google Calendar information for summary
                .googleCalendarEventId(task.getGoogleCalendarEventId())
                .googleCalendarEventUrl(task.getGoogleCalendarEventUrl())
                .googleMeetLink(task.getGoogleMeetLink())
                .isSyncedToCalendar(task.getIsSyncedToCalendar())
                .calendarSyncedAt(task.getCalendarSyncedAt())

                .build();
    }

    // ✅ HELPER: Map sort field names
    private String mapSortField(String sortBy) {
        switch (sortBy) {
            case "startDate": return "startDate";
            case "deadline": return "deadline";
            case "createdAt": return "createdAt";
            case "updatedAt": return "updatedAt";
            case "title": return "title";
            case "priority": return "priority";
            case "status": return "status";
            default: return "updatedAt";
        }
    }

    // ✅ HELPER: Map ProjectTask to TaskResponseDto
    private TaskResponseDto mapProjectTaskToDto(ProjectTask projectTask) {
        return TaskResponseDto.builder()
                .id(projectTask.getId())
                .title(projectTask.getTitle())
                .description(projectTask.getDescription())
                .status(projectTask.getStatus().toString())
                .priority(projectTask.getPriority().toString())
                .startDate(projectTask.getStartDate())
                .deadline(projectTask.getDeadline())
                .createdAt(projectTask.getCreatedAt())
                .updatedAt(projectTask.getUpdatedAt())
                .creatorId(projectTask.getCreator() != null ? projectTask.getCreator().getId() : null)
                .projectId(projectTask.getProject() != null ? projectTask.getProject().getId() : null)
                .groupId(null) // ProjectTask doesn't have team
                .checklists(List.of()) // ProjectTask might not have checklists
                .build();
    }

    // ✅ HELPER: Convert ProjectTask to MyTaskSummaryDto
    private MyTaskSummaryDto convertProjectTaskToMyTaskSummaryDto(ProjectTask projectTask, User currentUser) {
        // Determine participation type
        String participationType = "OTHER";
        if (projectTask.getCreator().equals(currentUser)) {
            participationType = "CREATOR";
        } else if (projectTask.getAssignee() != null && projectTask.getAssignee().equals(currentUser)) {
            participationType = "ASSIGNEE";
        } else if (projectTask.getAdditionalAssignees() != null &&
                   projectTask.getAdditionalAssignees().contains(currentUser)) {
            participationType = "ASSIGNEE";
        }

        // Get creator name
        String creatorName = "";
        if (projectTask.getCreator().getUserProfile() != null) {
            String firstName = projectTask.getCreator().getUserProfile().getFirstName();
            String lastName = projectTask.getCreator().getUserProfile().getLastName();
            creatorName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
            creatorName = creatorName.trim();
        }

        // Count assignees (primary + additional)
        long assigneeCount = 1; // Primary assignee
        if (projectTask.getAdditionalAssignees() != null) {
            assigneeCount += projectTask.getAdditionalAssignees().size();
        }

        return MyTaskSummaryDto.builder()
                .id(projectTask.getId())
                .title(projectTask.getTitle())
                .description(projectTask.getDescription())
                .status(projectTask.getStatus().toString())
                .priority(projectTask.getPriority().toString())
                .startDate(projectTask.getStartDate())
                .deadline(projectTask.getDeadline())
                .createdAt(projectTask.getCreatedAt())
                .updatedAt(projectTask.getUpdatedAt())
                .creatorId(projectTask.getCreator().getId())
                .creatorName(creatorName)
                .projectId(projectTask.getProject() != null ? projectTask.getProject().getId() : null)
                .projectName(projectTask.getProject() != null ? projectTask.getProject().getName() : null)
                .teamId(null) // ProjectTask doesn't have direct team
                .teamName(null)
                .checklistCount(0L) // ProjectTask might not have checklists
                .assigneeCount(assigneeCount)
                .participationType(participationType)
                .build();
    }

    // ✅ HELPER: Get comparator for TaskResponseDto
    private Comparator<TaskResponseDto> getTaskComparator(String sortBy, String sortDir) {
        Comparator<TaskResponseDto> comparator;

        switch (sortBy) {
            case "title":
                comparator = Comparator.comparing(TaskResponseDto::getTitle, Comparator.nullsLast(String::compareTo));
                break;
            case "startDate":
                comparator = Comparator.comparing(TaskResponseDto::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "deadline":
                comparator = Comparator.comparing(TaskResponseDto::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "createdAt":
                comparator = Comparator.comparing(TaskResponseDto::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
                break;
            case "priority":
                comparator = Comparator.comparing(TaskResponseDto::getPriority, Comparator.nullsLast(String::compareTo));
                break;
            case "status":
                comparator = Comparator.comparing(TaskResponseDto::getStatus, Comparator.nullsLast(String::compareTo));
                break;
            default:
                comparator = Comparator.comparing(TaskResponseDto::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
                break;
        }

        return sortDir.equalsIgnoreCase("desc") ? comparator.reversed() : comparator;
    }

    // ✅ HELPER: Get comparator for MyTaskSummaryDto
    private Comparator<MyTaskSummaryDto> getTaskSummaryComparator(String sortBy, String sortDir) {
        Comparator<MyTaskSummaryDto> comparator;

        switch (sortBy) {
            case "title":
                comparator = Comparator.comparing(MyTaskSummaryDto::getTitle, Comparator.nullsLast(String::compareTo));
                break;
            case "startDate":
                comparator = Comparator.comparing(MyTaskSummaryDto::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "deadline":
                comparator = Comparator.comparing(MyTaskSummaryDto::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "createdAt":
                comparator = Comparator.comparing(MyTaskSummaryDto::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
                break;
            case "priority":
                comparator = Comparator.comparing(MyTaskSummaryDto::getPriority, Comparator.nullsLast(String::compareTo));
                break;
            case "status":
                comparator = Comparator.comparing(MyTaskSummaryDto::getStatus, Comparator.nullsLast(String::compareTo));
                break;
            default:
                comparator = Comparator.comparing(MyTaskSummaryDto::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
                break;
        }

        return sortDir.equalsIgnoreCase("desc") ? comparator.reversed() : comparator;
    }

    public TaskResponseDto mapToDto(Task task) {
        // ✅ FIX: Get assignee information using repository query to avoid lazy loading issues
        List<TaskAssignee> assignees = tasksAssigneeJpaRepository.findByTask(task);
        List<Long> assignedToIds = assignees.stream()
                .map(assignee -> assignee.getUser().getId())
                .collect(Collectors.toList());
        List<String> assignedToEmails = assignees.stream()
                .map(assignee -> assignee.getUser().getEmail())
                .collect(Collectors.toList());

        // NEW: Get creator profile
        UserProfileDto creatorProfile = userProfileMapper.toUserProfileDto(task.getCreator());
        // NEW: Get assignee profiles
        List<UserProfileDto> assigneeProfiles = userProfileMapper.toUserProfileDtoList(
                assignees.stream().map(TaskAssignee::getUser).collect(Collectors.toList())
        );

        return TaskResponseDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatusKey())
                .priority(task.getPriorityKey())
                .startDate(task.getStartDate())
                .deadline(task.getDeadline())
                .comment(task.getComment())             // ✅ NEW: Add comment field
                .urlFile(task.getUrlFile())             // ✅ NEW: Add url file field

                // ✅ ENHANCED: Google Calendar information
                .googleCalendarEventId(task.getGoogleCalendarEventId())
                .googleCalendarEventUrl(task.getGoogleCalendarEventUrl())
                .googleMeetLink(task.getGoogleMeetLink())
                .isSyncedToCalendar(task.getIsSyncedToCalendar())
                .calendarSyncedAt(task.getCalendarSyncedAt())

                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .assignedToIds(assignedToIds)           // ✅ ADD: User IDs for backend processing
                .assignedToEmails(assignedToEmails)     // ✅ ADD: User emails for frontend avatars
                .creatorId(task.getCreator() != null ? task.getCreator().getId() : null)
                .projectId(task.getProject() != null ? task.getProject().getId() : null)
                .groupId(task.getTeam() != null ? task.getTeam().getId() : null)
                .checklists(List.of()) // Task might not have checklists
                .creatorProfile(creatorProfile)
                .assigneeProfiles(assigneeProfiles)
                .build();
    }

    /**
     * Convert Task entity to TaskResponseDto
     * Used by ProfilePageService and other services
     */
    public TaskResponseDto convertToTaskResponseDto(Task task) {
        if (task == null) {
            return null;
        }

        return TaskResponseDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus() != null ? task.getStatus().toString() : null)
                .priority(task.getPriority() != null ? task.getPriority().toString() : null)
                .deadline(task.getDeadline())
                .isPublic(task.getIsPublic())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .creatorProfile(task.getCreator() != null ? userProfileMapper.toUserProfileDto(task.getCreator()) : null)
                .projectId(task.getProject() != null ? task.getProject().getId() : null)
                .build();
    }

    // ✅ NEW: File Upload Methods for Task Integration

    /**
     * Handle multiple file uploads for a task using TransferManager
     * Returns comma-separated file keys for storage in task.urlFile
     */
    public String handleFileUploads(Long taskId, List<org.springframework.web.multipart.MultipartFile> files) {
        log.info("📤 Handling {} file uploads for task {}", files.size(), taskId);

        List<String> uploadedFileKeys = new ArrayList<>();
        List<java.util.concurrent.CompletableFuture<com.example.taskmanagement_backend.dtos.FileUploadDto.FileUploadResponseDto>> uploadFutures = new ArrayList<>();

        // Start all uploads concurrently for better performance
        for (org.springframework.web.multipart.MultipartFile file : files) {
            if (!file.isEmpty()) {
                try {
                    java.util.concurrent.CompletableFuture<com.example.taskmanagement_backend.dtos.FileUploadDto.FileUploadResponseDto> uploadFuture =
                        s3FileUploadService.uploadFileAsync(
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getInputStream(),
                            file.getSize(),
                            taskId
                        );
                    uploadFutures.add(uploadFuture);
                } catch (Exception e) {
                    log.error("❌ Failed to start upload for file: {}", file.getOriginalFilename(), e);
                }
            }
        }

        // Wait for all uploads to complete
        for (java.util.concurrent.CompletableFuture<com.example.taskmanagement_backend.dtos.FileUploadDto.FileUploadResponseDto> future : uploadFutures) {
            try {
                var response = future.get(60, java.util.concurrent.TimeUnit.SECONDS); // 60 second timeout
                if ("SUCCESS".equals(response.getUploadStatus())) {
                    uploadedFileKeys.add(response.getFileKey());
                    log.info("✅ Successfully uploaded: {}", response.getFileName());
                } else {
                    log.error("❌ Upload failed: {}", response.getMessage());
                }
            } catch (Exception e) {
                log.error("❌ Upload timeout or error", e);
            }
        }

        return String.join(",", uploadedFileKeys);
    }

    /**
     * Handle file deletions from S3
     */
    public void handleFileDeletions(List<String> fileKeys) {
        log.info("🗑️ Deleting {} files from S3", fileKeys.size());

        for (String fileKey : fileKeys) {
            try {
                boolean deleted = s3FileUploadService.deleteFile(fileKey);
                if (deleted) {
                    log.info("✅ Successfully deleted file: {}", fileKey);
                } else {
                    log.warn("⚠️ Failed to delete file: {}", fileKey);
                }
            } catch (Exception e) {
                log.error("❌ Error deleting file: {}", fileKey, e);
            }
        }
    }

    /**
     * Parse file URLs from task.urlFile field and generate download URLs
     */
    public List<String> generateDownloadUrls(String urlFile) {
        if (urlFile == null || urlFile.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> downloadUrls = new ArrayList<>();
        String[] fileKeys = urlFile.split(",");

        for (String fileKey : fileKeys) {
            try {
                String downloadUrl = s3FileUploadService.generatePresignedDownloadUrl(
                    fileKey.trim(), java.time.Duration.ofHours(1));
                downloadUrls.add(downloadUrl);
            } catch (Exception e) {
                log.error("❌ Failed to generate download URL for: {}", fileKey, e);
            }
        }

        return downloadUrls;
    }

    /**
     * Xóa Google Calendar Event ID khỏi task
     */
    @org.springframework.transaction.annotation.Transactional
    public void clearCalendarEventId(Long taskId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUserEmail = userDetails.getUsername();

        User currentUser = userJpaRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        // Check if user has permission to update this task
        boolean isCreator = task.getCreator().getId().equals(currentUser.getId());
        boolean isAssigned = tasksAssigneeJpaRepository.existsByTaskAndUserId(task, currentUser.getId());

        if (!isCreator && !isAssigned) {
            throw new SecurityException("Access denied: You don't have permission to update this task");
        }

        // Clear calendar event ID
        task.setGoogleCalendarEventId(null);
        taskRepository.save(task);
    }

    // Add missing import and log declaration
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TaskService.class);

    /**
     * ✅ NEW: Create task with Google Calendar option
     * This method delegates to createTask which already has calendar integration
     */
    @org.springframework.transaction.annotation.Transactional
    public TaskResponseDto createTaskWithCalendarOption(CreateTaskRequestDto dto) {
        // Delegate to the existing createTask method which already handles calendar integration
        return createTask(dto);
    }
}
