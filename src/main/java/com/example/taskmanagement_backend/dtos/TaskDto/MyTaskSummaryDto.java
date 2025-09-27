package com.example.taskmanagement_backend.dtos.TaskDto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ✅ ENHANCED PROJECTION DTO: Comprehensive task participation info
 * Includes participation type and context information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyTaskSummaryDto {
    
    // Basic task info
    private Long id;
    private String title;
    private String status;
    private String description;
    private String priority;
    private LocalDate startDate;
    private LocalDate deadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Creator info
    private Long creatorId;
    private String creatorName;
    
    // Context info
    private Long projectId;
    private String projectName;
    private Long teamId;
    private String teamName;
    
    // Statistics
    private Long checklistCount;
    private Long assigneeCount;
    
    // Participation type
    private String participationType; // CREATOR, ASSIGNEE, PROJECT_MEMBER, TEAM_MEMBER
    
    // ✅ NEW: Google Calendar information for summary view
    private String googleCalendarEventId;
    private String googleCalendarEventUrl;
    private String googleMeetLink;
    private Boolean isSyncedToCalendar;
    private LocalDateTime calendarSyncedAt;

    // Additional computed fields
    private Boolean isOverdue;
    private Integer completionPercentage;
    
    // Constructor for JPQL projection
    public MyTaskSummaryDto(Long id, String title, String status, String priority,
                           LocalDate startDate, LocalDate deadline, LocalDateTime createdAt, LocalDateTime updatedAt,
                           Long creatorId, String creatorName,
                           Long projectId, String projectName,
                           Long teamId, String teamName,
                           Long checklistCount, Long assigneeCount,
                           String participationType) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.priority = priority;
        this.startDate = startDate;
        this.deadline = deadline;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.projectId = projectId;
        this.projectName = projectName;
        this.teamId = teamId;
        this.teamName = teamName;
        this.checklistCount = checklistCount;
        this.assigneeCount = assigneeCount;
        this.participationType = participationType;
        
        // Computed fields
        this.isOverdue = deadline != null && deadline.isBefore(LocalDate.now()) && !"DONE".equals(status);
        this.completionPercentage = calculateCompletionPercentage(status);
    }
    
    private Integer calculateCompletionPercentage(String status) {
        return switch (status) {
            case "TODO" -> 0;
            case "IN_PROGRESS" -> 50;
            case "TESTING" -> 80;
            case "REVIEW" -> 90;
            case "DONE" -> 100;
            case "BLOCKED" -> 25;
            default -> 0;
        };
    }
}