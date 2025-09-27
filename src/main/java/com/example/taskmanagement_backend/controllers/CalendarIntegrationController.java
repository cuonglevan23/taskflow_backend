package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.CalendarIntegrationDto.*;
import com.example.taskmanagement_backend.services.CalendarIntegrationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calendar-integrations")
public class CalendarIntegrationController {

    @Autowired
    private CalendarIntegrationService calendarService;

    @PostMapping
    public CalendarIntegrationResponseDto create(@Valid @RequestBody CreateCalendarIntegrationRequestDto dto) {
        return calendarService.create(dto);
    }

    @PutMapping("/{id}")
    public CalendarIntegrationResponseDto update(@PathVariable Long id,
                                                 @Valid @RequestBody UpdateCalendarIntegrationRequestDto dto) {
        return calendarService.update(id, dto);
    }

    @GetMapping("/{id}")
    public CalendarIntegrationResponseDto getById(@PathVariable Long id) {
        return calendarService.getById(id);
    }

    @GetMapping
    public List<CalendarIntegrationResponseDto> getAll() {
        return calendarService.getAll();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        calendarService.delete(id);
    }

    @GetMapping("/user/{userId}")
    public List<CalendarIntegrationResponseDto> getByUserId(@PathVariable Long userId) {
        return calendarService.findByUserId(userId);
    }

}
