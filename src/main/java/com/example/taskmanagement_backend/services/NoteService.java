package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.NoteDto.CreateNoteRequestDto;
import com.example.taskmanagement_backend.dtos.NoteDto.NoteResponseDto;
import com.example.taskmanagement_backend.dtos.NoteDto.UpdateNoteRequestDto;
import com.example.taskmanagement_backend.entities.Note;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.NoteRepository;
import com.example.taskmanagement_backend.repositories.ProjectJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final UserJpaRepository userRepository;
    private final ProjectJpaRepository projectRepository;

    /**
     * Create a new note (personal or project)
     */
    @Transactional
    public NoteResponseDto createNote(CreateNoteRequestDto createDto) {
        User currentUser = getCurrentUser();

        // ✅ FIXED: Auto-assign current user for personal notes if neither userId nor projectId provided
        boolean isPersonalNote = createDto.getProjectId() == null;
        boolean isProjectNote = createDto.getProjectId() != null;

        // Validate that it's either personal OR project note, not both
        if (createDto.getUserId() != null && createDto.getProjectId() != null) {
            throw new IllegalArgumentException("Note cannot belong to both a user AND a project");
        }

        Note.NoteBuilder noteBuilder = Note.builder()
                .title(createDto.getTitle())
                .content(createDto.getContent())
                .description(createDto.getDescription())
                .creator(currentUser)
                .isPublic(createDto.getIsPublic() != null ? createDto.getIsPublic() : false);

        // Handle personal note
        if (isPersonalNote) {
            // Use provided userId or default to current user
            User targetUser = currentUser; // Default to current user

            if (createDto.getUserId() != null) {
                targetUser = userRepository.findById(createDto.getUserId())
                        .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + createDto.getUserId()));

                // Check if current user can create note for this user (only for themselves or if admin)
                if (!currentUser.getId().equals(targetUser.getId()) && !isAdmin(currentUser)) {
                    throw new AccessDeniedException("You can only create personal notes for yourself");
                }
            }

            noteBuilder.user(targetUser);
        }

        // Handle project note
        if (isProjectNote) {
            Project project = projectRepository.findById(createDto.getProjectId())
                    .orElseThrow(() -> new EntityNotFoundException("Project not found with id: " + createDto.getProjectId()));

            // Check if current user can create notes for this project (must be team member)
            if (!canUserAccessProject(currentUser, project)) {
                throw new AccessDeniedException("You don't have permission to create notes for this project");
            }

            noteBuilder.project(project);
        }

        Note savedNote = noteRepository.save(noteBuilder.build());
        log.info("✅ [NoteService] Created note: {} by user: {}", savedNote.getTitle(), currentUser.getEmail());

        return convertToResponseDto(savedNote);
    }

    /**
     * Get note by ID
     */
    @Transactional(readOnly = true)
    public NoteResponseDto getNoteById(Long noteId) {
        User currentUser = getCurrentUser();

        Note note = noteRepository.findNoteAccessibleByUser(noteId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Note not found or access denied"));

        return convertToResponseDto(note);
    }

    /**
     * Update note
     */
    @Transactional
    public NoteResponseDto updateNote(Long noteId, UpdateNoteRequestDto updateDto) {
        User currentUser = getCurrentUser();

        Note note = noteRepository.findNoteAccessibleByUser(noteId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Note not found or access denied"));

        // Check if user can edit this note
        if (!canUserEditNote(currentUser, note)) {
            throw new AccessDeniedException("You don't have permission to edit this note");
        }

        // Update fields
        if (updateDto.getTitle() != null) {
            note.setTitle(updateDto.getTitle());
        }
        if (updateDto.getContent() != null) {
            note.setContent(updateDto.getContent());
        }
        if (updateDto.getDescription() != null) {
            note.setDescription(updateDto.getDescription());
        }
        if (updateDto.getIsPublic() != null && note.isProjectNote()) {
            note.setIsPublic(updateDto.getIsPublic());
        }
        if (updateDto.getIsArchived() != null) {
            note.setIsArchived(updateDto.getIsArchived());
        }

        Note updatedNote = noteRepository.save(note);
        log.info("✅ [NoteService] Updated note: {} by user: {}", updatedNote.getTitle(), currentUser.getEmail());

        return convertToResponseDto(updatedNote);
    }

    /**
     * Delete note
     */
    @Transactional
    public void deleteNote(Long noteId) {
        User currentUser = getCurrentUser();

        Note note = noteRepository.findNoteAccessibleByUser(noteId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Note not found or access denied"));

        // Check if user can delete this note
        if (!canUserEditNote(currentUser, note)) {
            throw new AccessDeniedException("You don't have permission to delete this note");
        }

        noteRepository.delete(note);
        log.info("✅ [NoteService] Deleted note: {} by user: {}", note.getTitle(), currentUser.getEmail());
    }

    /**
     * Get personal notes for current user
     */
    @Transactional(readOnly = true)
    public List<NoteResponseDto> getMyPersonalNotes(boolean includeArchived) {
        User currentUser = getCurrentUser();

        log.info("🔍 [NoteService] getMyPersonalNotes called - User ID: {}, Email: {}, includeArchived: {}",
                currentUser.getId(), currentUser.getEmail(), includeArchived);

        List<Note> notes;

        if (includeArchived) {
            // Get all notes (both archived and non-archived)
            notes = noteRepository.findByUserIdOrderByUpdatedAtDesc(currentUser.getId());
            log.info("🔍 [NoteService] Getting ALL notes (includeArchived=true)");
        } else {
            // Get only non-archived notes (isArchived = false)
            notes = noteRepository.findByUserIdAndIsArchivedOrderByUpdatedAtDesc(
                    currentUser.getId(), false);
            log.info("🔍 [NoteService] Getting NON-ARCHIVED notes (isArchived=false)");
        }

        log.info("🔍 [NoteService] Found {} notes in database", notes.size());

        // Debug: Log each note
        notes.forEach(note -> {
            log.info("🔍 [NoteService] Note - ID: {}, Title: {}, UserID: {}, IsArchived: {}",
                    note.getId(), note.getTitle(),
                    note.getUser() != null ? note.getUser().getId() : "NULL",
                    note.getIsArchived());
        });

        List<NoteResponseDto> result = notes.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());

        log.info("🔍 [NoteService] Returning {} notes to controller", result.size());

        return result;
    }

    /**
     * Get personal notes with pagination
     */
    @Transactional(readOnly = true)
    public Page<NoteResponseDto> getMyPersonalNotes(boolean includeArchived, int page, int size) {
        User currentUser = getCurrentUser();

        log.info("🔍 [NoteService] getMyPersonalNotes PAGINATED called - User ID: {}, Email: {}, includeArchived: {}, page: {}, size: {}",
                currentUser.getId(), currentUser.getEmail(), includeArchived, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Note> notes;

        if (includeArchived) {
            // Get all notes (both archived and non-archived) - need to implement this in repository
            List<Note> allNotes = noteRepository.findByUserIdOrderByUpdatedAtDesc(currentUser.getId());
            // For now, manually create a page from the list (not ideal but works for debugging)
            int start = page * size;
            int end = Math.min(start + size, allNotes.size());
            List<Note> pageContent = allNotes.subList(start, end);
            notes = new PageImpl<>(pageContent, pageable, allNotes.size());
            log.info("🔍 [NoteService] PAGINATED Getting ALL notes (includeArchived=true)");
        } else {
            // Get only non-archived notes (isArchived = false)
            notes = noteRepository.findByUserIdAndIsArchivedOrderByUpdatedAtDesc(
                    currentUser.getId(), false, pageable);
            log.info("🔍 [NoteService] PAGINATED Getting NON-ARCHIVED notes (isArchived=false)");
        }

        log.info("🔍 [NoteService] PAGINATED Found {} notes in database, totalElements: {}", notes.getContent().size(), notes.getTotalElements());

        // Debug: Log each note
        notes.getContent().forEach(note -> {
            log.info("🔍 [NoteService] PAGINATED Note - ID: {}, Title: {}, UserID: {}, IsArchived: {}",
                    note.getId(), note.getTitle(),
                    note.getUser() != null ? note.getUser().getId() : "NULL",
                    note.getIsArchived());
        });

        Page<NoteResponseDto> result = notes.map(this::convertToResponseDto);
        log.info("🔍 [NoteService] PAGINATED Returning {} notes to controller", result.getContent().size());

        return result;
    }

    /**
     * Get project notes
     */
    @Transactional(readOnly = true)
    public List<NoteResponseDto> getProjectNotes(Long projectId, boolean includeArchived) {
        User currentUser = getCurrentUser();

        // Verify user can access this project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        if (!canUserAccessProject(currentUser, project)) {
            throw new AccessDeniedException("You don't have permission to view notes for this project");
        }

        List<Note> notes = noteRepository.findByProjectIdAndIsArchivedOrderByUpdatedAtDesc(
                projectId, !includeArchived);

        return notes.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get project notes with pagination
     */
    @Transactional(readOnly = true)
    public Page<NoteResponseDto> getProjectNotes(Long projectId, boolean includeArchived, int page, int size) {
        User currentUser = getCurrentUser();

        // Verify user can access this project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        if (!canUserAccessProject(currentUser, project)) {
            throw new AccessDeniedException("You don't have permission to view notes for this project");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Note> notes = noteRepository.findByProjectIdAndIsArchivedOrderByUpdatedAtDesc(
                projectId, !includeArchived, pageable);

        return notes.map(this::convertToResponseDto);
    }

    /**
     * Search personal notes
     */
    @Transactional(readOnly = true)
    public List<NoteResponseDto> searchMyPersonalNotes(String keyword, boolean includeArchived) {
        User currentUser = getCurrentUser();

        List<Note> notes = noteRepository.searchPersonalNotes(
                currentUser.getId(), !includeArchived, keyword);

        return notes.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Search project notes
     */
    @Transactional(readOnly = true)
    public List<NoteResponseDto> searchProjectNotes(Long projectId, String keyword, boolean includeArchived) {
        User currentUser = getCurrentUser();

        // Verify user can access this project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        if (!canUserAccessProject(currentUser, project)) {
            throw new AccessDeniedException("You don't have permission to search notes for this project");
        }

        List<Note> notes = noteRepository.searchProjectNotes(projectId, !includeArchived, keyword);

        return notes.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get recent notes
     */
    @Transactional(readOnly = true)
    public List<NoteResponseDto> getRecentPersonalNotes(int limit) {
        User currentUser = getCurrentUser();

        Pageable pageable = PageRequest.of(0, limit);
        List<Note> notes = noteRepository.findRecentPersonalNotes(currentUser.getId(), pageable);

        return notes.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get public project notes (visible to team members)
     */
    @Transactional(readOnly = true)
    public List<NoteResponseDto> getPublicProjectNotes(Long projectId) {
        User currentUser = getCurrentUser();

        // Verify user can access this project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        if (!canUserAccessProject(currentUser, project)) {
            throw new AccessDeniedException("You don't have permission to view notes for this project");
        }

        List<Note> notes = noteRepository.findPublicProjectNotes(projectId);

        return notes.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    // Helper methods
    private NoteResponseDto convertToResponseDto(Note note) {
        NoteResponseDto.NoteResponseDtoBuilder builder = NoteResponseDto.builder()
                .id(note.getId())
                .title(note.getTitle())
                .content(note.getContent())
                .description(note.getDescription())
                .isPublic(note.getIsPublic())
                .isArchived(note.getIsArchived())
                .isPersonalNote(note.isPersonalNote())
                .isProjectNote(note.isProjectNote())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt());

        // Creator info
        if (note.getCreator() != null) {
            builder.creatorId(note.getCreator().getId())
                   .creatorName(getFullName(note.getCreator()))
                   .creatorEmail(note.getCreator().getEmail());
        }

        // User info (for personal notes)
        if (note.getUser() != null) {
            builder.userId(note.getUser().getId())
                   .userName(getFullName(note.getUser()))
                   .userEmail(note.getUser().getEmail());
        }

        // Project info (for project notes)
        if (note.getProject() != null) {
            builder.projectId(note.getProject().getId())
                   .projectName(note.getProject().getName());
        }

        // NEW: Attachment info
        if (note.getAttachments() != null) {
            List<com.example.taskmanagement_backend.dtos.NoteDto.NoteAttachmentResponseDto> attachmentDtos =
                note.getAttachments().stream()
                    .map(this::convertToAttachmentDto)
                    .collect(Collectors.toList());

            builder.attachments(attachmentDtos)
                   .attachmentCount(note.getAttachmentCount())
                   .totalAttachmentSize(note.getTotalAttachmentSize())
                   .formattedTotalAttachmentSize(formatFileSize(note.getTotalAttachmentSize()))
                   .hasAttachments(note.hasAttachments());
        } else {
            builder.attachments(new ArrayList<>())
                   .attachmentCount(0)
                   .totalAttachmentSize(0L)
                   .formattedTotalAttachmentSize("0 B")
                   .hasAttachments(false);
        }

        return builder.build();
    }

    // NEW: Convert attachment to DTO
    private com.example.taskmanagement_backend.dtos.NoteDto.NoteAttachmentResponseDto convertToAttachmentDto(
            com.example.taskmanagement_backend.entities.NoteAttachment attachment) {
        return com.example.taskmanagement_backend.dtos.NoteDto.NoteAttachmentResponseDto.builder()
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

    // NEW: Format file size helper
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private String getFullName(User user) {
        if (user.getUserProfile() != null) {
            String firstName = user.getUserProfile().getFirstName();
            String lastName = user.getUserProfile().getLastName();
            if (firstName != null && lastName != null) {
                return String.format("%s %s", firstName, lastName).trim();
            }
        }
        return user.getEmail();
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

    private boolean canUserAccessProject(User user, Project project) {
        // For now, simplified access check - user can access if they are creator or admin
        // TODO: Implement proper team membership check when Team relationship is available
        return user.getId().equals(project.getCreatedBy().getId()) || isAdmin(user);
    }

    private boolean canUserEditNote(User user, Note note) {
        // User can edit note if:
        // 1. They are the creator of the note
        // 2. They are admin
        // 3. For personal notes: they are the owner
        // 4. For project notes: they have access to the project

        if (note.getCreator().getId().equals(user.getId()) || isAdmin(user)) {
            return true;
        }

        if (note.isPersonalNote() && note.getUser().getId().equals(user.getId())) {
            return true;
        }

        if (note.isProjectNote() && canUserAccessProject(user, note.getProject())) {
            return true;
        }

        return false;
    }

    private boolean isAdmin(User user) {
        return user.getSystemRole() != null &&
               user.getSystemRole().name().equals("ADMIN");
    }
}
