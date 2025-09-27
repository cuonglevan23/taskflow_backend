package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.NoteAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteAttachmentRepository extends JpaRepository<NoteAttachment, Long> {

    // Find attachments by note ID
    List<NoteAttachment> findByNoteIdOrderByCreatedAtDesc(Long noteId);

    // Find attachment by stored filename
    Optional<NoteAttachment> findByStoredFileName(String storedFileName);

    // Find attachments by note and user
    @Query("SELECT na FROM NoteAttachment na WHERE na.note.id = :noteId AND na.uploadedBy.id = :userId")
    List<NoteAttachment> findByNoteIdAndUploadedById(@Param("noteId") Long noteId, @Param("userId") Long userId);

    // Find image attachments for a note
    @Query("SELECT na FROM NoteAttachment na WHERE na.note.id = :noteId AND " +
           "LOWER(na.contentType) LIKE 'image/%' ORDER BY na.createdAt DESC")
    List<NoteAttachment> findImageAttachmentsByNoteId(@Param("noteId") Long noteId);

    // Count attachments for a note
    long countByNoteId(Long noteId);

    // Get total size of attachments for a note
    @Query("SELECT COALESCE(SUM(na.fileSize), 0) FROM NoteAttachment na WHERE na.note.id = :noteId")
    long getTotalSizeByNoteId(@Param("noteId") Long noteId);

    // Find attachments by content type
    List<NoteAttachment> findByNoteIdAndContentTypeContainingIgnoreCase(Long noteId, String contentType);

    // Check if user can access attachment (based on note permissions)
    @Query("SELECT na FROM NoteAttachment na WHERE na.id = :attachmentId AND " +
           "((na.note.user.id = :userId) OR " +
           "(na.note.project.id IN (SELECT p.id FROM Project p JOIN p.team t JOIN t.members m WHERE m.user.id = :userId)))")
    Optional<NoteAttachment> findAttachmentAccessibleByUser(@Param("attachmentId") Long attachmentId, @Param("userId") Long userId);
}
