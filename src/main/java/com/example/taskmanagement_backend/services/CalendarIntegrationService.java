package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.CalendarIntegrationDto.*;
import com.example.taskmanagement_backend.entities.CalendarIntegration;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.CalendarIntegrationJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CalendarIntegrationService {

    @Autowired
    private CalendarIntegrationJpaRepository calendarRepo;

    @Autowired
    private UserJpaRepository userRepo;

    public CalendarIntegrationResponseDto create(CreateCalendarIntegrationRequestDto dto) {
        User user = userRepo.findById(dto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        CalendarIntegration integration = CalendarIntegration.builder()
                .user(user)
                .provider(dto.getProvider())
                .accessToken(dto.getAccessToken())
                .refreshToken(dto.getRefreshToken())
                .expiresAt(dto.getExpiresAt())
                .createdAt(LocalDateTime.now())
                .build();

        return toDto(calendarRepo.save(integration));
    }

    public CalendarIntegrationResponseDto update(Long id, UpdateCalendarIntegrationRequestDto dto) {
        CalendarIntegration integration = calendarRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found"));

        integration.setAccessToken(dto.getAccessToken());
        integration.setRefreshToken(dto.getRefreshToken());
        integration.setExpiresAt(dto.getExpiresAt());

        return toDto(calendarRepo.save(integration));
    }

    public CalendarIntegrationResponseDto getById(Long id) {
        return calendarRepo.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found"));
    }

    public List<CalendarIntegrationResponseDto> getAll() {
        return calendarRepo.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public void delete(Long id) {
        if (!calendarRepo.existsById(id)) {
            throw new EntityNotFoundException("Integration not found");
        }
        calendarRepo.deleteById(id);
    }

    private CalendarIntegrationResponseDto toDto(CalendarIntegration entity) {
        return CalendarIntegrationResponseDto.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .provider(entity.getProvider())
                .accessToken(entity.getAccessToken())
                .refreshToken(entity.getRefreshToken())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public List<CalendarIntegrationResponseDto> findByUserId(Long userId) {
        return calendarRepo.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

}
