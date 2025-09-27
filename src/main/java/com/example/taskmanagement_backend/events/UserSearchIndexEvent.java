package com.example.taskmanagement_backend.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * User-specific search index event
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UserSearchIndexEvent extends SearchIndexEvent {
    private Long userIdEntity;
    private String email;
    private String firstName;
    private String lastName;
    private String username;

    public UserSearchIndexEvent(String eventType, Long userIdEntity, Long triggeredByUserId) {
        super(eventType, "USER", userIdEntity.toString(), triggeredByUserId);
        this.userIdEntity = userIdEntity;
    }
}
