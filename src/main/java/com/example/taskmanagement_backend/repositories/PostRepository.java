package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Post;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.PostPrivacy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // Get user's own posts (returning Page for pagination)
    Page<Post> findByAuthorOrderByCreatedAtDesc(User author, Pageable pageable);

    // Get posts for newsfeed (friends + public posts)
    @Query("SELECT p FROM Post p WHERE " +
           "(p.privacy = 'PUBLIC') OR " +
           "(p.privacy = 'FRIENDS' AND p.author.id IN :friendIds) OR " +
           "(p.author.id = :userId) " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findNewsfeedPosts(@Param("userId") Long userId,
                                @Param("friendIds") List<Long> friendIds,
                                Pageable pageable);

    // Get public posts only
    Page<Post> findByPrivacyOrderByCreatedAtDesc(PostPrivacy privacy, Pageable pageable);

    // Get posts by specific users (for profile viewing)
    @Query("SELECT p FROM Post p WHERE p.author.id = :authorId AND " +
           "(p.privacy = 'PUBLIC' OR " +
           "(p.privacy = 'FRIENDS' AND :viewerId IN " +
           "(SELECT CASE WHEN f.user.id = :authorId THEN f.friend.id ELSE f.user.id END " +
           "FROM Friend f WHERE " +
           "(f.user.id = :authorId OR f.friend.id = :authorId) AND " +
           "f.status = 'ACCEPTED')) OR " +
           "p.author.id = :viewerId) " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findVisiblePostsByAuthor(@Param("authorId") Long authorId,
                                       @Param("viewerId") Long viewerId,
                                       Pageable pageable);

    // Get posts with linked tasks
    @Query("SELECT p FROM Post p WHERE p.linkedTask IS NOT NULL AND " +
           "((p.privacy = 'PUBLIC') OR " +
           "(p.privacy = 'FRIENDS' AND p.author.id IN :friendIds) OR " +
           "(p.author.id = :userId)) " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findTaskLinkedPosts(@Param("userId") Long userId,
                                  @Param("friendIds") List<Long> friendIds,
                                  Pageable pageable);

    // Additional methods for profile privacy filtering (returning List for direct conversion)
    @Query("SELECT p FROM Post p WHERE p.author = :author AND p.privacy IN :privacies ORDER BY p.createdAt DESC")
    List<Post> findByAuthorAndPrivacyInOrderByCreatedAtDesc(@Param("author") User author,
                                                           @Param("privacies") List<PostPrivacy> privacies,
                                                           Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.author = :author AND p.privacy = :privacy ORDER BY p.createdAt DESC")
    List<Post> findByAuthorAndPrivacyOrderByCreatedAtDesc(@Param("author") User author,
                                                         @Param("privacy") PostPrivacy privacy,
                                                         Pageable pageable);

    // Count posts by author
    long countByAuthor(User author);

    // Get recent posts for specific users (for cache invalidation)
    @Query("SELECT p FROM Post p WHERE p.author.id IN :authorIds AND " +
           "p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<Post> findRecentPostsByAuthors(@Param("authorIds") List<Long> authorIds,
                                       @Param("since") LocalDateTime since);

    // Search posts by content
    @Query("SELECT p FROM Post p WHERE " +
           "LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) AND " +
           "((p.privacy = 'PUBLIC') OR " +
           "(p.privacy = 'FRIENDS' AND p.author.id IN :friendIds) OR " +
           "(p.author.id = :userId)) " +
           "ORDER BY p.createdAt DESC")
    Page<Post> searchPosts(@Param("keyword") String keyword,
                          @Param("userId") Long userId,
                          @Param("friendIds") List<Long> friendIds,
                          Pageable pageable);

    // ✅ NEW: Get trending posts (high engagement in last 24 hours)
    @Query("SELECT p FROM Post p WHERE " +
           "p.createdAt >= :since AND " +
           "((p.privacy = 'PUBLIC') OR " +
           "(p.privacy = 'FRIENDS' AND p.author.id IN :friendIds) OR " +
           "(p.author.id = :userId)) " +
           "ORDER BY (p.likeCount + p.commentCount * 2) DESC, p.createdAt DESC")
    Page<Post> findTrendingPosts(@Param("userId") Long userId,
                                @Param("friendIds") List<Long> friendIds,
                                @Param("since") LocalDateTime since,
                                Pageable pageable);

    // ✅ NEW: Get pinned posts first, then regular newsfeed
    @Query("SELECT p FROM Post p WHERE " +
           "((p.privacy = 'PUBLIC') OR " +
           "(p.privacy = 'FRIENDS' AND p.author.id IN :friendIds) OR " +
           "(p.author.id = :userId)) " +
           "ORDER BY p.isPinned DESC, p.createdAt DESC")
    Page<Post> findNewsfeedWithPinnedFirst(@Param("userId") Long userId,
                                          @Param("friendIds") List<Long> friendIds,
                                          Pageable pageable);

    // ✅ NEW: Get posts with high engagement for better newsfeed ranking
    @Query("SELECT p FROM Post p WHERE " +
           "((p.privacy = 'PUBLIC') OR " +
           "(p.privacy = 'FRIENDS' AND p.author.id IN :friendIds) OR " +
           "(p.author.id = :userId)) " +
           "ORDER BY " +
           "CASE WHEN p.isPinned = true THEN 0 ELSE 1 END, " +
           "(p.likeCount + p.commentCount) DESC, " +
           "p.createdAt DESC")
    Page<Post> findEngagementBasedNewsfeed(@Param("userId") Long userId,
                                          @Param("friendIds") List<Long> friendIds,
                                          Pageable pageable);
}
