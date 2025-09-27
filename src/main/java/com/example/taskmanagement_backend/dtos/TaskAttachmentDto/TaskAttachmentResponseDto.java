package com.example.taskmanagement_backend.dtos.TaskAttachmentDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAttachmentResponseDto {

    private Long id;
    private Long taskId;
    private String fileKey;
    private String originalFilename;
    private Long fileSize;
    private String contentType;
    private String downloadUrl;
    private String uploadedBy;
    private String uploadedByEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Helper methods for frontend display
    public String getFileSizeFormatted() {
        if (fileSize == null) return "0 B";

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = fileSize.doubleValue();

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.1f %s", size, units[unitIndex]);
    }

    public String getFileExtension() {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
    }

    public String getFileIcon() {
        String extension = getFileExtension();
        switch (extension) {
            case "pdf": return "📄";
            case "doc":
            case "docx": return "📝";
            case "xls":
            case "xlsx": return "📊";
            case "ppt":
            case "pptx": return "📈";
            case "jpg":
            case "jpeg":
            case "png":
            case "gif": return "🖼️";
            case "mp4":
            case "avi":
            case "mov": return "🎥";
            case "mp3":
            case "wav": return "🎵";
            case "zip":
            case "rar": return "🗜️";
            default: return "📎";
        }
    }
}
