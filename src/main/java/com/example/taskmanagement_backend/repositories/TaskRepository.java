package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Tìm task theo ID và kiểm tra không bị xóa
     */
    @Query("SELECT t FROM Task t WHERE t.id = :id")
    Optional<Task> findById(@Param("id") Long id);

    /**
     * Tìm tất cả task của một project
     */
    @Query("SELECT t FROM Task t WHERE t.project.id = :projectId ORDER BY t.createdAt DESC")
    List<Task> findByProjectId(@Param("projectId") Long projectId);

    /**
     * Tìm task được assign cho user
     */
    @Query("SELECT t FROM Task t JOIN t.assignees a WHERE a.user.id = :userId ORDER BY t.deadline ASC")
    List<Task> findByAssigneeUserId(@Param("userId") Long userId);

    /**
     * Tìm task theo status
     */
    @Query("SELECT t FROM Task t WHERE t.statusKey = :status ORDER BY t.createdAt DESC")
    List<Task> findByStatus(@Param("status") String status);

    /**
     * Tìm task theo priority
     */
    @Query("SELECT t FROM Task t WHERE t.priorityKey = :priority ORDER BY t.deadline ASC")
    List<Task> findByPriority(@Param("priority") String priority);

    /**
     * Đếm số task của project
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.id = :projectId")
    Long countByProjectId(@Param("projectId") Long projectId);

    /**
     * Tìm task overdue
     */
    @Query("SELECT t FROM Task t WHERE t.deadline < CURRENT_DATE AND t.statusKey != 'COMPLETED'")
    List<Task> findOverdueTasks();

    /**
     * Tìm task overdue với thời gian cụ thể
     */
    @Query("SELECT DISTINCT t FROM Task t JOIN t.assignees a WHERE t.deadline < :currentDate AND t.statusKey != 'COMPLETED'")
    List<Task> findOverdueTasks(@Param("currentDate") java.time.LocalDate currentDate);

    /**
     * Tìm tasks sắp quá hạn trong khoảng thời gian
     */
    @Query("SELECT DISTINCT t FROM Task t JOIN t.assignees a WHERE t.deadline BETWEEN :startDate AND :endDate AND t.statusKey != 'COMPLETED'")
    List<Task> findTasksDueBetween(@Param("startDate") java.time.LocalDate startDate,
                                   @Param("endDate") java.time.LocalDate endDate);

    /**
     * Tìm tasks của user được assign
     */
    @Query("SELECT t FROM Task t JOIN t.assignees a WHERE a.user.id = :userId ORDER BY t.deadline ASC")
    List<Task> findByAssignedUserId(@Param("userId") Long userId);

    /**
     * Tìm tasks sắp quá hạn của một user cụ thể
     */
    @Query("SELECT t FROM Task t JOIN t.assignees a WHERE a.user.id = :userId AND t.deadline BETWEEN :startDate AND :endDate AND t.statusKey != 'COMPLETED'")
    List<Task> findUserTasksDueBetween(@Param("userId") Long userId,
                                       @Param("startDate") java.time.LocalDate startDate,
                                       @Param("endDate") java.time.LocalDate endDate);
}
