package com.example.taskmanagement_backend.repositories;// package com.example.taskmanagement_backend.repositories;

 import com.example.taskmanagement_backend.entities.TaskAttachment;
 import org.springframework.data.jpa.repository.JpaRepository;
 import org.springframework.stereotype.Repository;

 import java.util.List;

@Repository
 public interface TaskAttachmentJpaRepository extends JpaRepository<TaskAttachment, Long> {
     List<TaskAttachment> findByTaskId(Long taskId);

 }
