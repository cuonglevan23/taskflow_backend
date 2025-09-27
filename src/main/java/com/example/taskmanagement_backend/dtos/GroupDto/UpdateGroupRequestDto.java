package com.example.taskmanagement_backend.dtos.GroupDto;


import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupRequestDto {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private Long leaderId;
}
