package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectTimelineResponseDto;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.ProjectTimeline;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.ProjectTimelineEventType;
import com.example.taskmanagement_backend.repositories.ProjectTimelineRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ProjectTimelineService {

    @Autowired
    private ProjectTimelineRepository projectTimelineRepository;

    /**
     * 📅 Lấy timeline của project
     */
    public List<ProjectTimelineResponseDto> getProjectTimeline(Long projectId) {
        log.info("📅 [ProjectTimelineService] Getting timeline for project ID: {}", projectId);

        List<ProjectTimeline> timelines = projectTimelineRepository
            .findByProjectIdWithUserOrderByCreatedAtDesc(projectId);

        return timelines.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * ➕ Thêm sự kiện vào timeline
     */
    public void addTimelineEvent(Project project, ProjectTimelineEventType eventType,
                               String description, String oldValue, String newValue, User changedBy) {
        log.info("➕ [ProjectTimelineService] Adding timeline event: {} for project {}",
                eventType, project.getId());

        ProjectTimeline timeline = ProjectTimeline.builder()
            .project(project)
            .eventType(eventType)
            .eventDescription(description)
            .oldValue(oldValue)
            .newValue(newValue)
            .changedBy(changedBy)
            .build();

        projectTimelineRepository.save(timeline);
    }

    /**
     * 🔄 Convert entity to DTO
     */
    private ProjectTimelineResponseDto convertToDto(ProjectTimeline timeline) {
        return ProjectTimelineResponseDto.builder()
            .id(timeline.getId())
            .eventType(timeline.getEventType())
            .eventDescription(timeline.getEventDescription())
            .oldValue(timeline.getOldValue())
            .newValue(timeline.getNewValue())
            .changedByUserName(timeline.getChangedBy() != null ?
                timeline.getChangedBy().getUsername() : "System")
            .changedByUserEmail(timeline.getChangedBy() != null ?
                timeline.getChangedBy().getEmail() : null)
            .createdAt(timeline.getCreatedAt())
            .build();
    }

    /**
     * 🎯 Helper methods cho các loại sự kiện khác nhau
     */
    public void addProjectCreatedEvent(Project project, User creator) {
        addTimelineEvent(project, ProjectTimelineEventType.PROJECT_CREATED,
            "Project '" + project.getName() + "' was created", null, null, creator);
    }

    public void addProjectNameChangedEvent(Project project, String oldName, String newName, User changedBy) {
        addTimelineEvent(project, ProjectTimelineEventType.PROJECT_NAME_CHANGED,
            "Project name changed from '" + oldName + "' to '" + newName + "'",
            oldName, newName, changedBy);
    }

    public void addProjectStatusChangedEvent(Project project, String oldStatus, String newStatus, User changedBy) {
        addTimelineEvent(project, ProjectTimelineEventType.PROJECT_STATUS_CHANGED,
            "Project status changed from '" + oldStatus + "' to '" + newStatus + "'",
            oldStatus, newStatus, changedBy);
    }

    public void addProjectDescriptionChangedEvent(Project project, String oldDesc, String newDesc, User changedBy) {
        addTimelineEvent(project, ProjectTimelineEventType.PROJECT_DESCRIPTION_CHANGED,
            "Project description was updated", oldDesc, newDesc, changedBy);
    }

    public void addProjectUpdatedEvent(Project project, User changedBy) {
        addTimelineEvent(project, ProjectTimelineEventType.PROJECT_UPDATED,
            "Project details were updated", null, null, changedBy);
    }
}
