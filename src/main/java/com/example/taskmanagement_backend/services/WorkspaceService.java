package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.entities.Team;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.TeamJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    
    private final TeamJpaRepository teamRepository;
    private final UserJpaRepository userRepository;

    /**
     * Create default workspace for new user
     */
    public Team createDefaultWorkspace(User user) {
        Team defaultWorkspace = Team.builder()
                .name("My Workspace")
                .description("Personal workspace for " + user.getEmail())
                .isDefaultWorkspace(true)

                .createdBy(user)
                .organization(user.getOrganization())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        Team savedWorkspace = teamRepository.save(defaultWorkspace);
        
        // Update user's default workspace reference
        user.setDefaultWorkspace(savedWorkspace);
        userRepository.save(user);
        
        return savedWorkspace;
    }

    /**
     * Get user's default workspace
     */
    public Team getUserDefaultWorkspace(User user) {
        if (user.getDefaultWorkspace() != null) {
            return user.getDefaultWorkspace();
        }
        
        // If no default workspace exists, create one
        return createDefaultWorkspace(user);
    }

    /**
     * Check if team is a default workspace
     */
    public boolean isDefaultWorkspace(Team team) {
        return team != null && team.isDefaultWorkspace();
    }
}