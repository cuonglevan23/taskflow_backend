package com.example.taskmanagement_backend.events;


import com.example.taskmanagement_backend.services.infrastructure.EmailService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectInvitationListener {

    private final EmailService emailService;

    @Async // chạy bất đồng bộ
    @EventListener
    public void handleProjectInvitation(ProjectInvitationCreatedEvent event) {
        try {
            emailService.sendInvitationEmail(
                    event.getEmail(),
                    event.getProjectName(),
                    event.getInviteLink()
            );
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}

//Publisher event, log, push queue redis
