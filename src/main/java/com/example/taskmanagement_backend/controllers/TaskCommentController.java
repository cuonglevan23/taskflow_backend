package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.TaskCommentDto.CreateTaskCommentRequestDto;
import com.example.taskmanagement_backend.dtos.TaskCommentDto.TaskCommentResponseDto;
import com.example.taskmanagement_backend.dtos.TaskCommentDto.UpdateTaskCommentRequestDto;
import com.example.taskmanagement_backend.services.TaskCommentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/task-comments")
public class TaskCommentController {

    @Autowired
    private TaskCommentService taskCommentService;

    /**
     * Tạo comment mới cho task
     */
    @PostMapping
    public ResponseEntity<TaskCommentResponseDto> createComment(@Valid @RequestBody CreateTaskCommentRequestDto dto) {
        TaskCommentResponseDto comment = taskCommentService.createComment(dto);
        return ResponseEntity.ok(comment);
    }

    /**
     * Lấy tất cả comments của 1 task
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<TaskCommentResponseDto>> getCommentsByTask(@PathVariable Long taskId) {
        List<TaskCommentResponseDto> comments = taskCommentService.getCommentsByTask(taskId);
        return ResponseEntity.ok(comments);
    }

    /**
     * Lấy comments của task với phân trang
     */
    @GetMapping("/task/{taskId}/paginated")
    public ResponseEntity<Page<TaskCommentResponseDto>> getCommentsByTaskPaginated(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<TaskCommentResponseDto> comments = taskCommentService.getCommentsByTaskPaginated(taskId, page, size);
        return ResponseEntity.ok(comments);
    }

    /**
     * Cập nhật comment
     */
    @PutMapping("/{commentId}")
    public ResponseEntity<TaskCommentResponseDto> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateTaskCommentRequestDto dto) {
        TaskCommentResponseDto updatedComment = taskCommentService.updateComment(commentId, dto);
        return ResponseEntity.ok(updatedComment);
    }

    /**
     * Xóa comment
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<String> deleteComment(@PathVariable Long commentId) {
        taskCommentService.deleteComment(commentId);
        return ResponseEntity.ok("Comment deleted successfully");
    }

    /**
     * Đếm số comments của 1 task
     */
    @GetMapping("/task/{taskId}/count")
    public ResponseEntity<Map<String, Long>> getCommentCount(@PathVariable Long taskId) {
        long count = taskCommentService.getCommentCountByTask(taskId);
        return ResponseEntity.ok(Map.of("commentCount", count));
    }
}
