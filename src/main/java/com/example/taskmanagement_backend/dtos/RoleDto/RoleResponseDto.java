package com.example.taskmanagement_backend.dtos.RoleDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponseDto {
    private Long id;
    private String roleName;
}
