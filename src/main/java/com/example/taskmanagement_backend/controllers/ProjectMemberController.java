package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.ProjectMemberDto.CreateProjectMemberRequestDto;
import com.example.taskmanagement_backend.dtos.ProjectMemberDto.ProjectMemberResponseDto;
import com.example.taskmanagement_backend.services.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/api/project-members")
@RequiredArgsConstructor
public class ProjectMemberController {
    private final ProjectMemberService projectMemberService;

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<ProjectMemberResponseDto>> getMembersByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectMemberService.getMembersByProject(projectId));
    }

    @PostMapping
    public ResponseEntity<ProjectMemberResponseDto> createMember(@RequestBody CreateProjectMemberRequestDto dto) {
        return ResponseEntity.ok(projectMemberService.createProjectMember(dto));
    }
//    @PutMapping("/{id}")
//    public ResponseEntity<ProjectMemberResponseDto> updateMember(
//            @PathVariable Long id,
//            @RequestBody UpdateProjectMemberRequestDto dto) {
//        return ResponseEntity.ok(projectMemberService.updateProjectMember(id, dto));
//    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id) {
        projectMemberService.deleteProjectMember(id);
        return ResponseEntity.noContent().build();
    }

}
