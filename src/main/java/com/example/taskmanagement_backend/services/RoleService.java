package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.RoleDto.RoleResponseDto;
import com.example.taskmanagement_backend.entities.Role;
import com.example.taskmanagement_backend.repositories.RoleJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleService {
    @Autowired
    private RoleJpaRepository roleJpaRepository;
    public RoleService(RoleJpaRepository roleJpaRepository) {
        this.roleJpaRepository = roleJpaRepository;
    }
    public List<RoleResponseDto> getAllRoles() {
        return roleJpaRepository.findAll().stream().map(this::convertToDto).collect(Collectors.toList());
    }
    private RoleResponseDto convertToDto(Role role) {
        RoleResponseDto roleResponseDto = new RoleResponseDto();
        roleResponseDto.setId(role.getId());
        roleResponseDto.setRoleName(role.getName());
        return roleResponseDto;
    }
}
