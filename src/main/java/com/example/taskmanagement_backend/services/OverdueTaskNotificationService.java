package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.NotificationDto.CreateNotificationRequestDto;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.TaskAssignee;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.NotificationType;
import com.example.taskmanagement_backend.repositories.TaskJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service để tự động tạo thông báo cho các task quá hạn
 * Chạy định kỳ để kiểm tra và thông báo cho user
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OverdueTaskNotificationService {

    private final TaskJpaRepository taskRepository;
    private final NotificationService notificationService;

    // 🚨 OPTIMIZATION: Flag để track đã chạy check overdue chưa hôm nay
    private volatile boolean hasCheckedOverdueToday = false;
    private volatile LocalDate lastOverdueCheckDate = null;

    /**
     * Chạy mỗi ngày lúc 9h sáng để kiểm tra task quá hạn và tạo thông báo
     * PRODUCTION: Chạy 1 lần mỗi ngày vào 9h sáng
     * + Chạy lần đầu ngay sau khi khởi động server (sau 30 giây)
     */
    @Scheduled(cron = "0 0 9 * * *") // 9:00 AM mỗi ngày
    @Transactional
    public void checkAndNotifyOverdueTasks() {
        try {
            LocalDate today = LocalDate.now();

            // 🚨 OPTIMIZATION: Kiểm tra đã chạy hôm nay chưa để tránh duplicate
            if (hasCheckedOverdueToday && today.equals(lastOverdueCheckDate)) {
                log.debug("⏭️ Already checked overdue tasks today ({}), skipping", today);
                return;
            }

            log.info("🔍 Starting overdue task notification check...");
            log.info("📅 Today is: {}", today);

            // Lấy task quá hạn thông qua repository query tối ưu
            List<Task> overdueTasks = taskRepository.findOverdueTasks(today);
            log.info("📋 Found {} overdue tasks", overdueTasks.size());

            if (overdueTasks.isEmpty()) {
                log.info("✅ No overdue tasks found");
                // Cập nhật flag đã check
                hasCheckedOverdueToday = true;
                lastOverdueCheckDate = today;
                return;
            }

            // 🚨 FIX: Nhóm tasks theo user (bao gồm cả creator và assignees)
            java.util.HashMap<User, List<Task>> tasksByUser = new java.util.HashMap<>();

            for (Task task : overdueTasks) {
                // Thêm creator vào danh sách nhận thông báo
                if (task.getCreator() != null) {
                    tasksByUser.computeIfAbsent(task.getCreator(), k -> new ArrayList<>()).add(task);
                }

                // Thêm tất cả assignees vào danh sách nhận thông báo
                if (task.getAssignees() != null) {
                    for (var assignee : task.getAssignees()) {
                        if (assignee.getUser() != null) {
                            tasksByUser.computeIfAbsent(assignee.getUser(), k -> new ArrayList<>()).add(task);
                        }
                    }
                }
            }

            log.info("👥 Will notify {} users about overdue tasks", tasksByUser.size());

            // Tạo thông báo cho từng user
            for (Map.Entry<User, List<Task>> entry : tasksByUser.entrySet()) {
                User user = entry.getKey();
                List<Task> userOverdueTasks = entry.getValue();

                log.info("📨 Creating notification for user {} with {} overdue tasks",
                    user.getEmail(), userOverdueTasks.size());

                createOverdueNotificationForUser(user, userOverdueTasks);
            }

            // Cập nhật flag đã check thành công
            hasCheckedOverdueToday = true;
            lastOverdueCheckDate = today;

            log.info("✅ Completed overdue task notification check. Notified {} users", tasksByUser.size());

        } catch (Exception e) {
            log.error("❌ Error during overdue task notification check: {}", e.getMessage(), e);
        }
    }

    /**
     * 🚀 STARTUP CHECK: Chạy lần đầu sau khi khởi động server (30 giây sau startup)
     * Để đảm bảo luôn có thông báo overdue ngay khi server start
     */
    @Scheduled(initialDelay = 30000, fixedDelay = Long.MAX_VALUE) // Chỉ chạy 1 lần sau 30s
    @Transactional
    public void checkOverdueTasksOnStartup() {
        log.info("🚀 STARTUP: Running initial overdue task check after server startup...");

        LocalDate today = LocalDate.now();

        // Reset flag để force check lần đầu
        hasCheckedOverdueToday = false;
        lastOverdueCheckDate = null;

        // Chạy check overdue
        checkAndNotifyOverdueTasks();

        log.info("✅ STARTUP: Completed initial overdue task check");
    }

    /**
     * Tạo thông báo quá hạn cho một user cụ thể
     */
    private void createOverdueNotificationForUser(User user, List<Task> overdueTasks) {
        try {
            // 🚨 FIX: Kiểm tra xem đã tạo thông báo quá hạn cho user này hôm nay chưa
            if (hasOverdueNotificationToday(user.getId())) {
                log.debug("⏭️ User {} already has overdue notification today, skipping", user.getEmail());
                return;
            }

            String title;
            String content;

            if (overdueTasks.size() == 1) {
                Task task = overdueTasks.get(0);
                title = "⚠️ Task quá hạn cần xử lý";

                // 🚨 FIX: Kiểm tra null và cung cấp giá trị mặc định
                String taskTitle = task.getTitle() != null ? task.getTitle() : "Không có tiêu đề";
                String deadlineStr = task.getDeadline() != null ?
                    task.getDeadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) :
                    "không xác định";

                content = String.format("Task \"%s\" đã quá hạn từ ngày %s. Vui lòng kiểm tra và cập nhật tiến độ!",
                    taskTitle, deadlineStr);

                log.info("📝 Creating single task notification: '{}' - deadline: {}", taskTitle, deadlineStr);
            } else {
                title = String.format("⚠️ Bạn có %d task quá hạn", overdueTasks.size());

                // 🚨 FIX: Tạo nội dung chi tiết với danh sách task
                StringBuilder taskDetails = new StringBuilder();
                taskDetails.append(String.format("Bạn có %d task đã quá hạn:\n\n", overdueTasks.size()));

                // Sắp xếp task theo deadline để hiển thị task quá hạn lâu nhất trước
                List<Task> sortedTasks = overdueTasks.stream()
                    .filter(task -> task.getDeadline() != null)
                    .sorted((t1, t2) -> t1.getDeadline().compareTo(t2.getDeadline()))
                    .collect(Collectors.toList());

                for (int i = 0; i < Math.min(sortedTasks.size(), 5); i++) { // Hiển thị tối đa 5 task
                    Task task = sortedTasks.get(i);
                    String taskTitle = task.getTitle() != null ? task.getTitle() : "Không có tiêu đề";
                    String deadlineStr = task.getDeadline() != null ?
                        task.getDeadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) :
                        "không xác định";

                    // Tính số ngày quá hạn
                    long daysOverdue = task.getDeadline() != null ?
                        LocalDate.now().toEpochDay() - task.getDeadline().toEpochDay() : 0;

                    taskDetails.append(String.format("• \"%s\" (quá hạn %d ngày - từ %s)\n",
                        taskTitle, daysOverdue, deadlineStr));
                }

                if (overdueTasks.size() > 5) {
                    taskDetails.append(String.format("\n... và %d task khác nữa", overdueTasks.size() - 5));
                }

                taskDetails.append("\nVui lòng kiểm tra và cập nhật tiến độ công việc!");

                content = taskDetails.toString();

                log.info("📝 Creating multiple tasks notification for {} tasks", overdueTasks.size());
            }

            // Lấy task quá hạn lâu nhất để làm reference
            Task oldestOverdueTask = overdueTasks.stream()
                .filter(task -> task.getDeadline() != null) // Chỉ lấy task có deadline
                .min((t1, t2) -> t1.getDeadline().compareTo(t2.getDeadline()))
                .orElse(overdueTasks.get(0));

            // 🚨 FIX: Tạo metadata với thông tin đầy đủ và kiểm tra null
            Map<String, String> metadata = Map.of(
                "overdueTaskCount", String.valueOf(overdueTasks.size()),
                "oldestOverdueDate", oldestOverdueTask.getDeadline() != null ?
                    oldestOverdueTask.getDeadline().toString() : LocalDate.now().toString(),
                "taskTitles", overdueTasks.stream()
                    .limit(3) // Chỉ lấy 3 task đầu để không quá dài
                    .map(task -> task.getTitle() != null ? task.getTitle() : "Không có tiêu đề")
                    .collect(Collectors.joining(", ")),
                "urgency", "high",
                "taskIds", overdueTasks.stream()
                    .map(task -> String.valueOf(task.getId()))
                    .limit(10)
                    .collect(Collectors.joining(","))
            );

            CreateNotificationRequestDto notificationRequest = CreateNotificationRequestDto.builder()
                .userId(user.getId())
                .title(title)
                .content(content)
                .type(NotificationType.TASK_OVERDUE)
                .referenceId(oldestOverdueTask.getId())
                .referenceType("TASK_OVERDUE")
                .priority(2) // Urgent priority
                .metadata(metadata)
                .expiresInHours(24) // Hết hạn sau 24h
                .build();

            // Gửi thông báo bất ��ồng bộ
            notificationService.createAndSendNotification(notificationRequest);

            log.info("📨 Sent overdue notification to user {} for {} tasks",
                user.getEmail(), overdueTasks.size());

        } catch (Exception e) {
            log.error("❌ Error creating overdue notification for user {}: {}",
                user.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra xem user đã nhận thông báo quá hạn hôm nay chưa
     * Để tránh spam thông báo
     */
    private boolean hasOverdueNotificationToday(Long userId) {
        try {
            LocalDate today = LocalDate.now();

            // Kiểm tra trong database xem đã có notification TASK_OVERDUE cho user hôm nay chưa
            return notificationService.hasNotificationToday(userId, NotificationType.TASK_OVERDUE, today);
        } catch (Exception e) {
            log.error("Error checking overdue notification for user {}: {}", userId, e.getMessage());
            // Nếu có lỗi, cho phép gửi thông báo để đảm bảo user không bị miss
            return false;
        }
    }

    /**
     * Chạy ngay lập tức để kiểm tra task quá hạn (dùng cho testing hoặc manual trigger)
     */
    public void checkOverdueTasksNow() {
        log.info("🚀 Manual trigger: Checking overdue tasks immediately...");

        // Reset flag để force check
        hasCheckedOverdueToday = false;
        lastOverdueCheckDate = null;

        checkAndNotifyOverdueTasks();
    }

    /**
     * Tạo thông báo nhắc nhở trước khi task quá hạn (chạy hàng ngày lúc 8h sáng)
     * Cron: 0 0 8 * * * = 8:00 AM mỗi ngày
     */
    @Scheduled(cron = "0 0 8 * * *") // 8:00 AM mỗi ngày
    @Transactional(readOnly = true)
    public void checkAndNotifyDueSoonTasks() {
        try {
            log.info("🔔 Starting due soon task notification check...");

            // Lấy task sẽ đến hạn trong 1-2 ngày tới
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            LocalDate dayAfterTomorrow = LocalDate.now().plusDays(2);

            List<Task> dueSoonTasks = taskRepository.findTasksDueBetween(tomorrow, dayAfterTomorrow);

            if (dueSoonTasks.isEmpty()) {
                log.info("✅ No tasks due soon found");
                return;
            }

            log.info("📅 Found {} tasks due soon", dueSoonTasks.size());

            // Nhóm tasks theo user để gửi thông báo
            java.util.HashMap<User, List<Task>> tasksByUser = new java.util.HashMap<>();

            for (Task task : dueSoonTasks) {
                // Thêm creator
                if (task.getCreator() != null) {
                    tasksByUser.computeIfAbsent(task.getCreator(), k -> new ArrayList<>()).add(task);
                }

                // Thêm assignees
                if (task.getAssignees() != null) {
                    for (var assignee : task.getAssignees()) {
                        if (assignee.getUser() != null) {
                            tasksByUser.computeIfAbsent(assignee.getUser(), k -> new ArrayList<>()).add(task);
                        }
                    }
                }
            }

            // Tạo thông báo cho từng user
            for (Map.Entry<User, List<Task>> entry : tasksByUser.entrySet()) {
                User user = entry.getKey();
                List<Task> userDueSoonTasks = entry.getValue();

                createDueSoonNotificationForUser(user, userDueSoonTasks);
            }

            log.info("✅ Completed due soon task notification check. Notified {} users", tasksByUser.size());

        } catch (Exception e) {
            log.error("❌ Error during due soon task notification check: {}", e.getMessage(), e);
        }
    }

    /**
     * Tạo thông báo sắp đến hạn cho user
     */
    private void createDueSoonNotificationForUser(User user, List<Task> dueSoonTasks) {
        try {
            String title;
            String content;

            if (dueSoonTasks.size() == 1) {
                Task task = dueSoonTasks.get(0);
                title = "⏰ Task sắp đến hạn";

                String taskTitle = task.getTitle() != null ? task.getTitle() : "Không có tiêu đề";
                String deadlineStr = task.getDeadline() != null ?
                    task.getDeadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) :
                    "không xác định";

                content = String.format("Task \"%s\" sẽ đến hạn vào ngày %s. Hãy chuẩn bị hoàn thành!",
                    taskTitle, deadlineStr);
            } else {
                title = String.format("⏰ Bạn có %d task sắp đến hạn", dueSoonTasks.size());

                StringBuilder taskDetails = new StringBuilder();
                taskDetails.append(String.format("Bạn có %d task sẽ đến hạn trong 1-2 ngày tới:\n\n", dueSoonTasks.size()));

                for (int i = 0; i < Math.min(dueSoonTasks.size(), 3); i++) {
                    Task task = dueSoonTasks.get(i);
                    String taskTitle = task.getTitle() != null ? task.getTitle() : "Không có tiêu đề";
                    String deadlineStr = task.getDeadline() != null ?
                        task.getDeadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) :
                        "không xác định";

                    taskDetails.append(String.format("• \"%s\" (đến hạn %s)\n", taskTitle, deadlineStr));
                }

                if (dueSoonTasks.size() > 3) {
                    taskDetails.append(String.format("... và %d task khác\n", dueSoonTasks.size() - 3));
                }

                taskDetails.append("\nHãy chuẩn bị hoàn thành!");
                content = taskDetails.toString();
            }

            Task nearestTask = dueSoonTasks.stream()
                .filter(task -> task.getDeadline() != null)
                .min((t1, t2) -> t1.getDeadline().compareTo(t2.getDeadline()))
                .orElse(dueSoonTasks.get(0));

            Map<String, String> metadata = Map.of(
                "dueSoonTaskCount", String.valueOf(dueSoonTasks.size()),
                "nearestDueDate", nearestTask.getDeadline() != null ?
                    nearestTask.getDeadline().toString() : LocalDate.now().toString(),
                "taskTitles", dueSoonTasks.stream()
                    .limit(3)
                    .map(task -> task.getTitle() != null ? task.getTitle() : "Không có tiêu đề")
                    .collect(Collectors.joining(", ")),
                "urgency", "medium"
            );

            CreateNotificationRequestDto notificationRequest = CreateNotificationRequestDto.builder()
                .userId(user.getId())
                .title(title)
                .content(content)
                .type(NotificationType.TASK_DUE_SOON)
                .referenceId(nearestTask.getId())
                .referenceType("TASK_DUE_SOON")
                .priority(1) // High priority
                .metadata(metadata)
                .expiresInHours(48) // Hết hạn sau 48h
                .build();

            notificationService.createAndSendNotification(notificationRequest);

            log.info("📨 Sent due soon notification to user {} for {} tasks",
                user.getEmail(), dueSoonTasks.size());

        } catch (Exception e) {
            log.error("❌ Error creating due soon notification for user {}: {}",
                user.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra xem task có được coi là hoàn thành hay không
     * Kiểm tra cả statusKey (string) và status (enum) để xác định
     */
    private boolean isTaskCompleted(Task task) {
        // Kiểm tra statusKey (nếu có) - đây là cách mới dùng string
        if (task.getStatusKey() != null) {
            String statusKey = task.getStatusKey().toUpperCase();
            return statusKey.equals("COMPLETED") || statusKey.equals("DONE") || statusKey.equals("CANCELLED");
        }

        // Kiểm tra status enum (legacy) - để backward compatibility
        if (task.getStatus() != null) {
            String statusString = task.getStatus().toString().toUpperCase();
            return statusString.equals("COMPLETED") || statusString.equals("DONE") || statusString.equals("CANCELLED");
        }

        // Nếu không có status nào, coi như task chưa hoàn thành
        return false;
    }
}
