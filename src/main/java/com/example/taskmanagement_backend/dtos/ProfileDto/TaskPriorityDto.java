package com.example.taskmanagement_backend.dtos.ProfileDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskPriorityDto {
    private Long id;
    private String priorityKey;
    private String displayName;
    private String color;
    private Integer sortOrder;
    private Boolean isDefault;
    private Boolean isActive;
}