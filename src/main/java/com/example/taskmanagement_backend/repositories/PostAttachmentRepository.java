package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Post;
import com.example.taskmanagement_backend.entities.PostAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostAttachmentRepository extends JpaRepository<PostAttachment, Long> {

    /**
     * Find all attachments for a specific post
     */
    List<PostAttachment> findByPostOrderByCreatedAtAsc(Post post);

    /**
     * Find all attachments for a specific post by post ID
     */
    List<PostAttachment> findByPostIdOrderByCreatedAtAsc(Long postId);

    /**
     * Find all image attachments for a specific post
     */
    @Query("SELECT pa FROM PostAttachment pa WHERE pa.post = :post AND pa.attachmentType = 'IMAGE' ORDER BY pa.createdAt ASC")
    List<PostAttachment> findImagesByPost(@Param("post") Post post);

    /**
     * Find all document attachments for a specific post
     */
    @Query("SELECT pa FROM PostAttachment pa WHERE pa.post = :post AND pa.attachmentType = 'DOCUMENT' ORDER BY pa.createdAt ASC")
    List<PostAttachment> findDocumentsByPost(@Param("post") Post post);

    /**
     * Count attachments by post
     */
    long countByPost(Post post);

    /**
     * Count images by post
     */
    @Query("SELECT COUNT(pa) FROM PostAttachment pa WHERE pa.post = :post AND pa.attachmentType = 'IMAGE'")
    long countImagesByPost(@Param("post") Post post);

    /**
     * Count documents by post
     */
    @Query("SELECT COUNT(pa) FROM PostAttachment pa WHERE pa.post = :post AND pa.attachmentType = 'DOCUMENT'")
    long countDocumentsByPost(@Param("post") Post post);

    /**
     * Delete all attachments for a specific post
     */
    void deleteByPost(Post post);
}
