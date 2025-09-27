package com.example.taskmanagement_backend.dtos.EmailDto;

import com.example.taskmanagement_backend.enums.EmailDirection;
import com.example.taskmanagement_backend.enums.EmailStatus;
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
public class EmailResponseDto {

    private Long id;
    private String gmailMessageId;
    private String subject;
    private String body;
    private String snippet; // Gmail snippet for preview
    private EmailStatus status;
    private EmailDirection direction;
    private LocalDateTime sentAt;
    private String recipientEmail;
    private String senderEmail;
    private String threadId;
    private List<String> labelIds;
    private Boolean hasAttachments;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Sender/Recipient info
    private String fromName;
    private String fromEmail;
    private List<String> toEmails;
    private List<String> ccEmails;
    private List<String> bccEmails;

    // Attachment info
    private List<AttachmentDto> attachments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentDto {
        private String filename;
        private String mimeType;
        private Long size;
        private String attachmentId;
        private String downloadUrl;
    }
}
