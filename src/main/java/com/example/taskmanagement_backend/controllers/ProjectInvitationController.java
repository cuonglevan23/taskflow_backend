package com.example.taskmanagement_backend.controllers;



import com.example.taskmanagement_backend.dtos.ProjectInvitatinDto.CreateProjectInvitationRequestDto;
import com.example.taskmanagement_backend.dtos.ProjectInvitatinDto.ProjectInvitationResponseDto;
import com.example.taskmanagement_backend.dtos.ProjectInvitatinDto.UpdateProjectInvitationStatusRequestDto;
import com.example.taskmanagement_backend.services.ProjectInvitationService;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/project-invitations")
@RequiredArgsConstructor
public class ProjectInvitationController {

    private final ProjectInvitationService invitationService;




    @PostMapping
    public ResponseEntity<ProjectInvitationResponseDto> createInvitation(
            @Valid @RequestBody CreateProjectInvitationRequestDto dto) throws MessagingException {
        return ResponseEntity.ok(invitationService.createInvitation(dto));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<ProjectInvitationResponseDto>> getInvitationsByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(invitationService.getInvitationsByProject(projectId));
    }

    @PutMapping("/{invitationId}/status")
    public ResponseEntity<ProjectInvitationResponseDto> updateStatus(
            @PathVariable Long invitationId,
            @Valid @RequestBody UpdateProjectInvitationStatusRequestDto dto) {
        return ResponseEntity.ok(invitationService.updateStatus(invitationId, dto));
    }
}