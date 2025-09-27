package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.entities.Note;
import com.example.taskmanagement_backend.entities.NoteAttachment;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.NoteAttachmentRepository;
import com.example.taskmanagement_backend.repositories.NoteRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final NoteAttachmentRepository noteAttachmentRepository;
    private final NoteRepository noteRepository;
    private final UserJpaRepository userRepository;
    private final NoteService noteService; // For permission checking

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.max-file-size:10485760}") // 10MB default
    private long maxFileSize;

    @Value("${app.upload.allowed-types:image/jpeg,image/png,image/gif,image/webp,application/pdf,text/plain,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document}")
    private String allowedTypes;

    /**
     * Upload file to note
     */
    @Transactional
    public NoteAttachment uploadFileToNote(Long noteId, MultipartFile file, String description) {
        // Validate file
        validateFile(file);

        // Check if user can access the note
        User currentUser = getCurrentUser();
        noteService.getNoteById(noteId); // This will throw exception if user can't access

        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException("Note not found"));

        try {
            // Create upload directory if not exists
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String storedFileName = UUID.randomUUID().toString() + fileExtension;

            // Save file to disk
            Path targetLocation = uploadPath.resolve(storedFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Create attachment record
            NoteAttachment attachment = NoteAttachment.builder()
                    .fileName(originalFilename)
                    .storedFileName(storedFileName)
                    .filePath(targetLocation.toString())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .description(description)
                    .note(note)
                    .uploadedBy(currentUser)
                    .build();

            NoteAttachment savedAttachment = noteAttachmentRepository.save(attachment);

            log.info("✅ [FileUploadService] Uploaded file: {} to note: {} by user: {}",
                     originalFilename, noteId, currentUser.getEmail());

            return savedAttachment;

        } catch (IOException e) {
            log.error("❌ [FileUploadService] Error uploading file: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file: " + e.getMessage());
        }
    }

    /**
     * Download file by attachment ID
     */
    public Resource downloadFile(Long attachmentId) {
        User currentUser = getCurrentUser();

        // Check if user can access this attachment
        NoteAttachment attachment = noteAttachmentRepository.findAttachmentAccessibleByUser(attachmentId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found or access denied"));

        try {
            Path filePath = Paths.get(attachment.getFilePath()).toAbsolutePath().normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                return resource;
            } else {
                throw new EntityNotFoundException("File not found on server");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error downloading file: " + e.getMessage());
        }
    }

    /**
     * Get attachments for a note
     */
    @Transactional(readOnly = true)
    public List<NoteAttachment> getNoteAttachments(Long noteId) {
        // Check if user can access the note
        noteService.getNoteById(noteId); // This will throw exception if user can't access

        return noteAttachmentRepository.findByNoteIdOrderByCreatedAtDesc(noteId);
    }

    /**
     * Delete attachment
     */
    @Transactional
    public void deleteAttachment(Long attachmentId) {
        User currentUser = getCurrentUser();

        NoteAttachment attachment = noteAttachmentRepository.findAttachmentAccessibleByUser(attachmentId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found or access denied"));

        // Check if user can delete (must be uploader, note creator, or admin)
        if (!canUserDeleteAttachment(currentUser, attachment)) {
            throw new AccessDeniedException("You don't have permission to delete this attachment");
        }

        try {
            // Delete file from disk
            Path filePath = Paths.get(attachment.getFilePath());
            Files.deleteIfExists(filePath);

            // Delete attachment record
            noteAttachmentRepository.delete(attachment);

            log.info("✅ [FileUploadService] Deleted attachment: {} by user: {}",
                     attachment.getFileName(), currentUser.getEmail());

        } catch (IOException e) {
            log.error("❌ [FileUploadService] Error deleting file: {}", e.getMessage());
            // Continue with database deletion even if file deletion fails
            noteAttachmentRepository.delete(attachment);
        }
    }

    /**
     * Get attachment info by ID
     */
    @Transactional(readOnly = true)
    public NoteAttachment getAttachmentById(Long attachmentId) {
        User currentUser = getCurrentUser();

        return noteAttachmentRepository.findAttachmentAccessibleByUser(attachmentId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found or access denied"));
    }

    // Helper methods
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size: " + formatFileSize(maxFileSize));
        }

        String contentType = file.getContentType();
        if (contentType == null || !isAllowedContentType(contentType)) {
            throw new IllegalArgumentException("File type not allowed: " + contentType);
        }
    }

    private boolean isAllowedContentType(String contentType) {
        String[] allowed = allowedTypes.split(",");
        for (String type : allowed) {
            if (contentType.toLowerCase().contains(type.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    private boolean canUserDeleteAttachment(User user, NoteAttachment attachment) {
        // User can delete attachment if:
        // 1. They uploaded the file
        // 2. They created the note
        // 3. They are admin
        return attachment.getUploadedBy().getId().equals(user.getId()) ||
               attachment.getNote().getCreator().getId().equals(user.getId()) ||
               isAdmin(user);
    }

    private boolean isAdmin(User user) {
        return user.getSystemRole() != null &&
               user.getSystemRole().name().equals("ADMIN");
    }
}
