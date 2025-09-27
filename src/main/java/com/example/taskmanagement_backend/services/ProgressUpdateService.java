package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.entities.ProjectTask;
import com.example.taskmanagement_backend.entities.TeamTask;
import com.example.taskmanagement_backend.entities.ProjectProgress;
import com.example.taskmanagement_backend.entities.TeamProjectProgress;
import com.example.taskmanagement_backend.repositories.ProjectProgressRepository;
import com.example.taskmanagement_backend.repositories.TeamProjectProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProgressUpdateService {

    private final ProjectProgressRepository projectProgressRepository;
    private final TeamProjectProgressRepository teamProjectProgressRepository;
    private final ProjectProgressService projectProgressService; // ✅ ADD: Thêm dependency

    /**
     * ✅ AUTO-UPDATE: Update progress when ProjectTask changes
     * Call this method whenever a ProjectTask status changes
     */
    public void updateProgressOnProjectTaskChange(ProjectTask projectTask) {
        if (projectTask.getProject() == null) {
            log.warn("ProjectTask {} has no associated project, skipping progress update", projectTask.getId());
            return;
        }

        try {
            Long projectId = projectTask.getProject().getId();
            log.info("🔄 [ProgressUpdateService] Updating ALL progress for project {} due to ProjectTask {} status change",
                    projectId, projectTask.getId());

            // ✅ FIX: Lấy teamId từ project để có thể cập nhật team progress
            // Cần query project để lấy teamId
            com.example.taskmanagement_backend.entities.Project project = projectTask.getProject();
            Long teamId = (project.getTeam() != null) ? project.getTeam().getId() : null;

            log.info("🔍 [ProgressUpdateService] Project info - projectId={}, teamId={}", projectId, teamId);

            if (teamId != null) {
                // ✅ FIX: Sử dụng refreshAllProgressData để cập nhật TẤT CẢ progress
                log.info("🚀 [ProgressUpdateService] Calling refreshAllProgressData for teamId={}, projectId={}", teamId, projectId);
                projectProgressService.refreshAllProgressData(teamId, projectId);
                log.info("✅ [ProgressUpdateService] Successfully updated ALL progress (Project + Team) for project {}", projectId);
            } else {
                // Project cá nhân - chỉ update project progress
                log.info("👤 [ProgressUpdateService] Personal project - only updating project progress");
                updateProjectProgress(projectId);
                log.info("✅ [ProgressUpdateService] Successfully updated project progress for personal project {}", projectId);
            }

        } catch (Exception e) {
            log.error("❌ Failed to update progress for ProjectTask {}: {}",
                    projectTask.getId(), e.getMessage(), e);
        }
    }

    /**
     * ✅ AUTO-UPDATE: Update progress when TeamTask changes
     * Call this method whenever a TeamTask status changes
     */
    public void updateProgressOnTeamTaskChange(TeamTask teamTask) {
        if (teamTask.getTeam() == null) {
            log.warn("TeamTask {} has no associated team, skipping progress update", teamTask.getId());
            return;
        }

        try {
            Long teamId = teamTask.getTeam().getId();

            // If TeamTask is related to a project, update team-project progress
            if (teamTask.getRelatedProject() != null) {
                Long projectId = teamTask.getRelatedProject().getId();
                log.info("🔄 Updating team-project progress for team {} and project {} due to TeamTask {} status change",
                        teamId, projectId, teamTask.getId());

                updateTeamProjectProgress(teamId, projectId);

                // Also update overall project progress
                updateProjectProgress(projectId);

                log.info("✅ Successfully updated team-project progress for team {} and project {}",
                        teamId, projectId);
            } else {
                log.debug("TeamTask {} is not related to any project, no project progress update needed",
                        teamTask.getId());
            }
        } catch (Exception e) {
            log.error("❌ Failed to update progress for TeamTask {}: {}",
                    teamTask.getId(), e.getMessage(), e);
        }
    }

    /**
     * Update overall project progress
     */
    public void updateProjectProgress(Long projectId) {
        // Get or create project progress record
        ProjectProgress progress = projectProgressRepository.findByProjectId(projectId)
                .orElse(ProjectProgress.builder()
                        .project(com.example.taskmanagement_backend.entities.Project.builder()
                                .id(projectId)
                                .build())
                        .build());

        // Calculate new totals using corrected queries
        Long totalTasks = projectProgressRepository.countTotalTasksByProject(projectId);
        Long completedTasks = projectProgressRepository.countCompletedTasksByProject(projectId);
        Long totalTeams = projectProgressRepository.countTeamsByProject(projectId);

        // Update progress data
        progress.setTotalTasks(totalTasks.intValue());
        progress.setCompletedTasks(completedTasks.intValue());
        progress.setTotalTeams(totalTeams.intValue());
        progress.calculateCompletionPercentage();
        progress.setLastUpdated(LocalDateTime.now());

        // Save updated progress
        projectProgressRepository.save(progress);

        log.debug("📊 Project {} progress updated: {}/{} tasks completed ({}%)",
                projectId, completedTasks, totalTasks, progress.getCompletionPercentage());
    }

    /**
     * Update team-project specific progress
     */
    private void updateTeamProjectProgress(Long teamId, Long projectId) {
        // Get or create team-project progress record
        TeamProjectProgress progress = teamProjectProgressRepository.findByTeamIdAndProjectId(teamId, projectId)
                .orElse(TeamProjectProgress.builder()
                        .team(com.example.taskmanagement_backend.entities.Team.builder()
                                .id(teamId)
                                .build())
                        .project(com.example.taskmanagement_backend.entities.Project.builder()
                                .id(projectId)
                                .build())
                        .build());

        // Calculate using TeamTask related to project
        Long totalTeamTasks = teamProjectProgressRepository.countTotalTasksByTeamAndProject(teamId, projectId);
        Long completedTeamTasks = teamProjectProgressRepository.countCompletedTasksByTeamAndProject(teamId, projectId);

        // Also include ProjectTasks assigned to team members (alternative calculation)
        Long totalProjectTasks = teamProjectProgressRepository.countProjectTasksAssignedToTeamMembers(teamId, projectId);
        Long completedProjectTasks = teamProjectProgressRepository.countCompletedProjectTasksAssignedToTeamMembers(teamId, projectId);

        // Combined totals (TeamTasks + ProjectTasks assigned to team members)
        Long totalCombined = totalTeamTasks + totalProjectTasks;
        Long completedCombined = completedTeamTasks + completedProjectTasks;

        // Update progress data
        progress.setTotalTasks(totalCombined.intValue());
        progress.setCompletedTasks(completedCombined.intValue());
        progress.calculateCompletionPercentage();
        progress.setLastUpdated(LocalDateTime.now());

        // Save updated progress
        teamProjectProgressRepository.save(progress);

        log.debug("📊 Team {} progress in project {} updated: {}/{} tasks completed ({}%)",
                teamId, projectId, completedCombined, totalCombined, progress.getCompletionPercentage());
    }

    /**
     * ✅ MANUAL REFRESH: Force refresh all progress for a project
     * Use this for batch updates or manual refresh
     */
    public void refreshProjectProgress(Long projectId) {
        log.info("🔄 Manual refresh of all progress for project {}", projectId);

        try {
            // Update overall project progress
            updateProjectProgress(projectId);

            // Update all team-project progress for this project
            teamProjectProgressRepository.findByProjectId(projectId)
                    .forEach(teamProgress -> {
                        Long teamId = teamProgress.getTeam().getId();
                        updateTeamProjectProgress(teamId, projectId);
                    });

            log.info("✅ Successfully refreshed all progress for project {}", projectId);
        } catch (Exception e) {
            log.error("❌ Failed to refresh progress for project {}: {}", projectId, e.getMessage(), e);
        }
    }

    /**
     * ✅ MANUAL REFRESH: Force refresh team progress across all projects
     */
    public void refreshTeamProgress(Long teamId) {
        log.info("🔄 Manual refresh of all progress for team {}", teamId);

        try {
            // Update all team-project progress for this team
            teamProjectProgressRepository.findByTeamId(teamId)
                    .forEach(teamProgress -> {
                        Long projectId = teamProgress.getProject().getId();
                        updateTeamProjectProgress(teamId, projectId);
                        // Also refresh overall project progress
                        updateProjectProgress(projectId);
                    });

            log.info("✅ Successfully refreshed all progress for team {}", teamId);
        } catch (Exception e) {
            log.error("❌ Failed to refresh progress for team {}: {}", teamId, e.getMessage(), e);
        }
    }

    /**
     * ✅ BATCH REFRESH: Force refresh all progress in the system
     * Use this for system maintenance or after major data changes
     */
    public void refreshAllProgress() {
        log.info("🔄 Manual refresh of ALL progress in the system");

        try {
            // Refresh all project progress
            projectProgressRepository.findAll().forEach(progress -> {
                Long projectId = progress.getProject().getId();
                updateProjectProgress(projectId);
            });

            // Refresh all team-project progress
            teamProjectProgressRepository.findAll().forEach(teamProgress -> {
                Long teamId = teamProgress.getTeam().getId();
                Long projectId = teamProgress.getProject().getId();
                updateTeamProjectProgress(teamId, projectId);
            });

            log.info("✅ Successfully refreshed ALL progress in the system");
        } catch (Exception e) {
            log.error("❌ Failed to refresh all progress: {}", e.getMessage(), e);
        }
    }

    /**
     * Get progress statistics for monitoring
     */
    public ProgressStats getProgressStats() {
        long totalProjects = projectProgressRepository.count();
        long totalTeamProjectProgress = teamProjectProgressRepository.count();

        return ProgressStats.builder()
                .totalProjects(totalProjects)
                .totalTeamProjectProgress(totalTeamProjectProgress)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    // ===== Inner Classes =====

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProgressStats {
        private long totalProjects;
        private long totalTeamProjectProgress;
        private LocalDateTime lastUpdated;
    }
}