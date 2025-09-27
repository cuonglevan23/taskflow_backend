package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {

    /**
     * Tìm tất cả file đính kèm của một task (chưa bị xóa)
     */
    @Query("SELECT ta FROM TaskAttachment ta WHERE ta.task.id = :taskId AND ta.isDeleted = false ORDER BY ta.createdAt DESC")
    List<TaskAttachment> findByTaskIdAndNotDeleted(@Param("taskId") Long taskId);

    /**
     * Tìm file đính kèm theo file key
     */
    @Query("SELECT ta FROM TaskAttachment ta WHERE ta.fileKey = :fileKey AND ta.isDeleted = false")
    Optional<TaskAttachment> findByFileKeyAndNotDeleted(@Param("fileKey") String fileKey);

    /**
     * Đếm số lượng file của một task
     */
    @Query("SELECT COUNT(ta) FROM TaskAttachment ta WHERE ta.task.id = :taskId AND ta.isDeleted = false")
    Long countByTaskIdAndNotDeleted(@Param("taskId") Long taskId);

    /**
     * Tìm tất cả file đính kèm của user trong một task
     */
    @Query("SELECT ta FROM TaskAttachment ta WHERE ta.task.id = :taskId AND ta.uploadedBy.id = :userId AND ta.isDeleted = false ORDER BY ta.createdAt DESC")
    List<TaskAttachment> findByTaskIdAndUserIdAndNotDeleted(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /**
     * Tính tổng dung lượng file của một task
     */
    @Query("SELECT COALESCE(SUM(ta.fileSize), 0) FROM TaskAttachment ta WHERE ta.task.id = :taskId AND ta.isDeleted = false")
    Long calculateTotalFileSizeByTaskId(@Param("taskId") Long taskId);

    /**
     * ✅ NEW: Xóa tất cả file đính kèm của một task (xóa hàng loạt khi xóa task)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM TaskAttachment ta WHERE ta.task.id = :taskId")
    int deleteByTaskId(@Param("taskId") Long taskId);
}
