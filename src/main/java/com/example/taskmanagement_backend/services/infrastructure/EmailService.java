package com.example.taskmanagement_backend.services.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async // gửi email bất đồng bộ
    public void sendInvitationEmail(String to, String projectName, String inviteLink) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Lời mời tham gia dự án: " + projectName);

        String htmlContent = String.format(
                "<!DOCTYPE html>" +
                        "<html>" +
                        "<body style='font-family: Arial, sans-serif;'>" +
                        "<p>Xin chào,</p>" +
                        "<p>Bạn được mời tham gia dự án <strong>%s</strong>.</p>" +
                        "<p>Vui lòng bấm vào liên kết sau để chấp nhận lời mời:</p>" +
                        "<p><a href='%s' style='color: blue; text-decoration: underline;'>Chấp nhận lời mời</a></p>" +
                        "<br>" +
                        "<p>Trân trọng,<br>Hệ thống Quản lý Dự án</p>" +
                        "</body>" +
                        "</html>",
                projectName, inviteLink
        );

        helper.setText(htmlContent, true); // true = gửi HTML
        mailSender.send(message);
    }
}
