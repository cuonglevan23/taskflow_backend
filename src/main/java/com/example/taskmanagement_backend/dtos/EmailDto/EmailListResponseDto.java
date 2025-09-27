package com.example.taskmanagement_backend.dtos.EmailDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailListResponseDto {

    private List<EmailResponseDto> emails;
    private Long totalElements;
    private Integer totalPages;
    private Integer currentPage;
    private Integer pageSize;
    private Boolean hasNext;
    private Boolean hasPrevious;
    private Long unreadCount;

    // Search/Filter metadata
    private String searchQuery;
    private String filterBy; // INBOX, SENT, DRAFT, etc.
}
