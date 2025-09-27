package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.TaskComment;
import com.example.taskmanagement_backend.entities.User;
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
public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    // Lấy tất cả comments của 1 task, sắp xếp theo thời gian tạo
    List<TaskComment> findByTaskOrderByCreatedAtAsc(Task task);

    // Lấy comments của 1 task với phân trang
    Page<TaskComment> findByTaskOrderByCreatedAtDesc(Task task, Pageable pageable);

    // Lấy comments của 1 user trong 1 task
    List<TaskComment> findByTaskAndUserOrderByCreatedAtAsc(Task task, User user);

    // Đếm số comments của 1 task
    long countByTask(Task task);

    // Lấy comment mới nhất của 1 task
    @Query("SELECT tc FROM TaskComment tc WHERE tc.task = :task ORDER BY tc.createdAt DESC LIMIT 1")
    TaskComment findLatestComment(@Param("task") Task task);

    // Xóa tất cả comments của 1 task (by task id)
    @Modifying
    @Transactional
    @Query("DELETE FROM TaskComment tc WHERE tc.task.id = :taskId")
    void deleteByTaskId(@Param("taskId") Long taskId);
}
