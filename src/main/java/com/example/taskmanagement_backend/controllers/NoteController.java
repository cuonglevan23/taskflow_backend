package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.NoteDto.CreateNoteRequestDto;
import com.example.taskmanagement_backend.dtos.NoteDto.NoteResponseDto;
import com.example.taskmanagement_backend.dtos.NoteDto.UpdateNoteRequestDto;
import com.example.taskmanagement_backend.entities.Note;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.NoteRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.services.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class NoteController {

    private final NoteService noteService;
    private final NoteRepository noteRepository;
    private final UserJpaRepository userRepository;

    // ===== CRUD Operations =====

    /**
     * Create a new note (personal or project)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<NoteResponseDto> createNote(
            @Valid @RequestBody CreateNoteRequestDto createDto) {

        NoteResponseDto note = noteService.createNote(createDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    /**
     * Get note by ID
     */
    @GetMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<NoteResponseDto> getNoteById(@PathVariable Long noteId) {
        NoteResponseDto note = noteService.getNoteById(noteId);
        return ResponseEntity.ok(note);
    }

    /**
     * Update note
     */
    @PutMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<NoteResponseDto> updateNote(
            @PathVariable Long noteId,
            @Valid @RequestBody UpdateNoteRequestDto updateDto) {

        NoteResponseDto updatedNote = noteService.updateNote(noteId, updateDto);
        return ResponseEntity.ok(updatedNote);
    }

    /**
     * Delete note
     */
    @DeleteMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Void> deleteNote(@PathVariable Long noteId) {
        noteService.deleteNote(noteId);
        return ResponseEntity.noContent().build();
    }

    // ===== Personal Notes =====

    /**
     * Get current user's personal notes
     */
    @GetMapping("/my-notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<NoteResponseDto>> getMyPersonalNotes(
            @RequestParam(defaultValue = "false") boolean includeArchived) {

        List<NoteResponseDto> notes = noteService.getMyPersonalNotes(includeArchived);
        return ResponseEntity.ok(notes);
    }

    /**
     * Get current user's personal notes with pagination
     */
    @GetMapping("/my-notes/paginated")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Page<NoteResponseDto>> getMyPersonalNotesPaginated(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<NoteResponseDto> notes = noteService.getMyPersonalNotes(includeArchived, page, size);
        return ResponseEntity.ok(notes);
    }

    /**
     * Search current user's personal notes
     */
    @GetMapping("/my-notes/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<NoteResponseDto>> searchMyPersonalNotes(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "false") boolean includeArchived) {

        List<NoteResponseDto> notes = noteService.searchMyPersonalNotes(keyword, includeArchived);
        return ResponseEntity.ok(notes);
    }

    /**
     * Get recent personal notes
     */
    @GetMapping("/my-notes/recent")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<NoteResponseDto>> getRecentPersonalNotes(
            @RequestParam(defaultValue = "5") int limit) {

        List<NoteResponseDto> notes = noteService.getRecentPersonalNotes(limit);
        return ResponseEntity.ok(notes);
    }

    // ===== Project Notes =====

    /**
     * Get project notes
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<NoteResponseDto>> getProjectNotes(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {

        List<NoteResponseDto> notes = noteService.getProjectNotes(projectId, includeArchived);
        return ResponseEntity.ok(notes);
    }

    /**
     * Get project notes with pagination
     */
    @GetMapping("/project/{projectId}/paginated")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Page<NoteResponseDto>> getProjectNotesPaginated(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<NoteResponseDto> notes = noteService.getProjectNotes(projectId, includeArchived, page, size);
        return ResponseEntity.ok(notes);
    }

    /**
     * Search project notes
     */
    @GetMapping("/project/{projectId}/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<NoteResponseDto>> searchProjectNotes(
            @PathVariable Long projectId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "false") boolean includeArchived) {

        List<NoteResponseDto> notes = noteService.searchProjectNotes(projectId, keyword, includeArchived);
        return ResponseEntity.ok(notes);
    }

    /**
     * Get public project notes (visible to all team members)
     */
    @GetMapping("/project/{projectId}/public")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<NoteResponseDto>> getPublicProjectNotes(@PathVariable Long projectId) {
        List<NoteResponseDto> notes = noteService.getPublicProjectNotes(projectId);
        return ResponseEntity.ok(notes);
    }

    // ===== Quick Actions =====

    /**
     * Archive/Unarchive note
     */
    @PutMapping("/{noteId}/archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<NoteResponseDto> toggleArchiveNote(
            @PathVariable Long noteId,
            @RequestParam boolean archived) {

        UpdateNoteRequestDto updateDto = UpdateNoteRequestDto.builder()
                .isArchived(archived)
                .build();

        NoteResponseDto updatedNote = noteService.updateNote(noteId, updateDto);
        return ResponseEntity.ok(updatedNote);
    }

    /**
     * Toggle public visibility for project notes
     */
    @PutMapping("/{noteId}/visibility")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<NoteResponseDto> toggleNoteVisibility(
            @PathVariable Long noteId,
            @RequestParam boolean isPublic) {

        UpdateNoteRequestDto updateDto = UpdateNoteRequestDto.builder()
                .isPublic(isPublic)
                .build();

        NoteResponseDto updatedNote = noteService.updateNote(noteId, updateDto);
        return ResponseEntity.ok(updatedNote);
    }

    /**
     * DEBUG: Direct database query for troubleshooting
     */
    @GetMapping("/debug/my-notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Map<String, Object>> debugMyNotes() {
        User currentUser = getCurrentUser();

        // Get all notes for current user regardless of archived status
        List<Note> allNotes = noteRepository.findByUserIdOrderByUpdatedAtDesc(currentUser.getId());
        List<Note> nonArchivedNotes = noteRepository.findByUserIdAndIsArchivedOrderByUpdatedAtDesc(currentUser.getId(), false);
        List<Note> archivedNotes = noteRepository.findByUserIdAndIsArchivedOrderByUpdatedAtDesc(currentUser.getId(), true);

        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("currentUserId", currentUser.getId());
        debugInfo.put("currentUserEmail", currentUser.getEmail());
        debugInfo.put("totalNotesCount", allNotes.size());
        debugInfo.put("nonArchivedCount", nonArchivedNotes.size());
        debugInfo.put("archivedCount", archivedNotes.size());

        // Detail about each note
        List<Map<String, Object>> noteDetails = allNotes.stream().map(note -> {
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", note.getId());
            detail.put("title", note.getTitle());
            detail.put("userId", note.getUser() != null ? note.getUser().getId() : null);
            detail.put("isArchived", note.getIsArchived());
            detail.put("isArchivedType", note.getIsArchived().getClass().getSimpleName());
            detail.put("createdAt", note.getCreatedAt());
            return detail;
        }).collect(Collectors.toList());

        debugInfo.put("noteDetails", noteDetails);

        return ResponseEntity.ok(debugInfo);
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
}
