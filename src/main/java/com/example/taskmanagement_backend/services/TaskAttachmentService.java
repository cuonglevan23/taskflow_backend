package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.TaskAttachmentDto.TaskAttachmentResponseDto;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.TaskAttachment;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.SystemRole;
import com.example.taskmanagement_backend.repositories.TaskAttachmentRepository;
import com.example.taskmanagement_backend.repositories.TaskRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAttachmentService {

    private final TaskAttachmentRepository taskAttachmentRepository;
    private final TaskRepository taskRepository;
    private final S3FileUploadService s3FileUploadService;
    private final UserJpaRepository userRepository;

    /**
     * Lấy danh sách tất cả file của một task
     */
    @Transactional(readOnly = true)
    public List<TaskAttachmentResponseDto> getTaskAttachments(Long taskId) {
        log.info("📋 Getting attachments for task: {}", taskId);

        // Kiểm tra task có tồn tại không
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));

        List<TaskAttachment> attachments = taskAttachmentRepository.findByTaskIdAndNotDeleted(taskId);

        return attachments.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Lưu thông tin file attachment sau khi upload thành công
     */
    @Transactional
    public TaskAttachmentResponseDto saveAttachment(Long taskId, String fileKey, String originalFilename,
                                                   Long fileSize, String contentType, String downloadUrl) {
        log.info("💾 Saving attachment for task: {} - file: {}", taskId, originalFilename);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));

        User currentUser = getCurrentUser();

        TaskAttachment attachment = TaskAttachment.builder()
                .task(task)
                .fileKey(fileKey)
                .originalFilename(originalFilename)
                .fileSize(fileSize)
                .contentType(contentType)
                .downloadUrl(downloadUrl)
                .uploadedBy(currentUser)
                .isDeleted(false)
                .build();

        TaskAttachment savedAttachment = taskAttachmentRepository.save(attachment);
        log.info("✅ Attachment saved successfully with id: {}", savedAttachment.getId());

        return convertToResponseDto(savedAttachment);
    }

    /**
     * 🔧 NEW: Lưu thông tin file attachment với user email (dùng cho async operations)
     */
    @Transactional
    public TaskAttachmentResponseDto saveAttachmentWithUser(Long taskId, String fileKey, String originalFilename,
                                                           Long fileSize, String contentType, String downloadUrl, String userEmail) {
        log.info("💾 Saving attachment for task: {} - file: {} - user: {}", taskId, originalFilename, userEmail);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));

        TaskAttachment attachment = TaskAttachment.builder()
                .task(task)
                .fileKey(fileKey)
                .originalFilename(originalFilename)
                .fileSize(fileSize)
                .contentType(contentType)
                .downloadUrl(downloadUrl)
                .uploadedBy(user)
                .isDeleted(false)
                .build();

        TaskAttachment savedAttachment = taskAttachmentRepository.save(attachment);
        log.info("✅ Attachment saved successfully with id: {}", savedAttachment.getId());

        return convertToResponseDto(savedAttachment);
    }

    /**
     * Xóa file attachment (soft delete)
     */
    @Transactional
    public boolean deleteAttachment(Long attachmentId) {
        log.info("🗑️ Deleting attachment: {}", attachmentId);

        TaskAttachment attachment = taskAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));

        // Kiểm tra quyền xóa (chỉ người upload hoặc admin mới được xóa)
        User currentUser = getCurrentUser();
        if (!attachment.getUploadedBy().getId().equals(currentUser.getId()) &&
            !isAdmin(currentUser)) {
            throw new RuntimeException("You don't have permission to delete this attachment");
        }

        // Soft delete
        attachment.setIsDeleted(true);
        attachment.setUpdatedAt(LocalDateTime.now());
        taskAttachmentRepository.save(attachment);

        // Xóa file khỏi S3 (optional - có thể giữ lại để backup)
        try {
            s3FileUploadService.deleteFile(attachment.getFileKey());
            log.info("✅ File deleted from S3: {}", attachment.getFileKey());
        } catch (Exception e) {
            log.warn("⚠️ Failed to delete file from S3: {}", attachment.getFileKey(), e);
        }

        return true;
    }

    /**
     * Tạo download URL mới cho file (refresh expired URL)
     */
    public String generateNewDownloadUrl(Long attachmentId) {
        log.info("🔗 Generating new download URL for attachment: {}", attachmentId);

        TaskAttachment attachment = taskAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found with id: " + attachmentId));

        String newDownloadUrl = s3FileUploadService.generatePresignedDownloadUrl(
                attachment.getFileKey(), Duration.ofDays(7));

        // Cập nhật URL mới vào database
        attachment.setDownloadUrl(newDownloadUrl);
        attachment.setUpdatedAt(LocalDateTime.now());
        taskAttachmentRepository.save(attachment);

        return newDownloadUrl;
    }

    /**
     * Lấy thống kê file của task
     */
    @Transactional(readOnly = true)
    public TaskAttachmentStatsDto getAttachmentStats(Long taskId) {
        Long totalFiles = taskAttachmentRepository.countByTaskIdAndNotDeleted(taskId);
        Long totalSize = taskAttachmentRepository.calculateTotalFileSizeByTaskId(taskId);

        return TaskAttachmentStatsDto.builder()
                .taskId(taskId)
                .totalFiles(totalFiles)
                .totalSize(totalSize)
                .totalSizeFormatted(formatFileSize(totalSize))
                .build();
    }

    /**
     * ✅ NEW: Delete all attachments for a task (used during task deletion)
     * This method is called when deleting a task to clean up all associated file attachments
     */
    @Transactional
    public void deleteAllAttachmentsForTask(Long taskId) {
        log.info("🗑️ Deleting all attachments for task: {}", taskId);

        try {
            // Use bulk delete to remove all attachment records from database
            int deletedCount = taskAttachmentRepository.deleteByTaskId(taskId);
            log.info("✅ Deleted {} attachment records for task {}", deletedCount, taskId);
        } catch (Exception e) {
            log.error("❌ Failed to delete attachment records for task {}: {}", taskId, e.getMessage());
            throw new RuntimeException("Failed to delete task attachments: " + e.getMessage(), e);
        }
    }

    // Helper methods
    private TaskAttachmentResponseDto convertToResponseDto(TaskAttachment attachment) {
        return TaskAttachmentResponseDto.builder()
                .id(attachment.getId())
                .taskId(attachment.getTask().getId())
                .fileKey(attachment.getFileKey())
                .originalFilename(attachment.getOriginalFilename())
                .fileSize(attachment.getFileSize())
                .contentType(attachment.getContentType())
                .downloadUrl(attachment.getDownloadUrl())
                .uploadedBy(attachment.getUploadedBy().getUserProfile().getFirstName() + " " +
                           attachment.getUploadedBy().getUserProfile().getLastName())
                .uploadedByEmail(attachment.getUploadedBy().getEmail())
                .createdAt(attachment.getCreatedAt())
                .updatedAt(attachment.getUpdatedAt())
                .build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new SecurityException("User not found: " + authentication.getName()));
        }
        throw new SecurityException("User not authenticated");
    }

    private boolean isAdmin(User user) {
        return user.getSystemRole() != null && user.getSystemRole() == SystemRole.ADMIN;
    }

    private String formatFileSize(Long fileSize) {
        if (fileSize == null) return "0 B";

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = fileSize.doubleValue();

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.1f %s", size, units[unitIndex]);
    }

    // Inner DTO class for stats
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TaskAttachmentStatsDto {
        private Long taskId;
        private Long totalFiles;
        private Long totalSize;
        private String totalSizeFormatted;
    }
}
