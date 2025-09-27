package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.TeamMemberDto.AddTeamMemberByEmailRequestDto;
import com.example.taskmanagement_backend.dtos.TeamMemberDto.CreateTeamMemberRequestDto;
import com.example.taskmanagement_backend.dtos.TeamMemberDto.TeamMemberResponseDto;
import com.example.taskmanagement_backend.services.TeamMemberService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/api/team-members")
@RequiredArgsConstructor
public class TeamMemberController {
    private final TeamMemberService teamMemberService;

    @PostMapping
    public ResponseEntity<TeamMemberResponseDto> createMember(@RequestBody @Valid CreateTeamMemberRequestDto dto){
        return ResponseEntity.ok(teamMemberService.createTeamMember(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id) {
        teamMemberService.deleteTeamMember(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/add")
    public ResponseEntity<?> addMember(@Valid @RequestBody AddTeamMemberByEmailRequestDto dto) {
        try {
            return ResponseEntity.ok(teamMemberService.addMember(dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
