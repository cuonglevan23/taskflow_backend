package com.example.taskmanagement_backend.controllers;// package com.example.taskmanagement_backend.controllers;

 import com.example.taskmanagement_backend.dtos.TaskChecklistDto.CreateTaskChecklistRequestDto;
 import com.example.taskmanagement_backend.dtos.TaskChecklistDto.UpdateTaskChecklistRequestDto;
 import com.example.taskmanagement_backend.dtos.TaskChecklistDto.TaskChecklistResponseDto;
 import com.example.taskmanagement_backend.services.TaskChecklistService;
 import jakarta.validation.Valid;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.web.bind.annotation.*;

 import java.util.List;

 @RestController
 @RequestMapping("/api/task-checklists")
 public class TaskChecklistController {

     @Autowired
     private TaskChecklistService checklistService;

     @PostMapping
     public TaskChecklistResponseDto create(@Valid @RequestBody CreateTaskChecklistRequestDto dto) {
         return checklistService.create(dto);
     }

     @PutMapping("/{id}")
     public TaskChecklistResponseDto update(@PathVariable Long id,
                                            @RequestBody UpdateTaskChecklistRequestDto dto) {
         return checklistService.update(id, dto);
     }

     @GetMapping("/{id}")
     public TaskChecklistResponseDto getById(@PathVariable Long id) {
         return checklistService.getById(id);
     }

     @GetMapping
     public List<TaskChecklistResponseDto> getAll() {
         return checklistService.getAll();
     }

     @DeleteMapping("/{id}")
     public void delete(@PathVariable Long id) {
         checklistService.delete(id);
     }

     @GetMapping("/task/{taskId}")
     public List<TaskChecklistResponseDto> getByTaskId(@PathVariable Long taskId) {
         return checklistService.getByTaskId(taskId);
     }

 }
