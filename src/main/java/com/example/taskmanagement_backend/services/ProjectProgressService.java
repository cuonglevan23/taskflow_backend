package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ProcessDto.ProjectProgressResponseDto;
import com.example.taskmanagement_backend.dtos.ProcessDto.TeamProjectProgressResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.ProjectProgress;
import com.example.taskmanagement_backend.entities.ProjectMember;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;
import com.example.taskmanagement_backend.repositories.ProjectProgressRepository;
import com.example.taskmanagement_backend.repositories.ProjectJpaRepository;
import com.example.taskmanagement_backend.repositories.ProjectMemberJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectProgressService {

    private final ProjectProgressRepository projectProgressRepository;
    private final ProjectJpaRepository projectRepository;
    private final TeamProjectProgressService teamProjectProgressService;
    private final TeamProgressService teamProgressService;
    private final UserJpaRepository userRepository;
    private final ProjectMemberJpaRepository projectMemberRepository;
    private final UserProfileMapper userProfileMapper; // ✅ NEW: Thêm dependency để convert user profiles

    @Transactional(readOnly = true)
    public ProjectProgressResponseDto getProjectProgress(Long projectId) {
        // Verify project exists
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));

        // Check if progress exists first
        Optional<ProjectProgress> existingProgress = projectProgressRepository.findByProjectId(projectId);

        ProjectProgress progress;
        if (existingProgress.isPresent()) {
            // Return existing progress without updating in read-only mode
            progress = existingProgress.get();
            System.out.println("✅ [ProjectProgressService] Found existing progress for project " + projectId);
        } else {
            // For new projects without progress, return default values without saving to DB
            System.out.println("🆕 [ProjectProgressService] No existing progress found for project " + projectId + " - returning default values");
            progress = createDefaultProgress(project);
        }

        // Get team-project progress details
        List<TeamProjectProgressResponseDto> teamProjectProgressList = teamProjectProgressService.getTeamProjectProgressByProjectId(projectId);

        return convertToDto(progress, teamProjectProgressList);
    }

    // Create a default progress object without persisting it to the database
    private ProjectProgress createDefaultProgress(Project project) {
        return ProjectProgress.builder()
                .project(project)
                .totalTasks(0)
                .completedTasks(0)
                .totalTeams(0)
                .completionPercentage(0.0)
                .lastUpdated(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // Create a new method with REQUIRES_NEW to ensure it runs in a new write transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProjectProgressResponseDto getOrCreateProjectProgressWithWrite(Long projectId) {
        // Get or create project progress in a write transaction
        ProjectProgress progress = getOrCreateProjectProgress(projectId);
        
        // Get team-project progress details
        List<TeamProjectProgressResponseDto> teamProjectProgressList = teamProjectProgressService.getTeamProjectProgressByProjectId(projectId);
        
        return convertToDto(progress, teamProjectProgressList);
    }

    @Transactional
    public ProjectProgress getOrCreateProjectProgress(Long projectId) {
        System.out.println("🔄 [ProjectProgressService] Starting getOrCreateProjectProgress for projectId: " + projectId);

        Optional<ProjectProgress> existingProgress = projectProgressRepository.findByProjectId(projectId);
        
        if (existingProgress.isPresent()) {
            // Update existing progress
            ProjectProgress progress = existingProgress.get();
            System.out.println("📊 [ProjectProgressService] Found existing progress for project " + projectId +
                             " - Current: " + progress.getCompletedTasks() + "/" + progress.getTotalTasks());

            updateProjectProgressData(progress, projectId);
            ProjectProgress savedProgress = projectProgressRepository.save(progress);

            System.out.println("✅ [ProjectProgressService] Updated progress for project " + projectId +
                             " - New: " + savedProgress.getCompletedTasks() + "/" + savedProgress.getTotalTasks() +
                             " (" + savedProgress.getCompletionPercentage() + "%)");
            return savedProgress;
        } else {
            // Create new progress
            System.out.println("🆕 [ProjectProgressService] Creating new progress for project " + projectId);

            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
            
            ProjectProgress newProgress = ProjectProgress.builder()
                    .project(project)
                    .build();
            
            updateProjectProgressData(newProgress, projectId);
            ProjectProgress savedProgress = projectProgressRepository.save(newProgress);

            System.out.println("✅ [ProjectProgressService] Created new progress for project " + projectId +
                             " - " + savedProgress.getCompletedTasks() + "/" + savedProgress.getTotalTasks() +
                             " (" + savedProgress.getCompletionPercentage() + "%)");
            return savedProgress;
        }
    }

    private void updateProjectProgressData(ProjectProgress progress, Long projectId) {
        // Calculate totals from all tasks in the project (across all teams)
        try {
            Long totalTasks = projectProgressRepository.countTotalTasksByProject(projectId);
            Long completedTasks = projectProgressRepository.countCompletedTasksByProject(projectId);
            Long totalTeams = projectProgressRepository.countTeamsByProject(projectId);

            // 🔧 FIX: Handle null values properly when there are no tasks or teams
            int totalTasksInt = (totalTasks != null) ? totalTasks.intValue() : 0;
            int completedTasksInt = (completedTasks != null) ? completedTasks.intValue() : 0;
            int totalTeamsInt = (totalTeams != null) ? totalTeams.intValue() : 0;

            progress.setTotalTasks(totalTasksInt);
            progress.setCompletedTasks(completedTasksInt);
            progress.setTotalTeams(totalTeamsInt);
            progress.calculateCompletionPercentage();
            progress.setLastUpdated(LocalDateTime.now());

            System.out.println("💾 [ProjectProgressService] Updated project " + projectId + " progress: " +
                             completedTasksInt + "/" + totalTasksInt + " tasks (" +
                             progress.getCompletionPercentage() + "%)");
        } catch (Exception e) {
            System.err.println("❌ Error calculating project progress for project " + projectId + ": " + e.getMessage());
            // Set default values in case of error
            progress.setTotalTasks(0);
            progress.setCompletedTasks(0);
            progress.setTotalTeams(0);
            progress.setCompletionPercentage(0.0);
            progress.setLastUpdated(LocalDateTime.now());
        }
    }

    private ProjectProgressResponseDto convertToDto(ProjectProgress progress, List<TeamProjectProgressResponseDto> teamProjectProgressList) {
        Project project = progress.getProject();
        Long projectId = project.getId();

        // ✅ NEW: Lấy thông tin user profiles cho project

        // 1. Lấy project creator (user tạo project)
        UserProfileDto projectCreator = null;
        if (project.getCreatedBy() != null) {
            projectCreator = userProfileMapper.toUserProfileDto(project.getCreatedBy());
        }

        // 2. Lấy tất cả project members với user profiles
        List<ProjectMember> projectMembers = projectMemberRepository.findByProjectId(projectId);
        List<UserProfileDto> memberProfiles = projectMembers.stream()
                .map(member -> userProfileMapper.toUserProfileDto(member.getUser()))
                .collect(Collectors.toList());

        // 3. Lấy user cuối cùng update progress
        UserProfileDto lastUpdatedBy = null;
        try {
            User currentUser = getCurrentUser();
            lastUpdatedBy = userProfileMapper.toUserProfileDto(currentUser);
        } catch (Exception e) {
            // Nếu không lấy được current user, để null
        }

        return ProjectProgressResponseDto.builder()
                .id(progress.getId())
                .projectId(progress.getProject().getId())
                .projectName(progress.getProject().getName())
                .totalTasks(progress.getTotalTasks())
                .completedTasks(progress.getCompletedTasks())
                .completionPercentage(progress.getCompletionPercentage())
                .totalTeams(progress.getTotalTeams())
                .lastUpdated(progress.getLastUpdated())
                .createdAt(progress.getCreatedAt())
                .updatedAt(progress.getUpdatedAt())
                .teamProjectProgressList(teamProjectProgressList)
                // ✅ NEW: Thêm user profile information
                .projectCreator(projectCreator)
                .projectMembers(memberProfiles)
                .lastUpdatedBy(lastUpdatedBy)
                .build();
    }

    // ✅ NEW: Helper method để lấy current user
    private User getCurrentUser() {
        org.springframework.security.core.Authentication authentication =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        org.springframework.security.core.userdetails.UserDetails userDetails =
            (org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal();

        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    // Method to refresh project progress data when task status changes
    @Transactional
    public void refreshProjectProgressData(Long projectId) {
        if (projectId != null) {
            getOrCreateProjectProgress(projectId);
        }
    }

    // Method to refresh all progress data when task changes
    @Transactional
    public void refreshAllProgressData(Long teamId, Long projectId) {
        System.out.println("🎯 [ProjectProgressService] Starting refreshAllProgressData - teamId=" + teamId + ", projectId=" + projectId);

        if (teamId != null && projectId != null) {
            // 1. Refresh team-project progress first
            System.out.println("1️⃣ [ProjectProgressService] Refreshing team-project progress...");
            teamProjectProgressService.refreshTeamProjectProgressData(teamId, projectId);

            // 2. Refresh project progress
            System.out.println("2️⃣ [ProjectProgressService] Refreshing project progress...");
            refreshProjectProgressData(projectId);

            // 3. ✅ FIX: Refresh team progress (đây là phần bị thiếu!)
            System.out.println("3️⃣ [ProjectProgressService] Refreshing team progress...");
            teamProgressService.getOrCreateTeamProgress(teamId);

            System.out.println("✅ [ProjectProgressService] Refreshed all progress: TeamProject, Project, and Team for teamId=" + teamId + ", projectId=" + projectId);
        } else {
            System.out.println("⚠️ [ProjectProgressService] Skipping refresh - teamId or projectId is null");
        }
    }

    // New method to get project progress with user details
    @Transactional(readOnly = true)
    public ProjectProgressResponseDto getProjectProgressWithUserDetails(Long projectId) {
        ProjectProgressResponseDto progressDto = getProjectProgress(projectId);

        // Get project members
        List<ProjectMember> projectMembers = projectMemberRepository.findByProjectId(projectId);

        // Get user details for each project member
        List<UserProfileDto> userProfileDtos = projectMembers.stream()
                .map(member -> {
                    User user = userRepository.findById(member.getUser().getId())
                            .orElseThrow(() -> new RuntimeException("User not found with id: " + member.getUser().getId()));

                    // Get the associated user profile
                    UserProfile profile = user.getUserProfile();
                    if (profile == null) {
                        // If no profile exists, create a minimal DTO with just the email
                        return UserProfileDto.builder()
                                .userId(user.getId())
                                .email(user.getEmail())
                                .build();
                    }

                    // Create UserProfileDto from UserProfile entity
                    return UserProfileDto.builder()
                            .userId(user.getId())
                            .email(user.getEmail())
                            .firstName(profile.getFirstName())
                            .lastName(profile.getLastName())
                            .username(profile.getUsername())
                            .jobTitle(profile.getJobTitle())
                            .department(profile.getDepartment())
                            .aboutMe(profile.getAboutMe())
                            .status(profile.getStatus())
                            .avatarUrl(profile.getAvtUrl())

                            .displayName(getDisplayName(profile))
                            .initials(getInitials(profile))
                            .build();
                })
                .collect(Collectors.toList());

        // Set project members in the response DTO
        progressDto.setProjectMembers(userProfileDtos);

        return progressDto;
    }

    // Helper method to generate display name
    private String getDisplayName(UserProfile profile) {
        if (profile.getFirstName() != null && profile.getLastName() != null) {
            return profile.getFirstName() + " " + profile.getLastName();
        } else if (profile.getUsername() != null) {
            return profile.getUsername();
        } else {
            return "Unknown";
        }
    }

    // Helper method to generate initials
    private String getInitials(UserProfile profile) {
        StringBuilder initials = new StringBuilder();
        if (profile.getFirstName() != null && !profile.getFirstName().isEmpty()) {
            initials.append(profile.getFirstName().charAt(0));
        }
        if (profile.getLastName() != null && !profile.getLastName().isEmpty()) {
            initials.append(profile.getLastName().charAt(0));
        }
        return initials.length() > 0 ? initials.toString().toUpperCase() : "?";
    }
}
