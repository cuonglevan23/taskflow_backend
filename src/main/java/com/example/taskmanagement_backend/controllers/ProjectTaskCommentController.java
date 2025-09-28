package com.example.taskmanagement_backend.controllers;

import com.example.taskmanagement_backend.dtos.TaskCommentDto.CreateTaskCommentRequestDto;
import com.example.taskmanagement_backend.dtos.TaskCommentDto.TaskCommentResponseDto;
import com.example.taskmanagement_backend.dtos.TaskCommentDto.UpdateTaskCommentRequestDto;
import com.example.taskmanagement_backend.services.ProjectTaskCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/project-task-comments")
@RequiredArgsConstructor
@CrossOrigin(origins = {"https://main.d2az19adxqfdf3.amplifyapp.com", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class ProjectTaskCommentController {

    private final ProjectTaskCommentService projectTaskCommentService;

    /**
     * Tạo comment mới cho project task
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<TaskCommentResponseDto> createProjectTaskComment(
            @Valid @RequestBody CreateTaskCommentRequestDto createDto) {

        TaskCommentResponseDto comment = projectTaskCommentService.createComment(createDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    /**
     * Lấy tất cả comments của một project task
     */
    @GetMapping("/project-task/{projectTaskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TaskCommentResponseDto>> getCommentsByProjectTask(
            @PathVariable Long projectTaskId) {

        List<TaskCommentResponseDto> comments = projectTaskCommentService.getCommentsByProjectTask(projectTaskId);
        return ResponseEntity.ok(comments);
    }

    /**
     * Lấy comments của project task với phân trang
     */
    @GetMapping("/project-task/{projectTaskId}/paginated")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Page<TaskCommentResponseDto>> getCommentsByProjectTaskPaginated(
            @PathVariable Long projectTaskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<TaskCommentResponseDto> comments = projectTaskCommentService.getCommentsByProjectTaskPaginated(
                projectTaskId, page, size);
        return ResponseEntity.ok(comments);
    }

    /**
     * Đếm số comments của một project task
     */
    @GetMapping("/project-task/{projectTaskId}/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Long> getCommentCountByProjectTask(@PathVariable Long projectTaskId) {
        long count = projectTaskCommentService.getCommentCountByProjectTask(projectTaskId);
        return ResponseEntity.ok(count);
    }

    /**
     * Lấy comment theo ID
     */
    @GetMapping("/{commentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<TaskCommentResponseDto> getCommentById(@PathVariable Long commentId) {
        TaskCommentResponseDto comment = projectTaskCommentService.getCommentById(commentId);
        return ResponseEntity.ok(comment);
    }

    /**
     * Cập nhật comment (chỉ người tạo comment)
     */
    @PutMapping("/{commentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<TaskCommentResponseDto> updateProjectTaskComment(
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateTaskCommentRequestDto updateDto) {

        TaskCommentResponseDto updatedComment = projectTaskCommentService.updateComment(commentId, updateDto);
        return ResponseEntity.ok(updatedComment);
    }

    /**
     * Xóa comment (người tạo comment hoặc creator của project task)
     */
    @DeleteMapping("/{commentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<Void> deleteProjectTaskComment(@PathVariable Long commentId) {
        projectTaskCommentService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lấy comments gần đây của project task (5 comments mới nhất)
     */
    @GetMapping("/project-task/{projectTaskId}/recent")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TaskCommentResponseDto>> getRecentCommentsByProjectTask(
            @PathVariable Long projectTaskId) {

        List<TaskCommentResponseDto> recentComments = projectTaskCommentService.getRecentCommentsByProjectTask(projectTaskId);
        return ResponseEntity.ok(recentComments);
    }

    /**
     * Lấy comments của user hiện tại trong project task
     */
    @GetMapping("/project-task/{projectTaskId}/my-comments")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TaskCommentResponseDto>> getCurrentUserCommentsByProjectTask(
            @PathVariable Long projectTaskId) {

        List<TaskCommentResponseDto> userComments = projectTaskCommentService.getCurrentUserCommentsByProjectTask(projectTaskId);
        return ResponseEntity.ok(userComments);
    }

    /**
     * Tìm kiếm comments trong project task theo nội dung
     */
    @GetMapping("/project-task/{projectTaskId}/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'LEADER', 'MEMBER')")
    public ResponseEntity<List<TaskCommentResponseDto>> searchCommentsInProjectTask(
            @PathVariable Long projectTaskId,
            @RequestParam String keyword) {

        List<TaskCommentResponseDto> searchResults = projectTaskCommentService.searchCommentsInProjectTask(projectTaskId, keyword);
        return ResponseEntity.ok(searchResults);
    }
}
