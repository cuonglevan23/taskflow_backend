package com.example.taskmanagement_backend.services.infrastructure;

import com.example.taskmanagement_backend.dtos.EmailDto.EmailListResponseDto;
import com.example.taskmanagement_backend.dtos.EmailDto.EmailResponseDto;
import com.example.taskmanagement_backend.dtos.EmailDto.SendEmailRequestDto;
import com.example.taskmanagement_backend.entities.EmailLog;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.EmailDirection;
import com.example.taskmanagement_backend.enums.EmailStatus;
import com.example.taskmanagement_backend.exceptions.HttpException;
import com.example.taskmanagement_backend.repositories.EmailLogRepository;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "TaskFlow Management";
    private static final List<String> SCOPES = Arrays.asList(
            "https://www.googleapis.com/auth/gmail.readonly",
            "https://www.googleapis.com/auth/gmail.send",
            "https://www.googleapis.com/auth/gmail.compose",
            "https://www.googleapis.com/auth/gmail.modify"
    );

    private final EmailLogRepository emailLogRepository;
    private final GmailOAuth2TokenService gmailOAuth2TokenService;

    /**
     * Send email via Gmail API
     */
    @Transactional
    public EmailResponseDto sendEmail(User user, SendEmailRequestDto request) {
        try {
            log.info("📧 Sending email from user: {} to: {}", user.getEmail(), String.join(", ", request.getTo()));

            Gmail gmailService = buildGmailService(user);

            // Create MIME message
            MimeMessage mimeMessage = createMimeMessage(request, user.getEmail());

            // Convert to Gmail message
            Message gmailMessage = createGmailMessage(mimeMessage);

            // Send via Gmail API
            Message sentMessage = gmailService.users().messages()
                    .send("me", gmailMessage)
                    .execute();

            log.info("✅ Email sent successfully with Gmail ID: {}", sentMessage.getId());

            // Save to database - join multiple recipients with semicolon
            EmailLog emailLog = EmailLog.builder()
                    .user(user)
                    .gmailMessageId(sentMessage.getId())
                    .subject(request.getSubject())
                    .body(request.getBody())
                    .status(EmailStatus.SENT)
                    .direction(EmailDirection.OUT)
                    .sentAt(LocalDateTime.now())
                    .recipientEmail(String.join("; ", request.getTo()))
                    .senderEmail(user.getEmail())
                    .threadId(sentMessage.getThreadId())
                    .hasAttachments(request.getAttachmentPaths() != null && !request.getAttachmentPaths().isEmpty())
                    .build();

            emailLog = emailLogRepository.save(emailLog);

            return convertToEmailResponseDto(emailLog);

        } catch (Exception e) {
            log.error("❌ Failed to send email: {}", e.getMessage(), e);

            // Save failed email log
            EmailLog failedLog = EmailLog.builder()
                    .user(user)
                    .subject(request.getSubject())
                    .body(request.getBody())
                    .status(EmailStatus.FAILED)
                    .direction(EmailDirection.OUT)
                    .sentAt(LocalDateTime.now())
                    .recipientEmail(String.join("; ", request.getTo()))
                    .senderEmail(user.getEmail())
                    .errorMessage(e.getMessage())
                    .hasAttachments(request.getAttachmentPaths() != null && !request.getAttachmentPaths().isEmpty())
                    .build();

            emailLogRepository.save(failedLog);

            throw new HttpException(
                    "Failed to send email: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Fetch emails from Gmail inbox
     */
    @Cacheable(value = "emails", key = "#user.id + '_' + #pageable.pageNumber + '_' + #labelId")
    public EmailListResponseDto getEmails(User user, Pageable pageable, String labelId) {
        try {
            log.info("📬 Fetching emails for user: {} with label: {}", user.getEmail(), labelId);

            // Special handling for SENT emails - check database first
            if ("SENT".equals(labelId)) {
                return getSentEmailsFromDatabase(user, pageable);
            }

            Gmail gmailService = buildGmailService(user);

            // Build query
            Gmail.Users.Messages.List request = gmailService.users().messages().list("me");
            if (labelId != null && !labelId.isEmpty()) {
                request.setLabelIds(Collections.singletonList(labelId));
            }

            // Set pagination
            request.setMaxResults((long) pageable.getPageSize());

            ListMessagesResponse response = request.execute();
            log.info("📬 Gmail API response for label {}: {} messages", labelId,
                    response.getMessages() != null ? response.getMessages().size() : 0);

            if (response.getMessages() == null) {
                log.warn("⚠️ No messages returned from Gmail API for label: {}", labelId);
                return EmailListResponseDto.builder()
                        .emails(Collections.emptyList())
                        .totalElements(0L)
                        .totalPages(0)
                        .currentPage(pageable.getPageNumber())
                        .pageSize(pageable.getPageSize())
                        .hasNext(false)
                        .hasPrevious(false)
                        .unreadCount(0L)
                        .filterBy(labelId)
                        .build();
            }

            // Fetch detailed message info
            List<EmailResponseDto> emails = new ArrayList<>();
            for (Message messageRef : response.getMessages()) {
                try {
                    Message fullMessage = gmailService.users().messages()
                            .get("me", messageRef.getId())
                            .execute();

                    EmailResponseDto emailDto = convertGmailMessageToDto(fullMessage, user);
                    emails.add(emailDto);

                    // Cache in database if not exists
                    cacheEmailInDatabase(user, fullMessage);

                } catch (Exception e) {
                    log.warn("⚠️ Failed to fetch message {}: {}", messageRef.getId(), e.getMessage());
                }
            }

            // Get unread count
            Long unreadCount = getUnreadCount(user);

            log.info("✅ Successfully fetched {} emails for label: {}", emails.size(), labelId);

            return EmailListResponseDto.builder()
                    .emails(emails)
                    .totalElements((long) emails.size())
                    .totalPages(1) // Gmail API doesn't provide total count easily
                    .currentPage(pageable.getPageNumber())
                    .pageSize(pageable.getPageSize())
                    .hasNext(response.getNextPageToken() != null)
                    .hasPrevious(false)
                    .unreadCount(unreadCount)
                    .filterBy(labelId)
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to fetch emails: {}", e.getMessage(), e);
            throw new HttpException(
                    "Failed to fetch emails: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get sent emails from database (fallback for SENT label)
     */
    private EmailListResponseDto getSentEmailsFromDatabase(User user, Pageable pageable) {
        try {
            log.info("📤 Fetching sent emails from database for user: {}", user.getEmail());

            Page<EmailLog> sentEmails = emailLogRepository.findSentEmailsByUser(user, pageable);

            List<EmailResponseDto> emails = sentEmails.getContent().stream()
                    .map(this::convertToEmailResponseDto)
                    .collect(Collectors.toList());

            log.info("✅ Found {} sent emails in database for user: {}", emails.size(), user.getEmail());

            return EmailListResponseDto.builder()
                    .emails(emails)
                    .totalElements(sentEmails.getTotalElements())
                    .totalPages(sentEmails.getTotalPages())
                    .currentPage(pageable.getPageNumber())
                    .pageSize(pageable.getPageSize())
                    .hasNext(sentEmails.hasNext())
                    .hasPrevious(sentEmails.hasPrevious())
                    .unreadCount(0L) // Sent emails are always "read"
                    .filterBy("SENT")
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to fetch sent emails from database: {}", e.getMessage(), e);

            // Return empty result instead of throwing exception
            return EmailListResponseDto.builder()
                    .emails(Collections.emptyList())
                    .totalElements(0L)
                    .totalPages(0)
                    .currentPage(pageable.getPageNumber())
                    .pageSize(pageable.getPageSize())
                    .hasNext(false)
                    .hasPrevious(false)
                    .unreadCount(0L)
                    .filterBy("SENT")
                    .build();
        }
    }

    /**
     * Get specific email by Gmail message ID
     */
    public EmailResponseDto getEmailById(User user, String messageId) {
        try {
            log.info("📧 Fetching email: {} for user: {}", messageId, user.getEmail());

            // First check database cache
            Optional<EmailLog> cachedEmail = emailLogRepository.findByGmailMessageId(messageId);
            if (cachedEmail.isPresent()) {
                return convertToEmailResponseDto(cachedEmail.get());
            }

            // Fetch from Gmail API
            Gmail gmailService = buildGmailService(user);
            Message message = gmailService.users().messages().get("me", messageId).execute();

            EmailResponseDto emailDto = convertGmailMessageToDto(message, user);

            // Cache in database
            cacheEmailInDatabase(user, message);

            return emailDto;

        } catch (Exception e) {
            log.error("❌ Failed to fetch email {}: {}", messageId, e.getMessage());
            throw new HttpException("Email not found: " + e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Search emails
     */
    public EmailListResponseDto searchEmails(User user, String query, Pageable pageable) {
        try {
            log.info("🔍 Searching emails for user: {} with query: {}", user.getEmail(), query);

            // First search in database cache
            Page<EmailLog> cachedResults = emailLogRepository.searchEmailsByContent(user, query, pageable);

            if (!cachedResults.isEmpty()) {
                List<EmailResponseDto> emails = cachedResults.getContent().stream()
                        .map(this::convertToEmailResponseDto)
                        .collect(Collectors.toList());

                return EmailListResponseDto.builder()
                        .emails(emails)
                        .totalElements(cachedResults.getTotalElements())
                        .totalPages(cachedResults.getTotalPages())
                        .currentPage(pageable.getPageNumber())
                        .pageSize(pageable.getPageSize())
                        .hasNext(cachedResults.hasNext())
                        .hasPrevious(cachedResults.hasPrevious())
                        .searchQuery(query)
                        .build();
            }

            // If no cached results, search Gmail API
            Gmail gmailService = buildGmailService(user);
            Gmail.Users.Messages.List request = gmailService.users().messages().list("me");
            request.setQ(query);
            request.setMaxResults((long) pageable.getPageSize());

            ListMessagesResponse response = request.execute();

            if (response.getMessages() == null) {
                return EmailListResponseDto.builder()
                        .emails(Collections.emptyList())
                        .totalElements(0L)
                        .totalPages(0)
                        .currentPage(pageable.getPageNumber())
                        .pageSize(pageable.getPageSize())
                        .hasNext(false)
                        .hasPrevious(false)
                        .searchQuery(query)
                        .build();
            }

            // Fetch detailed messages
            List<EmailResponseDto> emails = new ArrayList<>();
            for (Message messageRef : response.getMessages()) {
                try {
                    Message fullMessage = gmailService.users().messages()
                            .get("me", messageRef.getId())
                            .execute();

                    EmailResponseDto emailDto = convertGmailMessageToDto(fullMessage, user);
                    emails.add(emailDto);

                    // Cache in database
                    cacheEmailInDatabase(user, fullMessage);

                } catch (Exception e) {
                    log.warn("⚠️ Failed to fetch search result {}: {}", messageRef.getId(), e.getMessage());
                }
            }

            return EmailListResponseDto.builder()
                    .emails(emails)
                    .totalElements((long) emails.size())
                    .totalPages(1)
                    .currentPage(pageable.getPageNumber())
                    .pageSize(pageable.getPageSize())
                    .hasNext(response.getNextPageToken() != null)
                    .hasPrevious(false)
                    .searchQuery(query)
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to search emails: {}", e.getMessage(), e);
            throw new HttpException(
                    "Failed to search emails: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Helper methods...

    private Gmail buildGmailService(User user) throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        try {
            // Get access token for Gmail API
            String accessToken = gmailOAuth2TokenService.getValidAccessToken(user.getEmail());
            GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));

            return new Gmail.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to build Gmail service for user {}: {}", user.getEmail(), e.getMessage());
            throw new HttpException(
                    "Gmail OAuth2 token not available for user: " + user.getEmail() +
                    ". Please complete Gmail authorization first.",
                    HttpStatus.UNAUTHORIZED);
        }
    }

    private MimeMessage createMimeMessage(SendEmailRequestDto request, String fromEmail) throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(fromEmail));

        // Handle multiple recipients
        for (String recipient : request.getTo()) {
            email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(recipient));
        }

        if (request.getCc() != null) {
            for (String cc : request.getCc()) {
                email.addRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(cc));
            }
        }

        if (request.getBcc() != null) {
            for (String bcc : request.getBcc()) {
                email.addRecipient(jakarta.mail.Message.RecipientType.BCC, new InternetAddress(bcc));
            }
        }

        email.setSubject(request.getSubject());

        if (request.getIsHtml()) {
            email.setContent(request.getBody(), "text/html");
        } else {
            email.setText(request.getBody());
        }

        return email;
    }

    private Message createGmailMessage(MimeMessage mimeMessage) throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.getEncoder().encodeToString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    private EmailResponseDto convertGmailMessageToDto(Message message, User user) {
        MessagePart payload = message.getPayload();
        String subject = "";
        String fromEmail = "";
        String fromName = "";

        if (payload.getHeaders() != null) {
            for (MessagePartHeader header : payload.getHeaders()) {
                switch (header.getName().toLowerCase()) {
                    case "subject":
                        subject = header.getValue();
                        break;
                    case "from":
                        String fromValue = header.getValue();
                        if (fromValue.contains("<")) {
                            fromName = fromValue.substring(0, fromValue.indexOf("<")).trim();
                            fromEmail = fromValue.substring(fromValue.indexOf("<") + 1, fromValue.indexOf(">"));
                        } else {
                            fromEmail = fromValue;
                        }
                        break;
                }
            }
        }

        String body = extractBodyFromPayload(payload);

        boolean isUnread = message.getLabelIds() != null && message.getLabelIds().contains("UNREAD");
        boolean hasAttachments = hasAttachments(payload);

        return EmailResponseDto.builder()
                .gmailMessageId(message.getId())
                .subject(subject)
                .body(body)
                .snippet(message.getSnippet())
                .status(EmailStatus.RECEIVED)
                .direction(EmailDirection.IN)
                .sentAt(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(message.getInternalDate()),
                        ZoneId.systemDefault()))
                .threadId(message.getThreadId())
                .labelIds(message.getLabelIds())
                .hasAttachments(hasAttachments)
                .isRead(!isUnread)
                .fromName(fromName)
                .fromEmail(fromEmail)
                .build();
    }

    private String extractBodyFromPayload(MessagePart payload) {
        String body = "";

        if (payload.getBody() != null && payload.getBody().getData() != null) {
            body = new String(Base64.getDecoder().decode(payload.getBody().getData()));
        } else if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                if (part.getMimeType().equals("text/plain") || part.getMimeType().equals("text/html")) {
                    if (part.getBody() != null && part.getBody().getData() != null) {
                        body = new String(Base64.getDecoder().decode(part.getBody().getData()));
                        break;
                    }
                }
            }
        }

        return body;
    }

    private boolean hasAttachments(MessagePart payload) {
        if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                if (part.getFilename() != null && !part.getFilename().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void cacheEmailInDatabase(User user, Message message) {
        try {
            // Check if already cached
            if (emailLogRepository.findByGmailMessageId(message.getId()).isPresent()) {
                return;
            }

            EmailResponseDto dto = convertGmailMessageToDto(message, user);

            EmailLog emailLog = EmailLog.builder()
                    .user(user)
                    .gmailMessageId(message.getId())
                    .subject(dto.getSubject())
                    .body(dto.getBody())
                    .status(EmailStatus.RECEIVED)
                    .direction(EmailDirection.IN)
                    .sentAt(dto.getSentAt())
                    .senderEmail(dto.getFromEmail())
                    .threadId(message.getThreadId())
                    .labelIds(dto.getLabelIds() != null ? String.join(",", dto.getLabelIds()) : null)
                    .hasAttachments(dto.getHasAttachments())
                    .build();

            emailLogRepository.save(emailLog);

        } catch (Exception e) {
            log.warn("⚠️ Failed to cache email in database: {}", e.getMessage());
        }
    }

    private EmailResponseDto convertToEmailResponseDto(EmailLog emailLog) {
        return EmailResponseDto.builder()
                .id(emailLog.getId())
                .gmailMessageId(emailLog.getGmailMessageId())
                .subject(emailLog.getSubject())
                .body(emailLog.getBody())
                .status(emailLog.getStatus())
                .direction(emailLog.getDirection())
                .sentAt(emailLog.getSentAt())
                .recipientEmail(emailLog.getRecipientEmail())
                .senderEmail(emailLog.getSenderEmail())
                .threadId(emailLog.getThreadId())
                .labelIds(emailLog.getLabelIds() != null ?
                    Arrays.asList(emailLog.getLabelIds().split(",")) : null)
                .hasAttachments(emailLog.getHasAttachments())
                .createdAt(emailLog.getCreatedAt())
                .updatedAt(emailLog.getUpdatedAt())
                .build();
    }

    private Long getUnreadCount(User user) {
        return emailLogRepository.countUnreadEmails(user);
    }
}
