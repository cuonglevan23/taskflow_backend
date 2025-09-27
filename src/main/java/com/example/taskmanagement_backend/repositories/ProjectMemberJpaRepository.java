package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.ProjectMember;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberJpaRepository extends JpaRepository<ProjectMember, Long> {
   List<ProjectMember> findByProjectId(Long projectId);

   // ✅ ADD: Method để tìm membership của user trong project
   Optional<ProjectMember> findByProjectAndUser(Project project, User user);

   // ✅ ADD: Method để lấy tất cả members của project
   List<ProjectMember> findByProject(Project project);

   // ✅ ADD: Method để lấy tất cả projects của user
   List<ProjectMember> findByUser(User user);

   /**
    * 🔒 AUTHORIZATION: Find project memberships by user ID
    * Used for search authorization at database layer
    * @param userId The ID of the user
    * @return List of project memberships for the user
    */
   List<ProjectMember> findByUser_Id(Long userId);
}
