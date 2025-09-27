package com.example.taskmanagement_backend.services.infrastructure;

import com.example.taskmanagement_backend.dtos.EmailDto.SendEmailRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * SMTP Email Service using Spring Mail with Gmail App Password
 * This replaces Gmail OAuth2 API for simpler email sending
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Send email via SMTP using Gmail App Password
     */
    public void sendEmail(SendEmailRequestDto emailRequest) {
        try {
            log.info("📧 [SMTP] Sending email to: {} - Subject: {}",
                    String.join(", ", emailRequest.getTo()), emailRequest.getSubject());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                message,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name()
            );

            // Set email properties
            helper.setFrom(fromEmail, "TaskFlow System");
            helper.setTo(emailRequest.getTo().toArray(new String[0]));
            helper.setSubject(emailRequest.getSubject());

            // ✅ FIXED: Use getIsHtml() for Boolean wrapper type
            boolean isHtml = emailRequest.getIsHtml() != null ? emailRequest.getIsHtml() : false;
            helper.setText(emailRequest.getBody(), isHtml);

            // Add CC if provided
            if (emailRequest.getCc() != null && !emailRequest.getCc().isEmpty()) {
                helper.setCc(emailRequest.getCc().toArray(new String[0]));
            }

            // Add BCC if provided
            if (emailRequest.getBcc() != null && !emailRequest.getBcc().isEmpty()) {
                helper.setBcc(emailRequest.getBcc().toArray(new String[0]));
            }

            // Send email
            mailSender.send(message);

            log.info("✅ [SMTP] Email sent successfully to: {}", String.join(", ", emailRequest.getTo()));

        } catch (MessagingException e) {
            log.error("❌ [SMTP] Failed to create email message for {}: {}",
                    String.join(", ", emailRequest.getTo()), e.getMessage(), e);
            throw new RuntimeException("Failed to create email message", e);
        } catch (MailException e) {
            log.error("❌ [SMTP] Failed to send email to {}: {}",
                    String.join(", ", emailRequest.getTo()), e.getMessage(), e);
            throw new RuntimeException("Failed to send email via SMTP", e);
        } catch (Exception e) {
            log.error("❌ [SMTP] Unexpected error sending email to {}: {}",
                    String.join(", ", emailRequest.getTo()), e.getMessage(), e);
            throw new RuntimeException("Unexpected error in email service", e);
        }
    }

    /**
     * Send simple text email
     */
    public void sendSimpleEmail(String to, String subject, String body) {
        SendEmailRequestDto request = SendEmailRequestDto.builder()
                .to(Arrays.asList(to))
                .subject(subject)
                .body(body)
                .isHtml(false)
                .build();
        sendEmail(request);
    }

    /**
     * Send HTML email
     */
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        SendEmailRequestDto request = SendEmailRequestDto.builder()
                .to(Arrays.asList(to))
                .subject(subject)
                .body(htmlBody)
                .isHtml(true)
                .build();
        sendEmail(request);
    }

    /**
     * Check if email service is available
     */
    public boolean isEmailServiceAvailable() {
        try {
            // Test connection
            mailSender.createMimeMessage();
            return true;
        } catch (Exception e) {
            log.warn("⚠️ [SMTP] Email service not available: {}", e.getMessage());
            return false;
        }
    }
}
