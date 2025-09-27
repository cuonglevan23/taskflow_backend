package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.RoleDto.RoleResponseDto;
import com.example.taskmanagement_backend.repositories.RoleJpaRepository;
import com.example.taskmanagement_backend.services.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {
    @Autowired
    private RoleService roleService;

    @GetMapping
    public ResponseEntity<List<RoleResponseDto>> getRole() {
        return ResponseEntity.ok().body(roleService.getAllRoles());
    }
}
