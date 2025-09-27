package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ProcessDto.TeamProjectProgressResponseDto;
import com.example.taskmanagement_backend.dtos.UserDto.UserProfileDto;
import com.example.taskmanagement_backend.entities.Team;
import com.example.taskmanagement_backend.entities.TeamProjectProgress;
import com.example.taskmanagement_backend.entities.TeamMember;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.TeamProjectProgressRepository;
import com.example.taskmanagement_backend.repositories.TeamJpaRepository;
import com.example.taskmanagement_backend.repositories.ProjectJpaRepository;
import com.example.taskmanagement_backend.repositories.TeamMemberJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamProjectProgressService {

    private final TeamProjectProgressRepository teamProjectProgressRepository;
    private final TeamJpaRepository teamRepository;
    private final ProjectJpaRepository projectRepository;
    private final TeamMemberJpaRepository teamMemberRepository;
    private final UserJpaRepository userRepository;
    private final UserProfileMapper userProfileMapper; // ✅ NEW: Thêm dependency để convert user profiles

    @Transactional(readOnly = true)
    public List<TeamProjectProgressResponseDto> getTeamProjectProgressByTeamId(Long teamId) {
        // Verify team exists
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

        return teamProjectProgressRepository.findByTeamId(teamId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TeamProjectProgressResponseDto> getTeamProjectProgressByProjectId(Long projectId) {
        // Verify project exists
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));

        return teamProjectProgressRepository.findByProjectId(projectId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TeamProjectProgress getOrCreateTeamProjectProgress(Long teamId, Long projectId) {
        Optional<TeamProjectProgress> existingProgress = teamProjectProgressRepository.findByTeamIdAndProjectId(teamId, projectId);
        
        if (existingProgress.isPresent()) {
            // Update existing progress
            TeamProjectProgress progress = existingProgress.get();
            updateTeamProjectProgressData(progress, teamId, projectId);
            return teamProjectProgressRepository.save(progress);
        } else {
            // Create new progress
            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
            
            TeamProjectProgress newProgress = TeamProjectProgress.builder()
                    .team(team)
                    .project(project)
                    .build();
            
            updateTeamProjectProgressData(newProgress, teamId, projectId);
            return teamProjectProgressRepository.save(newProgress);
        }
    }

    private void updateTeamProjectProgressData(TeamProjectProgress progress, Long teamId, Long projectId) {
        Long totalTasks = teamProjectProgressRepository.countTotalTasksByTeamAndProject(teamId, projectId);
        Long completedTasks = teamProjectProgressRepository.countCompletedTasksByTeamAndProject(teamId, projectId);
        
        progress.setTotalTasks(totalTasks.intValue());
        progress.setCompletedTasks(completedTasks.intValue());
        progress.calculateCompletionPercentage();
        progress.setLastUpdated(LocalDateTime.now());
    }

    private TeamProjectProgressResponseDto convertToDto(TeamProjectProgress progress) {
        Team team = progress.getTeam();
        Project project = progress.getProject();
        Long teamId = team.getId();
        Long projectId = project.getId();

        // ✅ NEW: Lấy thông tin user profiles cho team-project progress

        // 1. Lấy team members trong project này
        List<TeamMember> teamMembers = teamMemberRepository.findByTeamId(teamId);
        List<UserProfileDto> teamMembersInProject = teamMembers.stream()
                .map(member -> userProfileMapper.toUserProfileDto(member.getUser()))
                .collect(Collectors.toList());

        // 2. Lấy user cuối cùng update progress
        UserProfileDto lastUpdatedBy = null;
        try {
            User currentUser = getCurrentUser();
            lastUpdatedBy = userProfileMapper.toUserProfileDto(currentUser);
        } catch (Exception e) {
            // Nếu không lấy được current user, để null
        }

        return TeamProjectProgressResponseDto.builder()
                .id(progress.getId())
                .teamId(progress.getTeam().getId())
                .teamName(progress.getTeam().getName())
                .projectId(progress.getProject().getId())
                .projectName(progress.getProject().getName())
                .totalTasks(progress.getTotalTasks())
                .completedTasks(progress.getCompletedTasks())
                .completionPercentage(progress.getCompletionPercentage())
                .lastUpdated(progress.getLastUpdated())
                .createdAt(progress.getCreatedAt())
                .updatedAt(progress.getUpdatedAt())
                // ✅ NEW: Thêm user profile information
                .teamMembersInProject(teamMembersInProject)
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

    // Method to refresh team-project progress data when task status changes
    @Transactional
    public void refreshTeamProjectProgressData(Long teamId, Long projectId) {
        if (teamId != null && projectId != null) {
            getOrCreateTeamProjectProgress(teamId, projectId);
        }
    }
}