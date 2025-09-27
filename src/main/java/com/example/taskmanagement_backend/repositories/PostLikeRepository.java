package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Post;
import com.example.taskmanagement_backend.entities.PostLike;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    // Check if user liked a post
    Optional<PostLike> findByPostAndUser(Post post, User user);

    // Check if user liked a post by IDs
    @Query("SELECT pl FROM PostLike pl WHERE pl.post.id = :postId AND pl.user.id = :userId")
    Optional<PostLike> findByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    // Get all likes for a post
    List<PostLike> findByPostOrderByCreatedAtDesc(Post post);

    // Get likes count for a post
    long countByPost(Post post);

    // Get users who liked a post
    @Query("SELECT pl.user FROM PostLike pl WHERE pl.post.id = :postId ORDER BY pl.createdAt DESC")
    List<User> findUsersByPostId(@Param("postId") Long postId);

    // Check if user exists in likes
    boolean existsByPostAndUser(Post post, User user);

    // Delete like by post and user
    void deleteByPostAndUser(Post post, User user);

    /**
     * ✅ NEW: Delete all likes for a specific post (for cascade deletion)
     */
    void deleteByPost(Post post);
}
