package com.example.taskmanagement_backend.dtos.OrganizationDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrganizationRequestDto {

    @NotBlank(message = "Organization name is required")
    private String name;
    @NotNull(message = "owner is required")
    private Long owner_id;
}