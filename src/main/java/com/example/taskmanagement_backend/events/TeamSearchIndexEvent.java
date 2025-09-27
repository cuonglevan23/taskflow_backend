package com.example.taskmanagement_backend.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Team-specific search index event
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TeamSearchIndexEvent extends SearchIndexEvent {
    private Long teamId;
    private String name;
    private String description;
    private Long leaderId;

    public TeamSearchIndexEvent(String eventType, Long teamId, Long userId) {
        super(eventType, "TEAM", teamId.toString(), userId);
        this.teamId = teamId;
    }
}
