package com.example.taskmanagement_backend.dtos.AnalyticsDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsFilterDto {

    // Time range filters
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    // Period type for grouping
    private String periodType; // "day", "week", "month", "quarter", "year"

    // Timezone for proper date grouping
    private String timezone; // Default: "UTC"

    // Data type filters
    private boolean includeRegistrations;
    private boolean includeLogins;
    private boolean includeOnlineStatus;

    // Comparison options
    private boolean compareWithPreviousPeriod;
    private Integer periodCount; // Number of periods to include (e.g., last 12 months)

    // User status filters
    private String userStatus; // "ACTIVE", "INACTIVE", "SUSPENDED", "ALL"

    // Chart options
    private String chartType; // "line", "bar", "area"
    private boolean showTrend;
    private boolean showPercentage;
}
