package com.example.taskmanagement_backend.dtos.OrganizationDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizationRequestDto {

    @NotBlank(message = "Organization name is required")
    private String name;

    @NotNull(message = "Onwer is required")
    private Long owner_id;

}
