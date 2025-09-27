package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ProjectMemberDto.CreateProjectMemberRequestDto;
import com.example.taskmanagement_backend.dtos.ProjectMemberDto.ProjectMemberResponseDto;
import com.example.taskmanagement_backend.entities.Project;
import com.example.taskmanagement_backend.entities.ProjectMember;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.ProjectJpaRepository;
import com.example.taskmanagement_backend.repositories.ProjectMemberJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service

public class ProjectMemberService {

    @Autowired
    private ProjectMemberJpaRepository projectMemberJpaRepository;
    @Autowired
    private  ProjectJpaRepository projectRepository;
    @Autowired
    private  UserJpaRepository userRepository;

    public List<ProjectMemberResponseDto> getMembersByProject(Long projectId) {
        return projectMemberJpaRepository.findByProjectId(projectId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    public ProjectMemberResponseDto createProjectMember(CreateProjectMemberRequestDto dto) {
        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + dto.getProjectId()));

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + dto.getUserId()));

        ProjectMember projectMember = ProjectMember.builder()
                .project(project)
                .user(user)
                .joinedAt(LocalDateTime.now())
                .build();

        return convertToDto(projectMemberJpaRepository.save(projectMember));
    }
    public void deleteProjectMember(Long id) {
        if (!projectMemberJpaRepository.existsById(id)) {
            throw new RuntimeException("Project member not found with id: " + id);
        }
        projectMemberJpaRepository.deleteById(id);
    }

    private ProjectMemberResponseDto convertToDto(ProjectMember entity) {
        return ProjectMemberResponseDto.builder()
                .id(entity.getId())
                .projectId(entity.getProject().getId())
                .userId(entity.getUser().getId())
                .joinedAt(entity.getJoinedAt())
                .build();
    }

}
