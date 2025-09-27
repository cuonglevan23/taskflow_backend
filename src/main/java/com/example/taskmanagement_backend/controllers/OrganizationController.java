package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.OrganizationDto.CreateOrganizationRequestDto;
import com.example.taskmanagement_backend.dtos.OrganizationDto.OrganizationResponseDto;
import com.example.taskmanagement_backend.dtos.OrganizationDto.UpdateOrganizationRequestDto;
import com.example.taskmanagement_backend.services.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/organizations")
public class OrganizationController {
    @Autowired
    private OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<List<OrganizationResponseDto>> getAllOrganizations() {
        return ResponseEntity.ok(organizationService.getAllOrganizations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrganizationsById(@PathVariable Long id) {
        OrganizationResponseDto org = organizationService.getOrganizationById(id);
        System.out.println("getOrganizationsById"+org);
        if (org == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Không tìm thấy tổ chức với ID: " + id));
        }
        return ResponseEntity.ok(org);
    }

    @PostMapping
    public ResponseEntity<OrganizationResponseDto> createOrganization(@Valid  @RequestBody CreateOrganizationRequestDto organization) {
        return ResponseEntity.ok(organizationService.createOrganization(organization));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrganizationResponseDto> updateOrganization(@Valid @PathVariable Long id,  @RequestBody UpdateOrganizationRequestDto organization) {
        return ResponseEntity.ok(organizationService.updateOrganizations(id, organization));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteOrganization(@PathVariable Long id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.ok("Deleted Organization with id: " + id);
    }
    @GetMapping("/by-owner/{ownerId}")
    public ResponseEntity<?> getOrganizationByOwnerId(@PathVariable Long ownerId) {
        OrganizationResponseDto org = organizationService.getOrganizationByOwnerId(ownerId);
        if (org == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Không tìm thấy tổ chức của owner với ID: " + ownerId));
        }
        return ResponseEntity.ok(org);
    }
}
