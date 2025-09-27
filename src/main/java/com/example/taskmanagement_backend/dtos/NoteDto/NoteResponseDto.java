package com.example.taskmanagement_backend.dtos.NoteDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteResponseDto {

    private Long id;
    private String title;
    private String content; // JSON content
    private String description;

    // Owner information
    private Long userId; // If personal note
    private String userName;
    private String userEmail;

    private Long projectId; // If project note
    private String projectName;

    // Creator information
    private Long creatorId;
    private String creatorName;
    private String creatorEmail;

    private Boolean isPublic;
    private Boolean isArchived;
    private Boolean isPersonalNote;
    private Boolean isProjectNote;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Attachment information
    private List<NoteAttachmentResponseDto> attachments;
    private Integer attachmentCount;
    private Long totalAttachmentSize;
    private String formattedTotalAttachmentSize;
    private Boolean hasAttachments;

    // Nested DTOs for detailed information
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfoDto {
        private Long id;
        private String name;
        private String email;
        private String username;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectInfoDto {
        private Long id;
        private String name;
        private String description;
        private Long teamId;
        private String teamName;
    }
}
