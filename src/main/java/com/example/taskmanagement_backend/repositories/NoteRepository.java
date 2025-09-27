package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    // Personal notes queries
    List<Note> findByUserIdAndIsArchivedOrderByUpdatedAtDesc(Long userId, Boolean isArchived);

    // Add this method for debug endpoint
    List<Note> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Page<Note> findByUserIdAndIsArchivedOrderByUpdatedAtDesc(Long userId, Boolean isArchived, Pageable pageable);

    @Query("SELECT n FROM Note n WHERE n.user.id = :userId AND n.isArchived = :isArchived AND " +
           "(LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(n.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Note> searchPersonalNotes(@Param("userId") Long userId,
                                   @Param("isArchived") Boolean isArchived,
                                   @Param("keyword") String keyword);

    // Project notes queries
    List<Note> findByProjectIdAndIsArchivedOrderByUpdatedAtDesc(Long projectId, Boolean isArchived);

    Page<Note> findByProjectIdAndIsArchivedOrderByUpdatedAtDesc(Long projectId, Boolean isArchived, Pageable pageable);

    @Query("SELECT n FROM Note n WHERE n.project.id = :projectId AND n.isArchived = :isArchived AND " +
           "(LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(n.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Note> searchProjectNotes(@Param("projectId") Long projectId,
                                  @Param("isArchived") Boolean isArchived,
                                  @Param("keyword") String keyword);

    // Public project notes (visible to team members)
    @Query("SELECT n FROM Note n WHERE n.project.id = :projectId AND n.isPublic = true AND n.isArchived = false")
    List<Note> findPublicProjectNotes(@Param("projectId") Long projectId);

    // Count queries
    long countByUserIdAndIsArchived(Long userId, Boolean isArchived);

    long countByProjectIdAndIsArchived(Long projectId, Boolean isArchived);

    // Recent notes
    @Query("SELECT n FROM Note n WHERE n.user.id = :userId AND n.isArchived = false ORDER BY n.updatedAt DESC")
    List<Note> findRecentPersonalNotes(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT n FROM Note n WHERE n.project.id = :projectId AND n.isArchived = false ORDER BY n.updatedAt DESC")
    List<Note> findRecentProjectNotes(@Param("projectId") Long projectId, Pageable pageable);

    // Fixed query - use 'members' instead of 'teamMembers' and simplify the logic
    @Query("SELECT n FROM Note n WHERE n.id = :noteId AND " +
           "((n.user.id = :userId) OR " +
           "(n.project.id IN (SELECT p.id FROM Project p JOIN p.team t JOIN t.members m WHERE m.user.id = :userId)))")
    Optional<Note> findNoteAccessibleByUser(@Param("noteId") Long noteId, @Param("userId") Long userId);

    // Find notes created by specific user
    List<Note> findByCreatorIdOrderByUpdatedAtDesc(Long creatorId);
}
