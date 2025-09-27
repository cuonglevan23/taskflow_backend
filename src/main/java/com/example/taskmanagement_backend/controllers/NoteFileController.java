package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.NoteDto.NoteAttachmentResponseDto;
import com.example.taskmanagement_backend.entities.NoteAttachment;
import com.example.taskmanagement_backend.services.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class NoteFileController {

    private final FileUploadService fileUploadService;

    /**
     * Upload file to note
     */
    @PostMapping("/{noteId}/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<NoteAttachmentResponseDto> uploadFileToNote(
            @PathVariable Long noteId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {

        NoteAttachment attachment = fileUploadService.uploadFileToNote(noteId, file, description);
        NoteAttachmentResponseDto responseDto = convertToAttachmentDto(attachment);

        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    /**
     * Get all attachments for a note
     */
    @GetMapping("/{noteId}/attachments")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<NoteAttachmentResponseDto>> getNoteAttachments(@PathVariable Long noteId) {
        List<NoteAttachment> attachments = fileUploadService.getNoteAttachments(noteId);
        List<NoteAttachmentResponseDto> responseDtos = attachments.stream()
                .map(this::convertToAttachmentDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Download file by attachment ID
     */
    @GetMapping("/attachments/{attachmentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long attachmentId) {
        // Get attachment info for headers
        NoteAttachment attachment = fileUploadService.getAttachmentById(attachmentId);

        // Get file resource
        Resource resource = fileUploadService.downloadFile(attachmentId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getFileName() + "\"")
                .body(resource);
    }

    /**
     * Get attachment info by ID
     */
    @GetMapping("/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<NoteAttachmentResponseDto> getAttachmentById(@PathVariable Long attachmentId) {
        NoteAttachment attachment = fileUploadService.getAttachmentById(attachmentId);
        NoteAttachmentResponseDto responseDto = convertToAttachmentDto(attachment);

        return ResponseEntity.ok(responseDto);
    }

    /**
     * Delete attachment
     */
    @DeleteMapping("/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long attachmentId) {
        fileUploadService.deleteAttachment(attachmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Preview image attachment (for images only)
     */
    @GetMapping("/attachments/{attachmentId}/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Resource> previewImage(@PathVariable Long attachmentId) {
        NoteAttachment attachment = fileUploadService.getAttachmentById(attachmentId);

        // Check if it's an image
        if (!attachment.isImage()) {
            return ResponseEntity.badRequest().build();
        }

        Resource resource = fileUploadService.downloadFile(attachmentId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    // Helper method to convert entity to DTO
    private NoteAttachmentResponseDto convertToAttachmentDto(NoteAttachment attachment) {
        return NoteAttachmentResponseDto.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .storedFileName(attachment.getStoredFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .formattedFileSize(attachment.getFormattedFileSize())
                .description(attachment.getDescription())
                .isImage(attachment.isImage())
                .fileExtension(attachment.getFileExtension())
                .noteId(attachment.getNote().getId())
                .noteTitle(attachment.getNote().getTitle())
                .uploadedById(attachment.getUploadedBy().getId())
                .uploadedByName(getFullName(attachment.getUploadedBy()))
                .uploadedByEmail(attachment.getUploadedBy().getEmail())
                .createdAt(attachment.getCreatedAt())
                .updatedAt(attachment.getUpdatedAt())
                .downloadUrl("/api/notes/attachments/" + attachment.getId() + "/download")
                .build();
    }

    private String getFullName(com.example.taskmanagement_backend.entities.User user) {
        if (user.getUserProfile() != null) {
            String firstName = user.getUserProfile().getFirstName();
            String lastName = user.getUserProfile().getLastName();
            if (firstName != null && lastName != null) {
                return String.format("%s %s", firstName, lastName).trim();
            }
        }
        return user.getEmail();
    }
}
