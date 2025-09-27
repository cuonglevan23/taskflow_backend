package com.example.taskmanagement_backend.dtos.GroupDto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupRequestDto {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Project ID is required")
    private Long projectId;

    private Long leaderId;
}
