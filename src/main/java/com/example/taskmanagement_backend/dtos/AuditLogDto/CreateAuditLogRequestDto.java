package com.example.taskmanagement_backend.dtos.AuditLogDto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAuditLogRequestDto {

    private Long userId; // có thể null nếu action không gắn với người dùng

    @NotBlank(message = "Action is required")
    private String action;
}