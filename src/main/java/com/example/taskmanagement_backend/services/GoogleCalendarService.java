package com.example.taskmanagement_backend.services;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.ProjectTask;
import com.example.taskmanagement_backend.entities.OAuthProvider;
import com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CreateCalendarEventRequestDto;
import com.example.taskmanagement_backend.dtos.GoogleCalendarDto.CalendarEventResponseDto;
import com.example.taskmanagement_backend.repositories.TaskRepository;
import com.example.taskmanagement_backend.repositories.ProjectTaskJpaRepository;
import com.example.taskmanagement_backend.repositories.OAuthProviderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GoogleCalendarService {

    private static final String APPLICATION_NAME = "Task Management Calendar";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectTaskJpaRepository projectTaskRepository;

    @Autowired
    private OAuthProviderRepository oauthProviderRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * ✅ ENHANCED: Tự động lấy Google access token từ user đã đăng nhập với auto-refresh
     */
    public String getGoogleAccessTokenForUser(String userEmail) {
        OAuthProvider provider = oauthProviderRepository.findByUser_EmailAndProviderName(userEmail, "google")
                .orElseThrow(() -> new RuntimeException("No Google authorization found for user. Please authorize Google Calendar access first."));

        // Kiểm tra xem token có hết hạn không (thêm buffer 5 phút)
        if (provider.getTokenExpiresAt() != null &&
            provider.getTokenExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) {

            log.info("🔄 Google access token expired for user: {}, attempting refresh...", userEmail);

            // Thử refresh token
            if (provider.getRefreshToken() != null && !provider.getRefreshToken().isEmpty()) {
                try {
                    refreshAccessToken(provider);
                    log.info("✅ Successfully refreshed Google access token for user: {}", userEmail);
                } catch (Exception e) {
                    log.error("❌ Failed to refresh Google access token for user: {}, error: {}", userEmail, e.getMessage());
                    throw new RuntimeException("Google access token expired and refresh failed. Please re-authorize Google Calendar access.");
                }
            } else {
                log.warn("⚠️ No refresh token available for user: {}", userEmail);
                throw new RuntimeException("Google access token expired. Please re-authorize Google Calendar access.");
            }
        }

        return provider.getAccessToken();
    }

    /**
     * 🔄 NEW: Refresh Google access token using refresh token
     */
    private void refreshAccessToken(OAuthProvider provider) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("client_id", googleClientId);
        requestBody.add("client_secret", googleClientSecret);
        requestBody.add("refresh_token", provider.getRefreshToken());
        requestBody.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(GOOGLE_TOKEN_URL, request, (Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();

                String newAccessToken = (String) responseBody.get("access_token");
                Integer expiresIn = (Integer) responseBody.get("expires_in");

                if (newAccessToken != null) {
                    // Cập nhật access token và expiry time
                    provider.setAccessToken(newAccessToken);

                    if (expiresIn != null) {
                        provider.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
                    } else {
                        // Default 1 hour if not provided
                        provider.setTokenExpiresAt(LocalDateTime.now().plusHours(1));
                    }

                    // Cập nhật refresh token nếu có (Google đôi khi trả về refresh token mới)
                    String newRefreshToken = (String) responseBody.get("refresh_token");
                    if (newRefreshToken != null && !newRefreshToken.isEmpty()) {
                        provider.setRefreshToken(newRefreshToken);
                    }

                    provider.setUpdatedAt(LocalDateTime.now());
                    oauthProviderRepository.save(provider);

                    log.info("✅ Token refreshed successfully for user: {}, expires at: {}",
                             provider.getUser().getEmail(), provider.getTokenExpiresAt());
                } else {
                    throw new RuntimeException("No access token in refresh response");
                }
            } else {
                throw new RuntimeException("Failed to refresh token, status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("❌ Error refreshing Google token: {}", e.getMessage());
            throw new RuntimeException("Failed to refresh Google access token: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ ENHANCED: Tạo sự kiện Google Calendar tự động với token từ user đã đăng nhập
     */
    public CalendarEventResponseDto createEventWithUserToken(CreateCalendarEventRequestDto dto, String userEmail) {
        try {
            // Tự động lấy Google access token từ user đã đăng nhập
            String accessToken = getGoogleAccessTokenForUser(userEmail);
            dto.setAccessToken(accessToken);

            return createEvent(dto);
        } catch (Exception e) {
            log.error("❌ Error creating calendar event with user token for user {}: {}", userEmail, e.getMessage());
            throw new RuntimeException("Failed to create calendar event: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ ENHANCED: Tạo sự kiện Google Calendar với đầy đủ tính năng
     */
    public CalendarEventResponseDto createEvent(CreateCalendarEventRequestDto dto) {
        try {
            Task task = taskRepository.findById(dto.getTaskId())
                    .orElseThrow(() -> new RuntimeException("Task not found"));

            Calendar service = getCalendarService(dto.getAccessToken());

            // Tạo sự kiện với title và description
            Event event = new Event()
                    .setSummary(buildEventTitle(task, dto))
                    .setDescription(buildEventDescription(task, dto));

            // ✅ Set thời gian bắt đầu và kết thúc (enhanced)
            setEventDateTime(event, task, dto);

            // ✅ Add Google Meet nếu được yêu cầu
            if (Boolean.TRUE.equals(dto.getCreateMeet())) {
                addGoogleMeet(event);
            }

            // ✅ Add attendees nếu có
            if (dto.getAttendeeEmails() != null && !dto.getAttendeeEmails().isEmpty()) {
                addAttendees(event, dto.getAttendeeEmails());
            }

            // ✅ Set location nếu có
            if (dto.getLocation() != null && !dto.getLocation().isEmpty()) {
                event.setLocation(dto.getLocation());
            }

            // ✅ Set reminder/notification
            if (dto.getReminderMinutes() != null && !dto.getReminderMinutes().isEmpty()) {
                addReminders(event, dto.getReminderMinutes());
            }

            // ✅ Set event color
            if (dto.getEventColor() != null) {
                event.setColorId(dto.getEventColor());
            }

            // ✅ Set visibility
            if (dto.getVisibility() != null) {
                event.setVisibility(dto.getVisibility());
            }

            // ✅ Set transparency (busy/free)
            if (dto.getTransparency() != null) {
                event.setTransparency(dto.getTransparency());
            }

            // ✅ Add recurrence rule nếu có
            if (Boolean.TRUE.equals(dto.getIsRecurring()) && dto.getRecurrenceRule() != null) {
                event.setRecurrence(Arrays.asList(dto.getRecurrenceRule()));
            }

            // Tạo sự kiện trên calendar
            String calendarId = dto.getCalendarId() != null ? dto.getCalendarId() : "primary";
            event = service.events().insert(calendarId, event)
                    .setConferenceDataVersion(Boolean.TRUE.equals(dto.getCreateMeet()) ? 1 : 0)
                    .execute();

            // Lấy meet link từ conference data
            String meetLink = extractMeetLink(event);

            // ✅ ENHANCED: Lưu đầy đủ thông tin Google Calendar vào task
            task.setGoogleCalendarEventId(event.getId());
            task.setGoogleCalendarEventUrl(event.getHtmlLink());
            task.setGoogleMeetLink(meetLink);
            task.setIsSyncedToCalendar(true);
            task.setCalendarSyncedAt(LocalDateTime.now());
            taskRepository.save(task);

            log.info("✅ Event created successfully: {}", event.getHtmlLink());


            return CalendarEventResponseDto.builder()
                    .eventId(event.getId())
                    .eventUrl(event.getHtmlLink())
                    .meetLink(meetLink)
                    .title(event.getSummary())
                    .startTime(event.getStart().getDateTime().toString())
                    .endTime(event.getEnd().getDateTime().toString())
                    .attendeeCount(dto.getAttendeeEmails() != null ? dto.getAttendeeEmails().size() : 0)
                    .hasReminders(dto.getReminderMinutes() != null && !dto.getReminderMinutes().isEmpty())
                    .isRecurring(Boolean.TRUE.equals(dto.getIsRecurring()))
                    .message("Sự kiện Google Calendar đã được tạo thành công" +
                            (meetLink != null ? " với Google Meet" : ""))
                    .build();

        } catch (Exception e) {
            log.error("❌ Error creating calendar event for task {}: {}", dto.getTaskId(), e.getMessage());
            throw new RuntimeException("Failed to create calendar event: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NEW: Tạo quick meeting cho task
     */
    public CalendarEventResponseDto createQuickMeeting(Long taskId, String accessToken,
                                                       String title, Integer durationMinutes,
                                                       List<String> attendeeEmails) {
        CreateCalendarEventRequestDto dto = CreateCalendarEventRequestDto.builder()
                .taskId(taskId)
                .accessToken(accessToken)
                .customTitle(title != null ? title : "Quick Meeting")
                .durationMinutes(durationMinutes != null ? durationMinutes : 60)
                .createMeet(true)
                .attendeeEmails(attendeeEmails)
                .meetingType("quick_meeting")
                .reminderMinutes(Arrays.asList(15, 5)) // Nhắc nhở 15 và 5 phút trước
                .build();

        return createEvent(dto);
    }

    /**
     * ✅ NEW: Tạo reminder cho task
     */
    public CalendarEventResponseDto createTaskReminder(Long taskId, String accessToken,
                                                       String reminderTime, Integer durationMinutes) {
        CreateCalendarEventRequestDto dto = CreateCalendarEventRequestDto.builder()
                .taskId(taskId)
                .accessToken(accessToken)
                .customTitle("⏰ Nhắc nhở Task")
                .customStartTime(reminderTime)
                .durationMinutes(durationMinutes != null ? durationMinutes : 30)
                .isReminder(true)
                .createMeet(false)
                .meetingType("reminder")
                .eventColor("3") // Màu xanh cho reminder
                .reminderMinutes(Arrays.asList(0)) // Nhắc nhở ngay khi đến giờ
                .build();

        return createEvent(dto);
    }

    // ✅ NEW: Helper methods

    private String buildEventTitle(Task task, CreateCalendarEventRequestDto dto) {
        if (dto.getCustomTitle() != null && !dto.getCustomTitle().isEmpty()) {
            return dto.getCustomTitle() + (Boolean.TRUE.equals(dto.getIsReminder()) ? task.getTitle() : "");
        }

        String prefix = "";
        if ("quick_meeting".equals(dto.getMeetingType())) {
            prefix = "🤝 Meeting: ";
        } else if ("reminder".equals(dto.getMeetingType()) || Boolean.TRUE.equals(dto.getIsReminder())) {
            prefix = "⏰ Nhắc nhở: ";
        } else if (Boolean.TRUE.equals(dto.getCreateMeet())) {
            prefix = "📅 ";
        }

        return prefix + task.getTitle();
    }

    private String buildEventDescription(Task task, CreateCalendarEventRequestDto dto) {
        StringBuilder description = new StringBuilder();

        if (dto.getCustomDescription() != null && !dto.getCustomDescription().isEmpty()) {
            description.append(dto.getCustomDescription()).append("\n\n");
        }

        description.append("📋 Task: ").append(task.getTitle()).append("\n\n");

        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            description.append("📝 Mô tả: ").append(task.getDescription()).append("\n\n");
        }

        if (task.getStatusKey() != null) {
            description.append("📊 Trạng thái: ").append(task.getStatusKey()).append("\n");
        }

        if (task.getPriorityKey() != null) {
            description.append("⚡ Độ ưu tiên: ").append(task.getPriorityKey()).append("\n");
        }

        if (task.getDeadline() != null) {
            description.append("⏰ Deadline: ").append(task.getDeadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n");
        }

        if (dto.getTaskUrl() != null) {
            description.append("\n🔗 Xem task: ").append(dto.getTaskUrl()).append("\n");
        }

        description.append("\n🔗 Được tạo từ TaskFlow System");
        description.append("\n📱 Source: ").append(dto.getSourceApp() != null ? dto.getSourceApp() : "TaskFlow");

        return description.toString();
    }

    private void setEventDateTime(Event event, Task task, CreateCalendarEventRequestDto dto) {
        LocalDateTime startTime;
        LocalDateTime endTime;
        String timezone = dto.getTimezone() != null ? dto.getTimezone() : "Asia/Ho_Chi_Minh";

        // ✅ FIXED: Xác định thời gian bắt đầu với improved parsing
        if (dto.getCustomStartTime() != null) {
            startTime = parseDateTime(dto.getCustomStartTime());
        } else if (task.getStartDate() != null) {
            startTime = task.getStartDate().atTime(9, 0);
        } else {
            startTime = LocalDateTime.now();
        }

        // ✅ FIXED: Xác định thời gian kết thúc với improved parsing
        if (dto.getCustomEndTime() != null) {
            endTime = parseDateTime(dto.getCustomEndTime());
        } else if (dto.getDurationMinutes() != null) {
            endTime = startTime.plusMinutes(dto.getDurationMinutes());
        } else if (task.getDeadline() != null) {
            endTime = task.getDeadline().atTime(18, 0);
        } else {
            endTime = startTime.plusHours(2);
        }

        // Set event times
        if (Boolean.TRUE.equals(dto.getAllDay())) {
            // All-day event
            event.setStart(new EventDateTime().setDate(new DateTime(Date.from(startTime.atZone(ZoneId.systemDefault()).toInstant()))));
            event.setEnd(new EventDateTime().setDate(new DateTime(Date.from(endTime.atZone(ZoneId.systemDefault()).toInstant()))));
        } else {
            // Timed event
            event.setStart(new EventDateTime()
                    .setDateTime(new DateTime(Date.from(startTime.atZone(ZoneId.systemDefault()).toInstant())))
                    .setTimeZone(timezone));
            event.setEnd(new EventDateTime()
                    .setDateTime(new DateTime(Date.from(endTime.atZone(ZoneId.systemDefault()).toInstant())))
                    .setTimeZone(timezone));
        }
    }

    /**
     * ✅ FIXED: Smart datetime parsing to handle multiple formats including ISO 8601 with Z suffix and milliseconds
     * and simple date-only formats
     */
    private LocalDateTime parseDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return LocalDateTime.now();
        }

        try {
            // Handle date-only format (e.g., "2025-09-14")
            if (dateTimeString.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate date = LocalDate.parse(dateTimeString);
                // Use current time of day with the specified date
                LocalTime now = LocalTime.now();
                return LocalDateTime.of(date, now);
            }

            // Handle ISO 8601 format with timezone (e.g., "2025-08-30T12:28:09.992Z")
            if (dateTimeString.endsWith("Z")) {
                // Parse as Instant and convert to LocalDateTime
                java.time.Instant instant = java.time.Instant.parse(dateTimeString);
                return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            }

            // Handle ISO 8601 format with timezone offset (e.g., "2025-08-30T12:28:09.992+07:00")
            if (dateTimeString.contains("+") || dateTimeString.lastIndexOf("-") > 10) {
                java.time.OffsetDateTime offsetDateTime = java.time.OffsetDateTime.parse(dateTimeString);
                return offsetDateTime.toLocalDateTime();
            }

            // Handle datetime with milliseconds (e.g., "2025-08-30T12:28:09.992")
            if (dateTimeString.contains(".")) {
                // Use a more flexible formatter that can handle milliseconds
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS][.SS][.S]");
                return LocalDateTime.parse(dateTimeString, formatter);
            }

            // Handle basic ISO format (e.g., "2025-08-30T12:28:09")
            return LocalDateTime.parse(dateTimeString);

        } catch (Exception e) {
            log.warn("❌ Failed to parse datetime '{}', using current time. Error: {}", dateTimeString, e.getMessage());
            // As a fallback, try to extract just the date and time parts
            try {
                // Try parsing as date-only format in the fallback as well
                if (dateTimeString.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    LocalDate date = LocalDate.parse(dateTimeString);
                    return LocalDateTime.of(date, LocalTime.now());
                }

                // Remove timezone info and try again
                String cleanDateTime = dateTimeString.replaceAll("Z$", "").replaceAll("[+-]\\d{2}:\\d{2}$", "");
                if (cleanDateTime.contains(".")) {
                    // Truncate milliseconds to 3 digits if longer
                    if (cleanDateTime.matches(".*\\.\\d{4,}")) {
                        cleanDateTime = cleanDateTime.replaceAll("(\\.)\\d{3}\\d+", "$1999");
                    }
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]");
                    return LocalDateTime.parse(cleanDateTime, formatter);
                } else {
                    return LocalDateTime.parse(cleanDateTime);
                }
            } catch (Exception fallbackError) {
                log.error("❌ Even fallback parsing failed for '{}': {}", dateTimeString, fallbackError.getMessage());
                return LocalDateTime.now();
            }
        }
    }

    private void addGoogleMeet(Event event) {
        ConferenceSolutionKey conferenceSolutionKey = new ConferenceSolutionKey()
                .setType("hangoutsMeet");
        CreateConferenceRequest createConferenceRequest = new CreateConferenceRequest()
                .setRequestId(java.util.UUID.randomUUID().toString())
                .setConferenceSolutionKey(conferenceSolutionKey);
        ConferenceData conferenceData = new ConferenceData()
                .setCreateRequest(createConferenceRequest);
        event.setConferenceData(conferenceData);
    }

    private void addAttendees(Event event, List<String> attendeeEmails) {
        List<EventAttendee> attendees = attendeeEmails.stream()
                .map(email -> new EventAttendee().setEmail(email))
                .collect(Collectors.toList());
        event.setAttendees(attendees);
    }

    private void addReminders(Event event, List<Integer> reminderMinutes) {
        List<EventReminder> reminders = reminderMinutes.stream()
                .map(minutes -> new EventReminder()
                        .setMethod("popup")
                        .setMinutes(minutes))
                .collect(Collectors.toList());

        Event.Reminders eventReminders = new Event.Reminders()
                .setUseDefault(false)
                .setOverrides(reminders);
        event.setReminders(eventReminders);
    }

    private String extractMeetLink(Event event) {
        if (event.getConferenceData() != null &&
            event.getConferenceData().getEntryPoints() != null &&
            !event.getConferenceData().getEntryPoints().isEmpty()) {
            return event.getConferenceData().getEntryPoints().get(0).getUri();
        }
        return null;
    }

    /**
     * Lấy danh sách sự kiện (placeholder - cần access token từ user context)
     */
    public List<CalendarEventResponseDto> getEvents() {
        // TODO: Implement get events - cần access token từ security context
        return Arrays.asList();
    }

    /**
     * Cập nhật sự kiện Google Calendar
     */
    public CalendarEventResponseDto updateEvent(String eventId, CreateCalendarEventRequestDto dto) {
        try {
            Task task = taskRepository.findById(dto.getTaskId())
                    .orElseThrow(() -> new RuntimeException("Task not found"));

            Calendar service = getCalendarService(dto.getAccessToken());

            Event event = service.events().get("primary", eventId).execute();

            event.setSummary(task.getTitle());
            event.setDescription(buildEventDescription(task, dto));

            // Cập nhật th���i gian
            DateTime startDateTime = getEventStartTime(task);
            DateTime endDateTime = getEventEndTime(task);

            event.getStart().setDateTime(startDateTime);
            event.getEnd().setDateTime(endDateTime);

            event = service.events().update("primary", eventId, event).execute();
            log.info("Event updated: {}", eventId);

            return CalendarEventResponseDto.builder()
                    .eventId(event.getId())
                    .eventUrl(event.getHtmlLink())
                    .message("Sự kiện đã được cập nhật thành công")
                    .build();

        } catch (Exception e) {
            log.error("Error updating calendar event {}: {}", eventId, e.getMessage());
            throw new RuntimeException("Failed to update calendar event", e);
        }
    }

    /**
     * Xóa sự kiện Google Calendar
     */
    public void deleteEvent(String eventId) {
        // TODO: Implement delete event - cần access token từ security context
        log.info("Event delete requested: {}", eventId);
    }

    private Calendar getCalendarService(String accessToken) throws GeneralSecurityException, IOException {
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null))
                .createScoped(Arrays.asList("https://www.googleapis.com/auth/calendar"));

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private String buildEventDescription(Task task) {
        StringBuilder description = new StringBuilder();
        description.append("📋 Task: ").append(task.getTitle()).append("\n\n");

        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            description.append("📝 Mô tả: ").append(task.getDescription()).append("\n\n");
        }

        if (task.getStatusKey() != null) {
            description.append("📊 Trạng thái: ").append(task.getStatusKey()).append("\n");
        }

        if (task.getPriorityKey() != null) {
            description.append("⚡ Độ ưu tiên: ").append(task.getPriorityKey()).append("\n");
        }

        if (task.getDeadline() != null) {
            description.append("⏰ Deadline: ").append(task.getDeadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n");
        }

        description.append("\n🔗 Được tạo từ Task Management System");

        return description.toString();
    }

    private DateTime getEventStartTime(Task task) {
        LocalDateTime startTime;

        if (task.getStartDate() != null) {
            // Nếu có ngày bắt đầu, dùng 9:00 AM
            startTime = task.getStartDate().atTime(9, 0);
        } else {
            // Nếu không có, dùng thời gian hiện tại
            startTime = LocalDateTime.now();
        }

        return new DateTime(Date.from(startTime.atZone(ZoneId.systemDefault()).toInstant()));
    }

    private DateTime getEventEndTime(Task task) {
        LocalDateTime endTime;

        if (task.getDeadline() != null) {
            // Nếu có deadline, dùng 6:00 PM của ngày deadline
            endTime = task.getDeadline().atTime(18, 0);
        } else if (task.getStartDate() != null) {
            // Nếu có ngày bắt đầu nhưng không có deadline, thêm 2 giờ
            endTime = task.getStartDate().atTime(11, 0);
        } else {
            // Mặc định 2 giờ từ thời gian hiện tại
            endTime = LocalDateTime.now().plusHours(2);
        }

        return new DateTime(Date.from(endTime.atZone(ZoneId.systemDefault()).toInstant()));
    }

    /**
     * ✅ NEW: Tạo sự kiện Google Calendar cho ProjectTask
     */
    public CalendarEventResponseDto createProjectTaskEvent(CreateCalendarEventRequestDto dto) {
        try {
            ProjectTask projectTask = projectTaskRepository.findById(dto.getTaskId())
                    .orElseThrow(() -> new RuntimeException("ProjectTask not found"));

            Calendar service = getCalendarService(dto.getAccessToken());

            // Tạo sự kiện với title và description
            Event event = new Event()
                    .setSummary(buildProjectTaskEventTitle(projectTask, dto))
                    .setDescription(buildProjectTaskEventDescription(projectTask, dto));

            // ✅ Set thời gian bắt đầu và kết thúc (enhanced)
            setProjectTaskEventDateTime(event, projectTask, dto);

            // ✅ Add Google Meet nếu được yêu cầu
            if (Boolean.TRUE.equals(dto.getCreateMeet())) {
                addGoogleMeet(event);
            }

            // ✅ Add attendees nếu có
            if (dto.getAttendeeEmails() != null && !dto.getAttendeeEmails().isEmpty()) {
                addAttendees(event, dto.getAttendeeEmails());
            }

            // ✅ Set location nếu có
            if (dto.getLocation() != null && !dto.getLocation().isEmpty()) {
                event.setLocation(dto.getLocation());
            }

            // ✅ Set reminder/notification
            if (dto.getReminderMinutes() != null && !dto.getReminderMinutes().isEmpty()) {
                addReminders(event, dto.getReminderMinutes());
            }

            // ✅ Set event color
            if (dto.getEventColor() != null) {
                event.setColorId(dto.getEventColor());
            }

            // ✅ Set visibility
            if (dto.getVisibility() != null) {
                event.setVisibility(dto.getVisibility());
            }

            // ✅ Set transparency (busy/free)
            if (dto.getTransparency() != null) {
                event.setTransparency(dto.getTransparency());
            }

            // ✅ Add recurrence rule nếu có
            if (Boolean.TRUE.equals(dto.getIsRecurring()) && dto.getRecurrenceRule() != null) {
                event.setRecurrence(Arrays.asList(dto.getRecurrenceRule()));
            }

            // Tạo sự kiện trên calendar
            String calendarId = dto.getCalendarId() != null ? dto.getCalendarId() : "primary";
            event = service.events().insert(calendarId, event)
                    .setConferenceDataVersion(Boolean.TRUE.equals(dto.getCreateMeet()) ? 1 : 0)
                    .execute();

            // Lấy meet link từ conference data
            String meetLink = extractMeetLink(event);

            // ✅ ENHANCED: Lưu đầy đủ thông tin Google Calendar vào ProjectTask
            projectTask.setGoogleCalendarEventId(event.getId());
            projectTask.setGoogleCalendarEventUrl(event.getHtmlLink());
            projectTask.setGoogleMeetLink(meetLink);
            projectTask.setIsSyncedToCalendar(true);
            projectTask.setCalendarSyncedAt(LocalDateTime.now());
            projectTaskRepository.save(projectTask);

            log.info("✅ Event created successfully: {}", event.getHtmlLink());

            return CalendarEventResponseDto.builder()
                    .eventId(event.getId())
                    .eventUrl(event.getHtmlLink())
                    .meetLink(meetLink)
                    .title(event.getSummary())
                    .startTime(event.getStart().getDateTime().toString())
                    .endTime(event.getEnd().getDateTime().toString())
                    .attendeeCount(dto.getAttendeeEmails() != null ? dto.getAttendeeEmails().size() : 0)
                    .hasReminders(dto.getReminderMinutes() != null && !dto.getReminderMinutes().isEmpty())
                    .isRecurring(Boolean.TRUE.equals(dto.getIsRecurring()))
                    .message("Sự kiện Google Calendar đã được tạo thành công cho Project Task" +
                            (meetLink != null ? " với Google Meet" : ""))
                    .build();

        } catch (Exception e) {
            log.error("❌ Error creating calendar event for project task {}: {}", dto.getTaskId(), e.getMessage());
            throw new RuntimeException("Failed to create calendar event: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NEW: Tạo sự kiện Google Calendar cho ProjectTask với user token
     */
    public CalendarEventResponseDto createProjectTaskEventWithUserToken(CreateCalendarEventRequestDto dto, String userEmail) {
        try {
            // Tự động lấy Google access token từ user đã đăng nhập
            String accessToken = getGoogleAccessTokenForUser(userEmail);
            dto.setAccessToken(accessToken);

            return createProjectTaskEvent(dto);
        } catch (Exception e) {
            log.error("❌ Error creating project task calendar event with user token for user {}: {}", userEmail, e.getMessage());
            throw new RuntimeException("Failed to create calendar event: " + e.getMessage(), e);
        }
    }

    // ✅ NEW: Helper methods cho ProjectTask

    private String buildProjectTaskEventTitle(ProjectTask projectTask, CreateCalendarEventRequestDto dto) {
        if (dto.getCustomTitle() != null && !dto.getCustomTitle().isEmpty()) {
            return dto.getCustomTitle() + (Boolean.TRUE.equals(dto.getIsReminder()) ? projectTask.getTitle() : "");
        }

        String prefix = "";
        if ("quick_meeting".equals(dto.getMeetingType())) {
            prefix = "🤝 Meeting: ";
        } else if ("reminder".equals(dto.getMeetingType()) || Boolean.TRUE.equals(dto.getIsReminder())) {
            prefix = "⏰ Nhắc nhở: ";
        } else if (Boolean.TRUE.equals(dto.getCreateMeet())) {
            prefix = "📅 ";
        }

        return prefix + projectTask.getTitle();
    }

    private String buildProjectTaskEventDescription(ProjectTask projectTask, CreateCalendarEventRequestDto dto) {
        StringBuilder description = new StringBuilder();

        if (dto.getCustomDescription() != null && !dto.getCustomDescription().isEmpty()) {
            description.append(dto.getCustomDescription()).append("\n\n");
        }

        description.append("📋 Project Task: ").append(projectTask.getTitle()).append("\n\n");

        if (projectTask.getDescription() != null && !projectTask.getDescription().isEmpty()) {
            description.append("📝 Mô tả: ").append(projectTask.getDescription()).append("\n\n");
        }

        if (projectTask.getStatus() != null) {
            description.append("📊 Trạng thái: ").append(projectTask.getStatus()).append("\n");
        }

        if (projectTask.getPriority() != null) {
            description.append("⚡ Độ ưu tiên: ").append(projectTask.getPriority()).append("\n");
        }

        if (projectTask.getDeadline() != null) {
            description.append("⏰ Deadline: ").append(projectTask.getDeadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n");
        }

        if (projectTask.getProject() != null) {
            description.append("📁 Project: ").append(projectTask.getProject().getName()).append("\n");
        }

        if (projectTask.getAssignee() != null) {
            String assigneeName = getFullName(projectTask.getAssignee());
            description.append("👤 Assignee: ").append(assigneeName).append("\n");
        }

        if (dto.getTaskUrl() != null) {
            description.append("\n🔗 Xem task: ").append(dto.getTaskUrl()).append("\n");
        }

        description.append("\n🔗 Được tạo từ TaskFlow Project Management");
        description.append("\n📱 Source: ").append(dto.getSourceApp() != null ? dto.getSourceApp() : "TaskFlow Projects");

        return description.toString();
    }

    private void setProjectTaskEventDateTime(Event event, ProjectTask projectTask, CreateCalendarEventRequestDto dto) {
        LocalDateTime startTime;
        LocalDateTime endTime;
        String timezone = dto.getTimezone() != null ? dto.getTimezone() : "Asia/Ho_Chi_Minh";

        // ✅ FIXED: Xác định thời gian bắt đầu với improved parsing
        if (dto.getCustomStartTime() != null) {
            startTime = parseDateTime(dto.getCustomStartTime());
        } else if (projectTask.getStartDate() != null) {
            startTime = projectTask.getStartDate().atTime(9, 0);
        } else {
            startTime = LocalDateTime.now();
        }

        // ✅ FIXED: Xác định thời gian kết thúc với improved parsing
        if (dto.getCustomEndTime() != null) {
            endTime = parseDateTime(dto.getCustomEndTime());
        } else if (dto.getDurationMinutes() != null) {
            endTime = startTime.plusMinutes(dto.getDurationMinutes());
        } else if (projectTask.getDeadline() != null) {
            endTime = projectTask.getDeadline().atTime(18, 0);
        } else {
            endTime = startTime.plusHours(2);
        }

        // Set event times
        if (Boolean.TRUE.equals(dto.getAllDay())) {
            // All-day event
            event.setStart(new EventDateTime().setDate(new DateTime(Date.from(startTime.atZone(ZoneId.systemDefault()).toInstant()))));
            event.setEnd(new EventDateTime().setDate(new DateTime(Date.from(endTime.atZone(ZoneId.systemDefault()).toInstant()))));
        } else {
            // Timed event
            event.setStart(new EventDateTime()
                    .setDateTime(new DateTime(Date.from(startTime.atZone(ZoneId.systemDefault()).toInstant())))
                    .setTimeZone(timezone));
            event.setEnd(new EventDateTime()
                    .setDateTime(new DateTime(Date.from(endTime.atZone(ZoneId.systemDefault()).toInstant())))
                    .setTimeZone(timezone));
        }
    }

    private String getFullName(com.example.taskmanagement_backend.entities.User user) {
        if (user.getUserProfile() != null) {
            String firstName = user.getUserProfile().getFirstName();
            String lastName = user.getUserProfile().getLastName();
            if (firstName != null || lastName != null) {
                return String.format("%s %s",
                        firstName != null ? firstName : "",
                        lastName != null ? lastName : "").trim();
            }
        }
        return user.getEmail();
    }
}
