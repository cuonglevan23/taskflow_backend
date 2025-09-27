package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.TaskActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {

    // Lấy tất cả activity của một task theo thứ tự thời gian mới nhất
    @Query("SELECT ta FROM TaskActivity ta WHERE ta.task.id = :taskId ORDER BY ta.createdAt DESC")
    List<TaskActivity> findByTaskIdOrderByCreatedAtDesc(@Param("taskId") Long taskId);

    // Lấy activity của task với phân trang
    @Query("SELECT ta FROM TaskActivity ta WHERE ta.task.id = :taskId ORDER BY ta.createdAt DESC")
    Page<TaskActivity> findByTaskIdOrderByCreatedAtDesc(@Param("taskId") Long taskId, Pageable pageable);

    // Lấy activity gần đây nhất của task
    @Query("SELECT ta FROM TaskActivity ta WHERE ta.task.id = :taskId ORDER BY ta.createdAt DESC")
    List<TaskActivity> findRecentActivityByTaskId(@Param("taskId") Long taskId, Pageable pageable);

    // Lấy tất cả activity của user
    @Query("SELECT ta FROM TaskActivity ta WHERE ta.user.id = :userId ORDER BY ta.createdAt DESC")
    Page<TaskActivity> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    // Đếm số activity của task
    @Query("SELECT COUNT(ta) FROM TaskActivity ta WHERE ta.task.id = :taskId")
    Long countByTaskId(@Param("taskId") Long taskId);

    // Lấy activity theo loại hoạt động
    @Query("SELECT ta FROM TaskActivity ta WHERE ta.task.id = :taskId AND ta.activityType = :activityType ORDER BY ta.createdAt DESC")
    List<TaskActivity> findByTaskIdAndActivityType(@Param("taskId") Long taskId, @Param("activityType") String activityType);

    // ✅ NEW: Delete all activities for a task (for task deletion)
    @Modifying
    @Transactional
    @Query("DELETE FROM TaskActivity ta WHERE ta.task.id = :taskId")
    void deleteByTaskId(@Param("taskId") Long taskId);
}
