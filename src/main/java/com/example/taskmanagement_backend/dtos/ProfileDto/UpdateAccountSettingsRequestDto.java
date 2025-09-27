package com.example.taskmanagement_backend.dtos.ProfileDto;

import com.example.taskmanagement_backend.enums.Language;
import com.example.taskmanagement_backend.enums.Theme;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccountSettingsRequestDto {

    @NotNull(message = "Preferred language is required")
    private Language preferredLanguage;

    @NotNull(message = "Preferred theme is required")
    private Theme preferredTheme;
}
