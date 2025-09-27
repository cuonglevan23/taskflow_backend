package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.PostComment;
import com.example.taskmanagement_backend.entities.PostCommentLike;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostCommentLikeRepository extends JpaRepository<PostCommentLike, Long> {

    // Check if user liked a comment
    Optional<PostCommentLike> findByCommentAndUser(PostComment comment, User user);

    // Check if user liked a comment by IDs
    @Query("SELECT pcl FROM PostCommentLike pcl WHERE pcl.comment.id = :commentId AND pcl.user.id = :userId")
    Optional<PostCommentLike> findByCommentIdAndUserId(@Param("commentId") Long commentId, @Param("userId") Long userId);

    // Check if user liked a comment by IDs (alternative method name)
    @Query("SELECT pcl FROM PostCommentLike pcl WHERE pcl.comment.id = :commentId AND pcl.user.id = :userId")
    Optional<PostCommentLike> findByCommentAndUserId(@Param("commentId") Long commentId, @Param("userId") Long userId);

    // Get all likes for a comment
    List<PostCommentLike> findByCommentOrderByCreatedAtDesc(PostComment comment);

    // Get likes count for a comment
    long countByComment(PostComment comment);

    // Get users who liked a comment
    @Query("SELECT pcl.user FROM PostCommentLike pcl WHERE pcl.comment.id = :commentId ORDER BY pcl.createdAt DESC")
    List<User> findUsersByCommentId(@Param("commentId") Long commentId);

    // Check if user exists in comment likes
    boolean existsByCommentAndUser(PostComment comment, User user);

    // Delete like by comment and user
    void deleteByCommentAndUser(PostComment comment, User user);

    // Get recent likes for a comment (for displaying like details)
    @Query("SELECT pcl FROM PostCommentLike pcl WHERE pcl.comment.id = :commentId ORDER BY pcl.createdAt DESC")
    List<PostCommentLike> findRecentLikesByCommentId(@Param("commentId") Long commentId);

    /**
     * ✅ NEW: Delete all likes for a specific comment (for cascade deletion)
     */
    void deleteByComment(PostComment comment);
}
