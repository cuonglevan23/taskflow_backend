package com.example.taskmanagement_backend.dtos.GroupMemberDto;


import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberResponseDto {

    private Long id;

    private Long groupId;

    private Long userId;

    private LocalDateTime joinedAt;
}