package com.example.taskmanagement_backend.events;

import lombok.Data;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ProjectInvitationCreatedEvent extends ApplicationEvent {
    private final String email;
    private final String projectName;
    private final String inviteLink;

    public ProjectInvitationCreatedEvent(Object source, String email, String projectName, String inviteLink) {
        super(source);
        this.email = email;
        this.projectName = projectName;
        this.inviteLink = inviteLink;
    }
}
