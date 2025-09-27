package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.ProjectInvitatinDto.CreateProjectInvitationRequestDto;
import com.example.taskmanagement_backend.dtos.ProjectInvitatinDto.ProjectInvitationResponseDto;
import com.example.taskmanagement_backend.dtos.ProjectInvitatinDto.UpdateProjectInvitationStatusRequestDto;
import com.example.taskmanagement_backend.dtos.TeamInvitationDto.CreateTeamInvitationRequestDto;
import com.example.taskmanagement_backend.dtos.TeamInvitationDto.TeamInvitationResponseDto;
import com.example.taskmanagement_backend.dtos.TeamInvitationDto.UpdateTeamInvitationStatusRequestDto;
import com.example.taskmanagement_backend.services.TeamInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/team-invitations")
@RequiredArgsConstructor
public class TeamInvitationController {
    private final TeamInvitationService teamInvitationService;

    @PostMapping
    public ResponseEntity<TeamInvitationResponseDto> createInvitation(
            @Valid @RequestBody CreateTeamInvitationRequestDto dto) {
        return ResponseEntity.ok(teamInvitationService.createTeamInvitation(dto));
    }
    @GetMapping("team/{projectId}")
    public ResponseEntity<List<TeamInvitationResponseDto>> getInvitationsByTeam(@PathVariable Long teamId) {
        return ResponseEntity.ok(teamInvitationService.getTeamInvitationsByTeam(teamId));
    }
    @PutMapping("/{invitationId}/status")
    public ResponseEntity<TeamInvitationResponseDto> updateStatus(
            @PathVariable Long invitationId,
            @Valid @RequestBody UpdateTeamInvitationStatusRequestDto dto) {
        return ResponseEntity.ok(teamInvitationService.updateStatus(invitationId, dto));
    }
}
