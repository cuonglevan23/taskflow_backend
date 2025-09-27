package com.example.taskmanagement_backend.dtos.NotificationDto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNotificationRequestDto {

    private Boolean isRead;
}
