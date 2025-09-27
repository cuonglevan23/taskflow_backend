package com.example.taskmanagement_backend.dtos.ProjectDto;

import com.example.taskmanagement_backend.enums.ProjectTimelineEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTimelineResponseDto {
    private Long id;
    private ProjectTimelineEventType eventType;
    private String eventDescription;
    private String oldValue;
    private String newValue;
    private String changedByUserName;
    private String changedByUserEmail;
    private LocalDateTime createdAt;
}
