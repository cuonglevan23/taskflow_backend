package com.example.taskmanagement_backend.repositories;
import com.example.taskmanagement_backend.entities.ProjectInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {
    List<ProjectInvitation> findByProjectId(Long projectId);
    Optional<ProjectInvitation> findByToken(String token);
}