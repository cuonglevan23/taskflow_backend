package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.TeamDto.CreateTeamResponseDto;
import com.example.taskmanagement_backend.dtos.TeamDto.TeamResponseDto;
import com.example.taskmanagement_backend.dtos.TeamDto.UpdateTeamResponseDto;
import com.example.taskmanagement_backend.dtos.TeamMemberDto.TeamMemberResponseDto;
import com.example.taskmanagement_backend.dtos.TaskActivityDto.TaskActivityResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.Team;
import com.example.taskmanagement_backend.entities.TeamMember;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.TaskActivity;
import com.example.taskmanagement_backend.enums.TeamRole;
import com.example.taskmanagement_backend.enums.SystemRole;
import com.example.taskmanagement_backend.enums.TaskStatus;
import com.example.taskmanagement_backend.enums.ProjectStatus;
import com.example.taskmanagement_backend.repositories.ProjectJpaRepository;
import com.example.taskmanagement_backend.repositories.TeamJpaRepository;
import com.example.taskmanagement_backend.repositories.TeamMemberJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.repositories.TaskJpaRepository;
import com.example.taskmanagement_backend.repositories.TaskActivityRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final TeamJpaRepository teamJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final ProjectJpaRepository projectJpaRepository;
    private final TeamMemberJpaRepository teamMemberJpaRepository;
    private final TaskJpaRepository taskJpaRepository;
    private final TaskActivityRepository taskActivityRepository;
    private final com.example.taskmanagement_backend.search.services.SearchEventPublisher searchEventPublisher; // ✅ NEW: Add SearchEventPublisher for Kafka indexing
    private final AutoNotificationService autoNotificationService; // ✅ NEW: Add AutoNotificationService
    private final com.example.taskmanagement_backend.repositories.TeamProgressRepository teamProgressRepository;


    public List<TeamResponseDto> getAllTeams() {
        try {
            // ✅ FIX: Chỉ trả về teams mà user hiện tại có quyền truy cập
            User currentUser = getCurrentUser();

            // Lấy tất cả teams mà user đã tạo hoặc là member
            List<Team> userTeams = teamJpaRepository.findTeamsByUserCreatedOrJoined(currentUser);

            return userTeams.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // Nếu user chưa authenticated, trả về empty list
            return List.of();
        }
    }

    public TeamResponseDto getTeamById(Long id) {
        return teamJpaRepository.findById(id).map(this::convertToDto).orElse(null);
    }
    public List<TeamResponseDto> findByProjectId(Long projectId) {
        // Now projects belong directly to teams, so we find teams that have this project
        // Find teams that have projects with this ID
        return teamJpaRepository.findAll().stream()
                .filter(team -> team.getProjects().stream().anyMatch(project -> project.getId().equals(projectId)))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public TeamResponseDto createTeams(CreateTeamResponseDto dto){
        // Get current user from JWT token
        User currentUser = getCurrentUser();

        Team team = Team.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .createdBy(currentUser)  // Always current user
                .isDefaultWorkspace(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Team savedTeam = teamJpaRepository.save(team);
    
        // Add the creator as a team owner
        TeamMember creatorMember = TeamMember.builder()
                .team(savedTeam)
                .user(currentUser)
                .role(TeamRole.OWNER) // Set role OWNER cho người tạo
                .joinedAt(LocalDateTime.now())
                .build();
        teamMemberJpaRepository.save(creatorMember);
        
        // ✅ NEW: Publish Kafka event for search indexing
        try {
            searchEventPublisher.publishTeamCreated(savedTeam.getId(), currentUser.getId());
            System.out.println("📤 Published TEAM_CREATED event to Kafka for team: " + savedTeam.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to publish TEAM_CREATED event for team " + savedTeam.getId() + ": " + e.getMessage());
            // Don't throw exception to avoid blocking team creation
        }

        // If project_id is provided, assign the project to this team
        if (dto.getProject_id() != null) {
            Project project = projectJpaRepository.findById(dto.getProject_id()).orElse(null);
            if (project != null) {
                project.setTeam(savedTeam);
                project.setIsPersonal(false);
                projectJpaRepository.save(project);
            }
        }
        
        return convertToDto(savedTeam);
    }

    public TeamResponseDto updateTeams(Long id, UpdateTeamResponseDto dto){
        Optional<Team> teamOpt = teamJpaRepository.findById(id);
        if (!teamOpt.isPresent()) {
            return null;
        }
        
        Team team = teamOpt.get();
        if(dto.getName() != null) team.setName(dto.getName());
        if(dto.getDescription() != null) team.setDescription(dto.getDescription());
        
        team.setUpdatedAt(LocalDateTime.now());
        
        Team savedTeam = teamJpaRepository.save(team);
        
        // Handle project relationship update if provided
        if(dto.getProjectId() != null) {
            Project project = projectJpaRepository.findById(dto.getProjectId()).orElse(null);
            if (project != null && project.getTeam() == null) {
                project.setTeam(savedTeam);
                project.setIsPersonal(false);
                projectJpaRepository.save(project);
            }
        }
        
        return convertToDto(savedTeam);
    }

    @org.springframework.transaction.annotation.Transactional
    public boolean deleteTeamById(Long id) {
        Optional<Team> teamOpt = teamJpaRepository.findById(id);
        if (!teamOpt.isPresent()) {
            return false;
        }

        Team team = teamOpt.get();
        System.out.println("🗑️ Starting comprehensive team deletion process for team ID: " + id);

        try {
            // ✅ STEP 1: Delete all team members
            List<TeamMember> teamMembers = teamMemberJpaRepository.findByTeamId(id);
            if (!teamMembers.isEmpty()) {
                System.out.println("🗑️ Found " + teamMembers.size() + " team members to delete");
                teamMemberJpaRepository.deleteAll(teamMembers);
                System.out.println("✅ Successfully deleted all team members");
            } else {
                System.out.println("ℹ️ No team members found for this team");
            }

            // ✅ STEP 2: Delete team progress records
            teamProgressRepository.deleteByTeamId(id);
            System.out.println("✅ Successfully deleted team progress records");

            // ✅ STEP 3: Update projects that belong to this team (set team to null)
            List<Project> teamProjects = projectJpaRepository.findAll().stream()
                    .filter(project -> project.getTeam() != null && project.getTeam().getId().equals(id))
                    .collect(Collectors.toList());

            if (!teamProjects.isEmpty()) {
                System.out.println("🔄 Found " + teamProjects.size() + " projects belonging to this team, updating them");
                for (Project project : teamProjects) {
                    project.setTeam(null);
                    project.setIsPersonal(true); // Convert back to personal project
                    projectJpaRepository.save(project);
                }
                System.out.println("✅ Successfully updated all team projects");
            } else {
                System.out.println("ℹ️ No projects found for this team");
            }

            // ✅ STEP 4: Publish Kafka event for search indexing before team deletion
            try {
                User currentUser = getCurrentUser();
                searchEventPublisher.publishTeamDeleted(team.getId(), currentUser.getId());
                System.out.println("📤 Published TEAM_DELETED event to Kafka for team: " + team.getId());
            } catch (Exception e) {
                System.err.println("⚠️ Failed to publish TEAM_DELETED event for team " + team.getId() + ": " + e.getMessage());
                // Don't throw exception since the team is about to be deleted anyway
            }

            // ✅ STEP 5: Finally delete the team itself
            teamJpaRepository.deleteById(id);
            System.out.println("✅ Successfully deleted team with ID: " + id);
            System.out.println("🎉 Team deletion completed successfully with full cleanup!");

            return true;
        } catch (Exception e) {
            System.err.println("❌ Error deleting team: " + e.getMessage());
            throw new RuntimeException("Failed to delete team: " + e.getMessage(), e);
        }
    }

    /**
     * Get all teams that a user either created or joined as a member
     * @param userId The ID of the user
     * @return List of teams the user is associated with
     * @throws RuntimeException if user not found
     */
    public List<TeamResponseDto> getTeamsByUserId(Long userId) {
        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        List<Team> teams = teamJpaRepository.findTeamsByUserCreatedOrJoined(user);
        return teams.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get teams created by a specific user
     * @param userId The ID of the user who created the teams
     * @return List of teams created by the user
     * @throws RuntimeException if user not found
     */
    public List<TeamResponseDto> getTeamsCreatedByUser(Long userId) {
        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        List<Team> teams = teamJpaRepository.findByCreatedBy(user);
        return teams.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get team members with their roles
     * @param teamId The ID of the team
     * @return List of team members with roles
     * @throws RuntimeException if team not found
     */
    public List<TeamMemberResponseDto> getTeamMembersWithRoles(Long teamId) {
        // Kiểm tra team tồn tại
        Team team = teamJpaRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

        // Lấy danh sách members của team
        List<TeamMember> members = teamMemberJpaRepository.findByTeamId(teamId);

        // Convert sang DTO với đầy đủ thông tin role
        return members.stream()
                .map(this::convertMemberToDto)
                .collect(Collectors.toList());
    }

    private TeamResponseDto convertToDto(Team team) {
        // Get the first project this team is associated with (for backward compatibility)
        // Get first project in this team (if any)
        // Get projects that belong to this team
        List<Project> teamProjects = projectJpaRepository.findAll().stream()
                .filter(project -> project.getTeam() != null && project.getTeam().getId().equals(team.getId()))
                .collect(Collectors.toList());
        Long projectId = teamProjects.isEmpty() ? null : teamProjects.get(0).getId();

        // ✅ FIX: Get current user's role in this team
        String currentUserRole = null;
        boolean isCurrentUserMember = false;

        try {
            User currentUser = getCurrentUser();
            Optional<TeamMember> currentUserMembership = teamMemberJpaRepository
                    .findByTeamAndUser(team, currentUser);

            if (currentUserMembership.isPresent()) {
                currentUserRole = currentUserMembership.get().getRole().name();
                isCurrentUserMember = true;
            }
        } catch (Exception e) {
            // User not authenticated or not found - skip role info
            System.out.println("Could not get current user role for team: " + e.getMessage());
        }

        return TeamResponseDto.builder()
                .id(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .createdById(team.getCreatedBy() != null ? team.getCreatedBy().getId() : null)
                .isDefaultWorkspace(team.isDefaultWorkspace())
                .organizationId(team.getOrganization() != null ? team.getOrganization().getId() : null)
                .currentUserRole(currentUserRole)
                .isCurrentUserMember(isCurrentUserMember)
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt())
                .build();
    }

    private Team convertToEntity(TeamResponseDto dto) {
        return Team.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    private User getUser(Long id) {
        return id != null ? userJpaRepository.findById(id).orElse(null) : null;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userJpaRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    private TeamMemberResponseDto convertMemberToDto(TeamMember member) {
        User user = member.getUser();

        return TeamMemberResponseDto.builder()
                .id(member.getId())
                .teamId(member.getTeam().getId())
                .userId(user.getId())
                .email(user.getEmail())
                .role(member.getRole())  // Thông tin role từ TeamMember
                .joinedAt(member.getJoinedAt())
                .aboutMe(user.getUserProfile() != null ? user.getUserProfile().getAboutMe() : null)
                .department(user.getUserProfile() != null ? user.getUserProfile().getDepartment() : null)
                .jobTitle(user.getUserProfile() != null ? user.getUserProfile().getJobTitle() : null)
                .avatarUrl(user.getAvatarUrl())
                .firstName(user.getUserProfile() != null ? user.getUserProfile().getFirstName() : null)
                .lastName(user.getUserProfile() != null ? user.getUserProfile().getLastName() : null)
                .build();
    }

    /**
     * Kiểm tra xem user hiện tại có quyền truy cập team hay không
     * @param teamId ID của team cần kiểm tra
     * @return true nếu user có quyền truy cập, false nếu không
     */
    public boolean hasAccessToTeam(Long teamId) {
        try {
            User currentUser = getCurrentUser();
            Team team = teamJpaRepository.findById(teamId)
                    .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

            // 1. ADMIN có full quyền với tất cả teams
            if (isAdmin(currentUser)) {
                return true;
            }

            // 2. Team creator/owner có quyền truy cập
            if (team.getCreatedBy() != null && team.getCreatedBy().getId().equals(currentUser.getId())) {
                return true;
            }

            // 3. Users with OWNER or LEADER role in same organization
            if (isOwnerOrLeader(currentUser)) {
                if (currentUser.getOrganization() != null && team.getOrganization() != null &&
                    currentUser.getOrganization().getId().equals(team.getOrganization().getId())) {
                    return true;
                }
            }

            // 4. Kiểm tra xem user có phải là member của team không
            Optional<TeamMember> membership = teamMemberJpaRepository.findByTeamAndUser(team, currentUser);
            if (membership.isPresent()) {
                return true;
            }

            // 5. Access denied - return false instead of temporarily allowing access
            System.err.println("❌ [TeamService] Access denied for user " + currentUser.getEmail() + " to team " + teamId);
            return false; // ✅ FIXED: Properly deny access instead of temporarily allowing it

        } catch (Exception e) {
            System.err.println("❌ [TeamService] Error checking team access: " + e.getMessage());
            return false; // ✅ FIXED: Properly deny access on error instead of temporarily allowing it
        }
    }

    /**
     * ✅ UPDATED: Check if user is admin using systemRole
     */
    private boolean isAdmin(User user) {
        return user.getSystemRole() == SystemRole.ADMIN;
    }

    /**
     * ✅ UPDATED: Check if user is owner or leader in team/project context
     * Note: OWNER and LEADER are team/project level roles, not system roles
     * This method checks if user has OWNER or LEADER role in any team they belong to
     */
    private boolean isOwnerOrLeader(User user) {
        try {
            // Check if user has OWNER or LEADER role in any team
            List<TeamMember> userMemberships = teamMemberJpaRepository.findByUser_Id(user.getId());

            return userMemberships.stream()
                    .anyMatch(membership ->
                        membership.getRole() == TeamRole.OWNER ||
                        membership.getRole() == TeamRole.LEADER);
        } catch (Exception e) {
            System.err.println("Error checking user team roles: " + e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra quyền truy cập team và throw exception nếu không có quyền
     * @param teamId ID của team cần kiểm tra
     * @throws RuntimeException nếu user không có quyền truy cập
     */
    public void validateTeamAccess(Long teamId) {
        if (!hasAccessToTeam(teamId)) {
            throw new RuntimeException("Access denied: You are not a member of this team");
        }
    }

    /**
     * Lấy role của user hiện tại trong team
     * @param teamId ID của team
     * @return TeamRole của user, null nếu không phải member
     */
    public TeamRole getCurrentUserRoleInTeam(Long teamId) {
        try {
            User currentUser = getCurrentUser();
            Team team = teamJpaRepository.findById(teamId)
                    .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

            Optional<TeamMember> membership = teamMemberJpaRepository.findByTeamAndUser(team, currentUser);
            return membership.map(TeamMember::getRole).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * 🔒 AUTHORIZATION: Get team IDs where user is creator
     * Used for search authorization at database layer
     */
    public List<Long> getTeamIdsByCreatorId(Long userId) {
        try {
            User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            List<Team> teams = teamJpaRepository.findByCreatedBy(user);
            List<Long> teamIds = teams.stream()
                .map(Team::getId)
                .collect(Collectors.toList());

            System.out.println("Found " + teamIds.size() + " teams created by user " + userId);
            return teamIds;
        } catch (Exception e) {
            System.err.println("Failed to get team IDs by creator " + userId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 🔒 AUTHORIZATION: Get team IDs where user is member
     * Used for search authorization at database layer
     */
    public List<Long> getTeamIdsByMemberId(Long userId) {
        try {
            List<TeamMember> memberships = teamMemberJpaRepository.findByUser_Id(userId);
            List<Long> teamIds = memberships.stream()
                .map(member -> member.getTeam().getId())
                .collect(Collectors.toList());

            System.out.println("Found " + teamIds.size() + " teams where user " + userId + " is member");
            return teamIds;
        } catch (Exception e) {
            System.err.println("Failed to get team IDs by member " + userId + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Add a user to a team by email address
     * @param teamId ID of the team to add the user to
     * @param email Email address of the user to add
     * @param role Role to assign to the user
     * @return TeamMemberResponseDto with details of the added member
     * @throws RuntimeException if team not found, user not found, or current user doesn't have permission
     */
    public TeamMemberResponseDto addTeamMemberByEmail(Long teamId, String email, TeamRole role) {
        // Validate team exists
        Team team = teamJpaRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

        // Get current user and verify they have permission to add members
        User currentUser = getCurrentUser();
        Optional<TeamMember> currentUserMembership = teamMemberJpaRepository.findByTeamAndUser(team, currentUser);

        // Check if current user has permission to add members
        if (!isAdmin(currentUser) &&
            !(currentUserMembership.isPresent() &&
              (currentUserMembership.get().getRole() == TeamRole.OWNER ||
               currentUserMembership.get().getRole() == TeamRole.MEMBER))) { // Changed LEADER to MEMBER since TeamRole doesn't have LEADER
            throw new RuntimeException("You don't have permission to add members to this team");
        }

        // Find user by email
        User userToAdd = userJpaRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // Check if user is already a member of the team
        Optional<TeamMember> existingMembership = teamMemberJpaRepository.findByTeamAndUser(team, userToAdd);
        if (existingMembership.isPresent()) {
            throw new RuntimeException("User is already a member of this team");
        }

        // Create new team membership
        TeamMember newMember = TeamMember.builder()
                .team(team)
                .user(userToAdd)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build();

        TeamMember savedMember = teamMemberJpaRepository.save(newMember);

        // Send notification to the added user
        try {
            autoNotificationService.sendTeamMemberAddedNotification(team, userToAdd, currentUser);
        } catch (Exception e) {
            System.err.println("Failed to create notification for new team member: " + e.getMessage());
            // Continue execution even if notification fails
        }

        return convertMemberToDto(savedMember);
    }

    /**
     * Xóa thành viên khỏi team
     * @param teamId ID của team
     * @param memberId ID của thành viên cần xóa
     * @return true nếu xóa thành công, false nếu không tìm thấy thành viên
     * @throws RuntimeException nếu người dùng hiện tại không có quyền xóa thành viên
     */
    public boolean removeTeamMember(Long teamId, Long memberId) {
        // Kiểm tra team tồn tại
        Team team = teamJpaRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

        // Kiểm tra thành viên tồn tại
        TeamMember memberToRemove = teamMemberJpaRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Team member not found with id: " + memberId));

        // Kiểm tra thành viên thuộc team được chỉ định
        if (!memberToRemove.getTeam().getId().equals(teamId)) {
            throw new RuntimeException("Member does not belong to the specified team");
        }

        // Lấy thông tin người dùng hiện tại
        User currentUser = getCurrentUser();
        Optional<TeamMember> currentUserMembership = teamMemberJpaRepository.findByTeamAndUser(team, currentUser);

        // Kiểm tra quy���n xóa thành viên
        // Chỉ ADMIN, OWNER của team hoặc chính người dùng đó mới có thể xóa thành viên
        if (!isAdmin(currentUser) &&
            !(currentUserMembership.isPresent() && currentUserMembership.get().getRole() == TeamRole.OWNER) &&
            !currentUser.getId().equals(memberToRemove.getUser().getId())) {
            throw new RuntimeException("You don't have permission to remove members from this team");
        }

        // Không cho phép xóa OWNER duy nhất của team
        if (memberToRemove.getRole() == TeamRole.OWNER) {
            long ownerCount = teamMemberJpaRepository.findByTeamId(teamId).stream()
                    .filter(member -> member.getRole() == TeamRole.OWNER)
                    .count();

            if (ownerCount <= 1) {
                throw new RuntimeException("Cannot remove the only owner of the team");
            }
        }

        // Thực hiện xóa thành viên
        try {
            teamMemberJpaRepository.delete(memberToRemove);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove team member: " + e.getMessage());
        }
    }

    // ✅ NEW: Team Timeline/Activity methods

    /**
     * Get team timeline activities (all activities from team tasks and project tasks)
     * @param teamId ID of the team
     * @param page Page number for pagination
     * @param size Number of items per page
     * @return List of task activities for the team
     */
    public List<TaskActivityResponseDto> getTeamTimeline(Long teamId, int page, int size) {
        try {
            Team team = teamJpaRepository.findById(teamId)
                    .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

            // Get all tasks that belong to this team (both direct team tasks and project tasks)
            List<Task> teamTasks = getTeamTasks(teamId);

            if (teamTasks.isEmpty()) {
                return new ArrayList<>();
            }

            // Get task IDs
            List<Long> taskIds = teamTasks.stream()
                    .map(Task::getId)
                    .collect(Collectors.toList());

            // Get activities for all team tasks with pagination
            Pageable pageable = PageRequest.of(page, size);
            List<TaskActivity> activities = taskActivityRepository.findAll().stream()
                    .filter(activity -> taskIds.contains(activity.getTask().getId()))
                    .sorted((a1, a2) -> a2.getCreatedAt().compareTo(a1.getCreatedAt()))
                    .skip((long) page * size)
                    .limit(size)
                    .collect(Collectors.toList());

            return activities.stream()
                    .map(this::convertActivityToDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error getting team timeline: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get recent activities of team (20 most recent activities)
     * @param teamId ID of the team
     * @return List of recent task activities for the team
     */
    public List<TaskActivityResponseDto> getTeamRecentActivities(Long teamId) {
        return getTeamTimeline(teamId, 0, 20);
    }

    /**
     * Get team activities by type
     * @param teamId ID of the team
     * @param type Type of activity (e.g., "TASK_CREATED", "STATUS_CHANGED")
     * @param page Page number for pagination
     * @param size Number of items per page
     * @return List of task activities filtered by type
     */
    public List<TaskActivityResponseDto> getTeamActivitiesByType(Long teamId, String type, int page, int size) {
        try {
            Team team = teamJpaRepository.findById(teamId)
                    .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

            // Get all tasks that belong to this team
            List<Task> teamTasks = getTeamTasks(teamId);

            if (teamTasks.isEmpty()) {
                return new ArrayList<>();
            }

            // Get task IDs
            List<Long> taskIds = teamTasks.stream()
                    .map(Task::getId)
                    .collect(Collectors.toList());

            // Get activities filtered by type with pagination
            List<TaskActivity> activities = taskActivityRepository.findAll().stream()
                    .filter(activity -> taskIds.contains(activity.getTask().getId()))
                    .filter(activity -> activity.getActivityType().name().equals(type))
                    .sorted((a1, a2) -> a2.getCreatedAt().compareTo(a1.getCreatedAt()))
                    .skip((long) page * size)
                    .limit(size)
                    .collect(Collectors.toList());

            return activities.stream()
                    .map(this::convertActivityToDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error getting team activities by type: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get team activities by date range
     * @param teamId ID of the team
     * @param startDate Start date (format: "2025-09-01")
     * @param endDate End date (format: "2025-09-15")
     * @param page Page number for pagination
     * @param size Number of items per page
     * @return List of task activities within the date range
     */
    public List<TaskActivityResponseDto> getTeamActivitiesByDateRange(Long teamId, String startDate, String endDate, int page, int size) {
        try {
            Team team = teamJpaRepository.findById(teamId)
                    .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

            // Parse dates
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate start = LocalDate.parse(startDate, formatter);
            LocalDate end = LocalDate.parse(endDate, formatter);

            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.plusDays(1).atStartOfDay(); // Include the end date

            // Get all tasks that belong to this team
            List<Task> teamTasks = getTeamTasks(teamId);

            if (teamTasks.isEmpty()) {
                return new ArrayList<>();
            }

            // Get task IDs
            List<Long> taskIds = teamTasks.stream()
                    .map(Task::getId)
                    .collect(Collectors.toList());

            // Get activities filtered by date range with pagination
            List<TaskActivity> activities = taskActivityRepository.findAll().stream()
                    .filter(activity -> taskIds.contains(activity.getTask().getId()))
                    .filter(activity -> activity.getCreatedAt().isAfter(startDateTime) &&
                                      activity.getCreatedAt().isBefore(endDateTime))
                    .sorted((a1, a2) -> a2.getCreatedAt().compareTo(a1.getCreatedAt()))
                    .skip((long) page * size)
                    .limit(size)
                    .collect(Collectors.toList());

            return activities.stream()
                    .map(this::convertActivityToDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error getting team activities by date range: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Helper method to get all tasks that belong to a team
     * This includes both direct team tasks and tasks from projects owned by the team
     */
    private List<Task> getTeamTasks(Long teamId) {
        List<Task> allTasks = new ArrayList<>();

        try {
            // Get direct team tasks
            List<Task> directTeamTasks = taskJpaRepository.findAll().stream()
                    .filter(task -> task.getTeam() != null && task.getTeam().getId().equals(teamId))
                    .collect(Collectors.toList());
            allTasks.addAll(directTeamTasks);

            // Get tasks from projects owned by this team
            List<Project> teamProjects = projectJpaRepository.findAll().stream()
                    .filter(project -> project.getTeam() != null && project.getTeam().getId().equals(teamId))
                    .collect(Collectors.toList());

            for (Project project : teamProjects) {
                List<Task> projectTasks = taskJpaRepository.findAll().stream()
                        .filter(task -> task.getProject() != null && task.getProject().getId().equals(project.getId()))
                        .collect(Collectors.toList());
                allTasks.addAll(projectTasks);
            }

        } catch (Exception e) {
            System.err.println("Error getting team tasks: " + e.getMessage());
        }

        return allTasks;
    }

    /**
     * Convert TaskActivity entity to DTO
     */
    private TaskActivityResponseDto convertActivityToDto(TaskActivity activity) {
        User user = activity.getUser();
        Task task = activity.getTask();

        // Create UserProfileDto for the user
        UserProfileDto userProfileDto = UserProfileDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getUserProfile() != null ? user.getUserProfile().getFirstName() : null)
                .lastName(user.getUserProfile() != null ? user.getUserProfile().getLastName() : null)
                .avatarUrl(user.getAvatarUrl())
                .aboutMe(user.getUserProfile() != null ? user.getUserProfile().getAboutMe() : null)
                .department(user.getUserProfile() != null ? user.getUserProfile().getDepartment() : null)
                .jobTitle(user.getUserProfile() != null ? user.getUserProfile().getJobTitle() : null)
                .build();

        return TaskActivityResponseDto.builder()
                .id(activity.getId())
                .taskId(task.getId())
                .activityType(activity.getActivityType())
                .description(activity.getDescription())
                .oldValue(activity.getOldValue())
                .newValue(activity.getNewValue())
                .fieldName(activity.getFieldName())
                .createdAt(activity.getCreatedAt())
                .user(userProfileDto)
                .formattedMessage(activity.getDescription())
                .timeAgo(calculateTimeAgo(activity.getCreatedAt()))
                .build();
    }

    /**
     * Helper method to calculate time ago string
     */
    private String calculateTimeAgo(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        long minutes = java.time.Duration.between(dateTime, now).toMinutes();

        if (minutes < 1) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        } else if (minutes < 1440) // 24 hours
        {
            long hours = minutes / 60;
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        } else {
            long days = minutes / 1440;
            return days + " day" + (days == 1 ? "" : "s") + " ago";
        }
    }

    /**
     * Get comprehensive team dashboard data
     * @param teamId ID of the team
     * @return TeamDashboardResponseDto with complete team analytics
     */
    public com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto getTeamDashboard(Long teamId) {
        Team team = teamJpaRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

        // Get team members
        List<TeamMember> teamMembers = teamMemberJpaRepository.findByTeamId(teamId);

        // Get team projects - convert Set to List
        List<Project> teamProjects = new ArrayList<>(team.getProjects());

        // Get all tasks from team and project tasks
        List<Task> allTeamTasks = new ArrayList<>(taskJpaRepository.findByTeamId(teamId));

        // Add project tasks from team projects
        for (Project project : teamProjects) {
            allTeamTasks.addAll(taskJpaRepository.findByProjectId(project.getId()));
        }

        // Build team stats
        com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.TeamStats teamStats =
            buildTeamStats(teamMembers, teamProjects, allTeamTasks);

        // Build member breakdown
        com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.MemberBreakdown memberBreakdown =
            buildMemberBreakdown(teamMembers, allTeamTasks);

        // Build project breakdown
        com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.ProjectBreakdown projectBreakdown =
            buildProjectBreakdown(teamProjects);

        // Build upcoming deadlines
        com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.UpcomingDeadlines upcomingDeadlines =
            buildUpcomingDeadlines(allTeamTasks);

        // Build team performance
        com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.TeamPerformance teamPerformance =
            buildTeamPerformance(allTeamTasks);

        return com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.builder()
                .teamStats(teamStats)
                .memberBreakdown(memberBreakdown)
                .projectBreakdown(projectBreakdown)
                .upcomingDeadlines(upcomingDeadlines)
                .teamPerformance(teamPerformance)
                .build();
    }

    private com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.TeamStats buildTeamStats(
            List<TeamMember> teamMembers, List<Project> teamProjects, List<Task> allTeamTasks) {

        int totalMembers = teamMembers.size();
        int activeMembers = (int) teamMembers.stream()
                .filter(member -> member.getUser().getIsActive() != null && member.getUser().getIsActive())
                .count();

        int totalProjects = teamProjects.size();
        int activeProjects = (int) teamProjects.stream()
                .filter(project -> project.getStatus() == ProjectStatus.PLANNED ||
                                 project.getStatus() == ProjectStatus.IN_PROGRESS)
                .count();
        int completedProjects = (int) teamProjects.stream()
                .filter(project -> project.getStatus() == ProjectStatus.COMPLETED)
                .count();

        int totalTasks = allTeamTasks.size();
        int completedTasks = (int) allTeamTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.DONE)
                .count();
        int pendingTasks = (int) allTeamTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.TODO ||
                              task.getStatus() == TaskStatus.IN_PROGRESS)
                .count();
        int overdueTasks = (int) allTeamTasks.stream()
                .filter(task -> task.getDeadline() != null && task.getDeadline().isBefore(LocalDate.now())
                        && task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.DONE)
                .count();

        double completionRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0.0;
        double avgTasksPerMember = activeMembers > 0 ? (double) totalTasks / activeMembers : 0.0;
        double teamEfficiency = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0.0;

        return com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.TeamStats.builder()
                .totalMembers(totalMembers)
                .activeMembers(activeMembers)
                .totalProjects(totalProjects)
                .activeProjects(activeProjects)
                .completedProjects(completedProjects)
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .pendingTasks(pendingTasks)
                .overdueTasks(overdueTasks)
                .teamEfficiency(Math.round(teamEfficiency * 10.0) / 10.0)
                .avgTasksPerMember(Math.round(avgTasksPerMember * 10.0) / 10.0)
                .completionRate(Math.round(completionRate * 10.0) / 10.0)
                .build();
    }

    private com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.MemberBreakdown buildMemberBreakdown(
            List<TeamMember> teamMembers, List<Task> allTeamTasks) {

        // Role breakdown
        java.util.Map<TeamRole, Long> roleCount = teamMembers.stream()
                .collect(java.util.stream.Collectors.groupingBy(TeamMember::getRole, java.util.stream.Collectors.counting()));

        List<com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.RoleBreakdown> byRole =
                roleCount.entrySet().stream()
                        .map(entry -> com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.RoleBreakdown.builder()
                                .name(entry.getKey().toString())
                                .count(entry.getValue().intValue())
                                .build())
                        .collect(java.util.stream.Collectors.toList());

        // Workload breakdown (top 5 members by task count) - using assignees relationship
        java.util.Map<Long, Long> userTaskCount = allTeamTasks.stream()
                .flatMap(task -> task.getAssignees().stream())
                .collect(java.util.stream.Collectors.groupingBy(
                        assignee -> assignee.getUser().getId(),
                        java.util.stream.Collectors.counting()));

        List<com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.WorkloadBreakdown> byWorkload =
                teamMembers.stream()
                        .map(member -> {
                            Long taskCount = userTaskCount.getOrDefault(member.getUser().getId(), 0L);
                            String userName = member.getUser().getUserProfile() != null &&
                                            member.getUser().getUserProfile().getFirstName() != null ?
                                            member.getUser().getUserProfile().getFirstName() + " " +
                                            (member.getUser().getUserProfile().getLastName() != null ?
                                             member.getUser().getUserProfile().getLastName() : "") :
                                            member.getUser().getUsername();
                            return com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.WorkloadBreakdown.builder()
                                    .name(userName.trim())
                                    .count(taskCount.intValue())
                                    .build();
                        })
                        .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
                        .limit(5)
                        .collect(java.util.stream.Collectors.toList());

        return com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.MemberBreakdown.builder()
                .byRole(byRole)
                .byWorkload(byWorkload)
                .build();
    }

    private com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.ProjectBreakdown buildProjectBreakdown(
            List<Project> teamProjects) {

        // Status breakdown
        java.util.Map<String, Long> statusCount = teamProjects.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        project -> project.getStatus() != null ? project.getStatus().toString() : "UNKNOWN",
                        java.util.stream.Collectors.counting()));

        List<com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.StatusBreakdown> byStatus =
                statusCount.entrySet().stream()
                        .map(entry -> com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.StatusBreakdown.builder()
                                .name(entry.getKey())
                                .count(entry.getValue().intValue())
                                .build())
                        .collect(java.util.stream.Collectors.toList());

        // Progress breakdown (top 5 projects by task completion rate)
        List<com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.ProgressBreakdown> byProgress =
                teamProjects.stream()
                        .limit(5)
                        .map(project -> {
                            // Calculate progress based on completed tasks in project
                            List<Task> projectTasks = taskJpaRepository.findByProjectId(project.getId());
                            double progress = 0.0;
                            if (!projectTasks.isEmpty()) {
                                long completedCount = projectTasks.stream()
                                        .filter(task -> task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.DONE)
                                        .count();
                                progress = (double) completedCount / projectTasks.size() * 100;
                            }

                            return com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.ProgressBreakdown.builder()
                                    .name(project.getName())
                                    .progress(Math.round(progress * 10.0) / 10.0)
                                    .build();
                        })
                        .collect(java.util.stream.Collectors.toList());

        return com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.ProjectBreakdown.builder()
                .byStatus(byStatus)
                .byProgress(byProgress)
                .build();
    }

    private com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.UpcomingDeadlines buildUpcomingDeadlines(
            List<Task> allTeamTasks) {

        LocalDate today = LocalDate.now();
        LocalDate endOfWeek = today.plusDays(7);
        LocalDate endOfNextWeek = today.plusDays(14);

        // This week tasks
        List<com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.DeadlineTask> thisWeek =
                allTeamTasks.stream()
                        .filter(task -> task.getDeadline() != null &&
                                task.getDeadline().isAfter(today) &&
                                task.getDeadline().isBefore(endOfWeek) &&
                                task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.DONE)
                        .map(task -> {
                            String assigneeName = getMainAssigneeName(task);
                            return com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.DeadlineTask.builder()
                                    .id(task.getId())
                                    .title(task.getTitle())
                                    .project(task.getProject() != null ? task.getProject().getName() : "Team Task")
                                    .dueDate(task.getDeadline().toString())
                                    .assignee(assigneeName)
                                    .build();
                        })
                        .collect(java.util.stream.Collectors.toList());

        // Next week tasks
        List<com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.DeadlineTask> nextWeek =
                allTeamTasks.stream()
                        .filter(task -> task.getDeadline() != null &&
                                task.getDeadline().isAfter(endOfWeek) &&
                                task.getDeadline().isBefore(endOfNextWeek) &&
                                task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.DONE)
                        .map(task -> {
                            String assigneeName = getMainAssigneeName(task);
                            return com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.DeadlineTask.builder()
                                    .id(task.getId())
                                    .title(task.getTitle())
                                    .project(task.getProject() != null ? task.getProject().getName() : "Team Task")
                                    .dueDate(task.getDeadline().toString())
                                    .assignee(assigneeName)
                                    .build();
                        })
                        .collect(java.util.stream.Collectors.toList());

        // Overdue tasks
        List<com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.OverdueTask> overdue =
                allTeamTasks.stream()
                        .filter(task -> task.getDeadline() != null &&
                                task.getDeadline().isBefore(today) &&
                                task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.DONE)
                        .map(task -> {
                            long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(task.getDeadline(), today);
                            String assigneeName = getMainAssigneeName(task);
                            return com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.OverdueTask.builder()
                                    .id(task.getId())
                                    .title(task.getTitle())
                                    .project(task.getProject() != null ? task.getProject().getName() : "Team Task")
                                    .dueDate(task.getDeadline().toString())
                                    .daysOverdue((int) daysOverdue)
                                    .assignee(assigneeName)
                                    .build();
                        })
                        .collect(java.util.stream.Collectors.toList());

        return com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.UpcomingDeadlines.builder()
                .thisWeek(thisWeek)
                .nextWeek(nextWeek)
                .overdue(overdue)
                .build();
    }

    private com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.TeamPerformance buildTeamPerformance(
            List<Task> allTeamTasks) {

        // Get last 5 months performance data
        List<com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.MonthlyTrend> monthlyTrends =
                new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        for (int i = 4; i >= 0; i--) {
            LocalDateTime monthStart = now.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1).minusDays(1).withHour(23).withMinute(59).withSecond(59);

            int tasksCreated = (int) allTeamTasks.stream()
                    .filter(task -> task.getCreatedAt() != null &&
                            task.getCreatedAt().isAfter(monthStart) &&
                            task.getCreatedAt().isBefore(monthEnd))
                    .count();

            int tasksCompleted = (int) allTeamTasks.stream()
                    .filter(task -> task.getUpdatedAt() != null &&
                            task.getUpdatedAt().isAfter(monthStart) &&
                            task.getUpdatedAt().isBefore(monthEnd) &&
                            (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.DONE))
                    .count();

            monthlyTrends.add(com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.MonthlyTrend.builder()
                    .month(monthStart.format(DateTimeFormatter.ofPattern("MMM")))
                    .tasksCreated(tasksCreated)
                    .tasksCompleted(tasksCompleted)
                    .build());
        }

        return com.example.taskmanagement_backend.dtos.DashboardDto.TeamDashboardResponseDto.TeamPerformance.builder()
                .monthlyTrends(monthlyTrends)
                .build();
    }

    private String getMainAssigneeName(Task task) {
        if (task.getAssignees() == null || task.getAssignees().isEmpty()) {
            return "Unassigned";
        }

        // Get the first assignee
        com.example.taskmanagement_backend.entities.TaskAssignee firstAssignee = task.getAssignees().iterator().next();
        User user = firstAssignee.getUser();

        if (user.getUserProfile() != null && user.getUserProfile().getFirstName() != null) {
            String fullName = user.getUserProfile().getFirstName();
            if (user.getUserProfile().getLastName() != null) {
                fullName += " " + user.getUserProfile().getLastName();
            }
            return fullName.trim();
        }

        return user.getUsername();
    }
}
