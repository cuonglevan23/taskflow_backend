package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.TaskChecklistDto.CreateTaskChecklistRequestDto;
import com.example.taskmanagement_backend.dtos.TaskChecklistDto.UpdateTaskChecklistRequestDto;
import com.example.taskmanagement_backend.dtos.TaskChecklistDto.TaskChecklistResponseDto;
import com.example.taskmanagement_backend.entities.Task;
import com.example.taskmanagement_backend.entities.TaskChecklist;
import com.example.taskmanagement_backend.repositories.TaskChecklistJpaRepository;
import com.example.taskmanagement_backend.repositories.TaskJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskChecklistService {

    @Autowired
    private TaskChecklistJpaRepository checklistRepo;

    @Autowired
    private TaskJpaRepository taskRepo;

    public TaskChecklistResponseDto create(CreateTaskChecklistRequestDto dto) {
        Task task = taskRepo.findById(dto.getTaskId())
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        TaskChecklist checklist = TaskChecklist.builder()
                .task(task)
                .item(dto.getItem())
                .isCompleted(false)
                .createdAt(LocalDateTime.now())
                .build();

        return toDto(checklistRepo.save(checklist));
    }

    public TaskChecklistResponseDto update(Long id, UpdateTaskChecklistRequestDto dto) {
        TaskChecklist checklist = checklistRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Checklist not found"));

        if (dto.getItem() != null) checklist.setItem(dto.getItem());
        if (dto.getIsCompleted() != null) checklist.setIsCompleted(dto.getIsCompleted());

        return toDto(checklistRepo.save(checklist));
    }

    public TaskChecklistResponseDto getById(Long id) {
        return checklistRepo.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Checklist not found"));
    }

    public List<TaskChecklistResponseDto> getAll() {
        return checklistRepo.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public void delete(Long id) {
        if (!checklistRepo.existsById(id)) {
            throw new EntityNotFoundException("Checklist not found");
        }
        checklistRepo.deleteById(id);
    }

    private TaskChecklistResponseDto toDto(TaskChecklist checklist) {
        return TaskChecklistResponseDto.builder()
                .id(checklist.getId())
                .taskId(checklist.getTask().getId())
                .item(checklist.getItem())
                .isCompleted(checklist.getIsCompleted())
                .createdAt(checklist.getCreatedAt())
                .build();
    }

    public List<TaskChecklistResponseDto> getByTaskId(Long taskId) {
        return checklistRepo.findByTaskId(taskId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

}
