package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Post;
import com.example.taskmanagement_backend.entities.PostComment;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    // Get comments for a post (top-level comments only)
    Page<PostComment> findByPostAndParentCommentIsNullOrderByCreatedAtAsc(Post post, Pageable pageable);

    // Get replies for a comment
    List<PostComment> findByParentCommentOrderByCreatedAtAsc(PostComment parentComment);

    // Get replies for a comment with pagination
    @Query("SELECT pc FROM PostComment pc WHERE pc.parentComment = :parentComment ORDER BY pc.createdAt ASC")
    Page<PostComment> findRepliesByParentComment(@Param("parentComment") PostComment parentComment, Pageable pageable);

    // Get all comments for a post (including replies)
    List<PostComment> findByPostOrderByCreatedAtAsc(Post post);

    // Count comments for a post (excluding replies)
    long countByPostAndParentCommentIsNull(Post post);

    // Count all comments for a post (including replies)
    long countByPost(Post post);

    // Count replies for a specific comment
    long countByParentComment(PostComment parentComment);

    // Get recent comments by user
    Page<PostComment> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // Search comments by content
    @Query("SELECT pc FROM PostComment pc WHERE " +
           "LOWER(pc.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY pc.createdAt DESC")
    Page<PostComment> searchComments(@Param("keyword") String keyword, Pageable pageable);

    // Get top-level comments with their reply counts
    @Query("SELECT pc, " +
           "(SELECT COUNT(reply) FROM PostComment reply WHERE reply.parentComment = pc) as replyCount " +
           "FROM PostComment pc WHERE pc.post.id = :postId AND pc.parentComment IS NULL " +
           "ORDER BY pc.createdAt ASC")
    List<Object[]> findCommentsWithReplyCounts(@Param("postId") Long postId);

    /**
     * ✅ NEW: Delete all comments for a specific post (for cascade deletion)
     */
    void deleteByPost(Post post);
}
