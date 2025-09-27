package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.TaskChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TaskChecklistJpaRepository extends JpaRepository<TaskChecklist, Long> {
  List<TaskChecklist> findByTaskId(Long taskId);

  @Modifying
  @Transactional
  @Query("DELETE FROM TaskChecklist tc WHERE tc.task.id = :taskId")
  void deleteByTaskId(@Param("taskId") Long taskId);
}
