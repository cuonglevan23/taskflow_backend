package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.AnalyticsDto.UserAnalyticsResponseDto;
import com.example.taskmanagement_backend.dtos.AnalyticsDto.AnalyticsFilterDto;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.UserStatus;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAnalyticsService {

    private final UserJpaRepository userRepository;

    /**
     * Get comprehensive user analytics data
     */
    public UserAnalyticsResponseDto getUserAnalytics(AnalyticsFilterDto filter) {
        try {
            log.info("🔍 [UserAnalyticsService] Generating user analytics for period: {}", filter.getPeriodType());

            // Set default values if not provided
            if (filter.getStartDate() == null) {
                filter.setStartDate(LocalDateTime.now().minusYears(1));
            }
            if (filter.getEndDate() == null) {
                filter.setEndDate(LocalDateTime.now());
            }

            // Get current statistics
            Long totalUsers = userRepository.count();
            Long activeUsers = userRepository.countByDeletedFalseAndStatus(UserStatus.ACTIVE);
            Long onlineUsers = userRepository.countByOnlineTrue();

            // Get new users for different periods
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();
            LocalDateTime monthStart = LocalDate.now().minusMonths(1).atStartOfDay();

            Long newUsersToday = userRepository.countByCreatedAtAfter(todayStart);
            Long newUsersThisWeek = userRepository.countByCreatedAtAfter(weekStart);
            Long newUsersThisMonth = userRepository.countByCreatedAtAfter(monthStart);

            // Generate chart data based on period type
            UserAnalyticsResponseDto response = UserAnalyticsResponseDto.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .onlineUsers(onlineUsers)
                .newUsersToday(newUsersToday)
                .newUsersThisWeek(newUsersThisWeek)
                .newUsersThisMonth(newUsersThisMonth)
                .generatedAt(LocalDateTime.now())
                .reportPeriod(filter.getPeriodType())
                .build();

            // Generate registration charts
            if (filter.isIncludeRegistrations()) {
                response.setDailyRegistrations(generateDailyRegistrations(filter.getStartDate(), filter.getEndDate()));
                response.setMonthlyRegistrations(generateMonthlyRegistrations(filter.getStartDate(), filter.getEndDate()));
                response.setQuarterlyRegistrations(generateQuarterlyRegistrations(filter.getStartDate(), filter.getEndDate()));
                response.setYearlyRegistrations(generateYearlyRegistrations(filter.getStartDate(), filter.getEndDate()));
            }

            // Generate login charts
            if (filter.isIncludeLogins()) {
                response.setDailyLogins(generateDailyLogins(filter.getStartDate(), filter.getEndDate()));
                response.setMonthlyLogins(generateMonthlyLogins(filter.getStartDate(), filter.getEndDate()));
                response.setQuarterlyLogins(generateQuarterlyLogins(filter.getStartDate(), filter.getEndDate()));
                response.setYearlyLogins(generateYearlyLogins(filter.getStartDate(), filter.getEndDate()));
            }

            // Calculate growth rates
            response.setMonthlyGrowthRate(calculateMonthlyGrowthRate());
            response.setQuarterlyGrowthRate(calculateQuarterlyGrowthRate());
            response.setYearlyGrowthRate(calculateYearlyGrowthRate());

            // Find peak times
            response.setPeakRegistrationTime(findPeakRegistrationTime());
            response.setPeakLoginTime(findPeakLoginTime());

            log.info("✅ [UserAnalyticsService] Generated analytics - Total Users: {}, Active: {}, Online: {}",
                    totalUsers, activeUsers, onlineUsers);

            return response;

        } catch (Exception e) {
            log.error("❌ [UserAnalyticsService] Error generating user analytics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate user analytics", e);
        }
    }

    /**
     * Generate daily registration data for the last 30 days
     */
    private List<UserAnalyticsResponseDto.ChartDataPoint> generateDailyRegistrations(LocalDateTime startDate, LocalDateTime endDate) {
        List<UserAnalyticsResponseDto.ChartDataPoint> dataPoints = new ArrayList<>();

        LocalDate start = startDate.toLocalDate();
        LocalDate end = endDate.toLocalDate();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            Long count = userRepository.countByCreatedAtBetween(dayStart, dayEnd);

            dataPoints.add(UserAnalyticsResponseDto.ChartDataPoint.builder()
                .label(date.format(DateTimeFormatter.ofPattern("MM/dd")))
                .value(count)
                .period("day")
                .date(dayStart)
                .build());
        }

        return dataPoints;
    }

    /**
     * Generate monthly registration data for the last 12 months
     */
    private List<UserAnalyticsResponseDto.ChartDataPoint> generateMonthlyRegistrations(LocalDateTime startDate, LocalDateTime endDate) {
        List<UserAnalyticsResponseDto.ChartDataPoint> dataPoints = new ArrayList<>();

        LocalDate start = startDate.toLocalDate().withDayOfMonth(1);
        LocalDate end = endDate.toLocalDate();

        for (LocalDate month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            LocalDateTime monthStart = month.atStartOfDay();
            LocalDateTime monthEnd = month.plusMonths(1).atStartOfDay();

            Long count = userRepository.countByCreatedAtBetween(monthStart, monthEnd);

            dataPoints.add(UserAnalyticsResponseDto.ChartDataPoint.builder()
                .label(month.format(DateTimeFormatter.ofPattern("MMM yyyy")))
                .value(count)
                .period("month")
                .date(monthStart)
                .build());
        }

        return dataPoints;
    }

    /**
     * Generate quarterly registration data
     */
    private List<UserAnalyticsResponseDto.ChartDataPoint> generateQuarterlyRegistrations(LocalDateTime startDate, LocalDateTime endDate) {
        List<UserAnalyticsResponseDto.ChartDataPoint> dataPoints = new ArrayList<>();

        LocalDate start = startDate.toLocalDate().withDayOfMonth(1).withMonth(((startDate.getMonthValue() - 1) / 3) * 3 + 1);
        LocalDate end = endDate.toLocalDate();

        for (LocalDate quarter = start; !quarter.isAfter(end); quarter = quarter.plusMonths(3)) {
            LocalDateTime quarterStart = quarter.atStartOfDay();
            LocalDateTime quarterEnd = quarter.plusMonths(3).atStartOfDay();

            Long count = userRepository.countByCreatedAtBetween(quarterStart, quarterEnd);

            int quarterNum = ((quarter.getMonthValue() - 1) / 3) + 1;

            dataPoints.add(UserAnalyticsResponseDto.ChartDataPoint.builder()
                .label("Q" + quarterNum + " " + quarter.getYear())
                .value(count)
                .period("quarter")
                .date(quarterStart)
                .build());
        }

        return dataPoints;
    }

    /**
     * Generate yearly registration data
     */
    private List<UserAnalyticsResponseDto.ChartDataPoint> generateYearlyRegistrations(LocalDateTime startDate, LocalDateTime endDate) {
        List<UserAnalyticsResponseDto.ChartDataPoint> dataPoints = new ArrayList<>();

        int startYear = startDate.getYear();
        int endYear = endDate.getYear();

        for (int year = startYear; year <= endYear; year++) {
            LocalDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay();
            LocalDateTime yearEnd = LocalDate.of(year + 1, 1, 1).atStartOfDay();

            Long count = userRepository.countByCreatedAtBetween(yearStart, yearEnd);

            dataPoints.add(UserAnalyticsResponseDto.ChartDataPoint.builder()
                .label(String.valueOf(year))
                .value(count)
                .period("year")
                .date(yearStart)
                .build());
        }

        return dataPoints;
    }

    // Login analytics methods (similar structure but using lastLoginAt)
    private List<UserAnalyticsResponseDto.ChartDataPoint> generateDailyLogins(LocalDateTime startDate, LocalDateTime endDate) {
        List<UserAnalyticsResponseDto.ChartDataPoint> dataPoints = new ArrayList<>();

        LocalDate start = startDate.toLocalDate();
        LocalDate end = endDate.toLocalDate();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            Long count = userRepository.countByLastLoginAtBetween(dayStart, dayEnd);

            dataPoints.add(UserAnalyticsResponseDto.ChartDataPoint.builder()
                .label(date.format(DateTimeFormatter.ofPattern("MM/dd")))
                .value(count)
                .period("day")
                .date(dayStart)
                .build());
        }

        return dataPoints;
    }

    private List<UserAnalyticsResponseDto.ChartDataPoint> generateMonthlyLogins(LocalDateTime startDate, LocalDateTime endDate) {
        List<UserAnalyticsResponseDto.ChartDataPoint> dataPoints = new ArrayList<>();

        LocalDate start = startDate.toLocalDate().withDayOfMonth(1);
        LocalDate end = endDate.toLocalDate();

        for (LocalDate month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            LocalDateTime monthStart = month.atStartOfDay();
            LocalDateTime monthEnd = month.plusMonths(1).atStartOfDay();

            Long count = userRepository.countByLastLoginAtBetween(monthStart, monthEnd);

            dataPoints.add(UserAnalyticsResponseDto.ChartDataPoint.builder()
                .label(month.format(DateTimeFormatter.ofPattern("MMM yyyy")))
                .value(count)
                .period("month")
                .date(monthStart)
                .build());
        }

        return dataPoints;
    }

    private List<UserAnalyticsResponseDto.ChartDataPoint> generateQuarterlyLogins(LocalDateTime startDate, LocalDateTime endDate) {
        // Similar implementation to quarterly registrations but using lastLoginAt
        return new ArrayList<>(); // Simplified for brevity
    }

    private List<UserAnalyticsResponseDto.ChartDataPoint> generateYearlyLogins(LocalDateTime startDate, LocalDateTime endDate) {
        // Similar implementation to yearly registrations but using lastLoginAt
        return new ArrayList<>(); // Simplified for brevity
    }

    /**
     * Calculate monthly growth rate
     */
    private Double calculateMonthlyGrowthRate() {
        LocalDateTime thisMonthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonthStart = thisMonthStart.minusMonths(1);

        Long thisMonthUsers = userRepository.countByCreatedAtBetween(thisMonthStart, LocalDateTime.now());
        Long lastMonthUsers = userRepository.countByCreatedAtBetween(lastMonthStart, thisMonthStart);

        if (lastMonthUsers == 0) return 0.0;
        return ((double) (thisMonthUsers - lastMonthUsers) / lastMonthUsers) * 100;
    }

    /**
     * Calculate quarterly growth rate
     */
    private Double calculateQuarterlyGrowthRate() {
        LocalDateTime thisQuarterStart = LocalDate.now().withDayOfMonth(1)
            .withMonth(((LocalDate.now().getMonthValue() - 1) / 3) * 3 + 1).atStartOfDay();
        LocalDateTime lastQuarterStart = thisQuarterStart.minusMonths(3);

        Long thisQuarterUsers = userRepository.countByCreatedAtBetween(thisQuarterStart, LocalDateTime.now());
        Long lastQuarterUsers = userRepository.countByCreatedAtBetween(lastQuarterStart, thisQuarterStart);

        if (lastQuarterUsers == 0) return 0.0;
        return ((double) (thisQuarterUsers - lastQuarterUsers) / lastQuarterUsers) * 100;
    }

    /**
     * Calculate yearly growth rate
     */
    private Double calculateYearlyGrowthRate() {
        LocalDateTime thisYearStart = LocalDate.now().withDayOfYear(1).atStartOfDay();
        LocalDateTime lastYearStart = thisYearStart.minusYears(1);

        Long thisYearUsers = userRepository.countByCreatedAtBetween(thisYearStart, LocalDateTime.now());
        Long lastYearUsers = userRepository.countByCreatedAtBetween(lastYearStart, thisYearStart);

        if (lastYearUsers == 0) return 0.0;
        return ((double) (thisYearUsers - lastYearUsers) / lastYearUsers) * 100;
    }

    /**
     * Find peak registration time
     */
    private Map<String, Object> findPeakRegistrationTime() {
        // Get registrations by hour to find peak time
        List<User> recentUsers = userRepository.findByCreatedAtAfter(LocalDateTime.now().minusDays(30));

        Map<Integer, Long> hourCounts = recentUsers.stream()
            .collect(Collectors.groupingBy(
                user -> user.getCreatedAt().getHour(),
                Collectors.counting()
            ));

        Integer peakHour = hourCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(12);

        return Map.of(
            "hour", peakHour,
            "timeRange", peakHour + ":00 - " + (peakHour + 1) + ":00",
            "count", hourCounts.getOrDefault(peakHour, 0L)
        );
    }

    /**
     * Find peak login time
     */
    private Map<String, Object> findPeakLoginTime() {
        // Get logins by hour to find peak time
        List<User> recentLogins = userRepository.findByLastLoginAtAfter(LocalDateTime.now().minusDays(30));

        Map<Integer, Long> hourCounts = recentLogins.stream()
            .filter(user -> user.getLastLoginAt() != null)
            .collect(Collectors.groupingBy(
                user -> user.getLastLoginAt().getHour(),
                Collectors.counting()
            ));

        Integer peakHour = hourCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(9);

        return Map.of(
            "hour", peakHour,
            "timeRange", peakHour + ":00 - " + (peakHour + 1) + ":00",
            "count", hourCounts.getOrDefault(peakHour, 0L)
        );
    }
}
