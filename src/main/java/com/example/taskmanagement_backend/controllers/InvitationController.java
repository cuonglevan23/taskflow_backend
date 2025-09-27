package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.entities.ProjectInvitation;
import com.example.taskmanagement_backend.entities.ProjectMember;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.InvitationStatus;
import com.example.taskmanagement_backend.repositories.ProjectInvitationRepository;
import com.example.taskmanagement_backend.repositories.ProjectMemberJpaRepository;
import com.example.taskmanagement_backend.repositories.TeamMemberJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import com.example.taskmanagement_backend.services.ProjectInvitationService;
import com.example.taskmanagement_backend.services.TeamInvitationService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final ProjectInvitationService projectInvitationService;
    private final TeamInvitationService teamInvitationService;
    @GetMapping("/accept-project")
    public ResponseEntity<String> acceptInvitationToProject(@RequestParam String token) {
        String result = projectInvitationService.acceptInvitation(token);
        return ResponseEntity.ok(result);
    }
    @GetMapping("/accept-team")
    public ResponseEntity<String> acceptInvitationToTeam(@RequestParam String token) {
        String result = teamInvitationService.acceptInvitation(token);
        return ResponseEntity.ok(result);
    }

}
