package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ChatDto.*;
import com.example.taskmanagement_backend.dtos.FriendDto.FriendDto;
import com.example.taskmanagement_backend.entities.*;
import com.example.taskmanagement_backend.enums.*;
import com.example.taskmanagement_backend.repositories.*;
import com.example.taskmanagement_backend.utils.ChatTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final MessageReadRepository messageReadRepository;
    private final UserJpaRepository userRepository; // Sử dụng repository có sẵn
    private final ChatKafkaService chatKafkaService;
    private final ChatRedisService chatRedisService;
    private final FriendService friendService;
    private final ChatTimeFormatter chatTimeFormatter;
    private final S3Service s3Service; // 🆕 Add S3Service dependency

    @Transactional(readOnly = true)
    public Page<ConversationResponseDto> getUserConversations(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Conversation> conversations = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable);

        return conversations.map(conversation -> {
            MessageResponseDto lastMessage = getLastMessageDto(conversation.getId());
            Integer unreadCount = messageRepository.countUnreadMessages(conversation.getId(), userId);
            Boolean isOnline = false;

            // For direct conversations, check if the other user is online
            if (conversation.getType() == ConversationType.DIRECT) {
                Long otherUserId = getOtherUserInDirectConversation(conversation.getId(), userId);
                if (otherUserId != null) {
                    isOnline = chatRedisService.isUserOnline(otherUserId);
                }
            }

            return ConversationResponseDto.builder()
                    .id(conversation.getId())
                    .type(conversation.getType().name())
                    .name(getConversationName(conversation, userId))
                    .description(conversation.getDescription())
                    .avatarUrl(getConversationAvatar(conversation, userId))
                    .memberCount(conversationMemberRepository.countActiveByConversationId(conversation.getId()))
                    .lastMessage(lastMessage)
                    .unreadCount(unreadCount)
                    .isOnline(isOnline)
                    .lastActivity(conversation.getUpdatedAt())
                    .createdAt(conversation.getCreatedAt())
                    .build();
        });
    }

    @Transactional
    public MessageResponseDto sendMessage(Long senderId, SendMessageRequestDto request) {
        // Validate conversation membership
        ConversationMember senderMember = conversationMemberRepository
                .findByConversationIdAndUserId(request.getConversationId(), senderId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

        if (!senderMember.getIsActive()) {
            throw new RuntimeException("User is not an active member of this conversation");
        }

        // Validate reply-to message if provided
        if (request.getReplyToId() != null) {
            Message replyToMessage = messageRepository.findById(request.getReplyToId())
                    .orElseThrow(() -> new RuntimeException("Reply-to message not found"));

            if (!replyToMessage.getConversation().getId().equals(request.getConversationId())) {
                throw new RuntimeException("Cannot reply to a message from a different conversation");
            }

            if (replyToMessage.getIsDeleted()) {
                throw new RuntimeException("Cannot reply to a deleted message");
            }
        }

        Conversation conversation = senderMember.getConversation();
        User sender = senderMember.getUser();

        // Create message
        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .type(request.getType() != null ? request.getType() : MessageType.TEXT)
                .content(request.getContent())
                .fileName(request.getFileName())
                .fileUrl(request.getFileUrl())
                .fileSize(request.getFileSize())
                .replyToId(request.getReplyToId())
                .build();

        message = messageRepository.save(message);

        // Create message read status for all conversation members
        List<Long> memberUserIds = conversationMemberRepository.findUserIdsByConversationId(conversation.getId());
        for (Long memberId : memberUserIds) {
            MessageRead messageRead = MessageRead.builder()
                    .message(message)
                    .user(userRepository.findById(memberId).orElse(null))
                    .status(memberId.equals(senderId) ? MessageStatus.READ : MessageStatus.SENT)
                    .deliveredAt(LocalDateTime.now())
                    .readAt(memberId.equals(senderId) ? LocalDateTime.now() : null)
                    .build();
            messageReadRepository.save(messageRead);
        }

        // Update conversation timestamp
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        // Create response DTO
        MessageResponseDto responseDto = convertToMessageResponseDto(message);

        // Publish to Kafka for real-time delivery
        if (conversation.getType() == ConversationType.DIRECT) {
            chatKafkaService.publishDirectMessage(responseDto);
        } else {
            chatKafkaService.publishGroupMessage(responseDto);
        }

        // Update Redis cache
        chatRedisService.updateUnreadCount(request.getConversationId(), memberUserIds, senderId);

        return responseDto;
    }

    @Transactional
    public ConversationResponseDto createDirectConversation(Long userId1, Long userId2) {
        // Check if conversation already exists
        return conversationRepository.findDirectConversation(ConversationType.DIRECT, userId1, userId2)
                .map(conversation -> convertToConversationResponseDto(conversation, userId1))
                .orElseGet(() -> {
                    // Create new direct conversation
                    Conversation conversation = Conversation.builder()
                            .type(ConversationType.DIRECT)
                            .createdBy(userId1)
                            .build();
                    conversation = conversationRepository.save(conversation);

                    // Add members
                    User user1 = userRepository.findById(userId1).orElseThrow();
                    User user2 = userRepository.findById(userId2).orElseThrow();

                    ConversationMember member1 = ConversationMember.builder()
                            .conversation(conversation)
                            .user(user1)
                            .role(MemberRole.MEMBER)
                            .build();

                    ConversationMember member2 = ConversationMember.builder()
                            .conversation(conversation)
                            .user(user2)
                            .role(MemberRole.MEMBER)
                            .build();

                    conversationMemberRepository.save(member1);
                    conversationMemberRepository.save(member2);

                    return convertToConversationResponseDto(conversation, userId1);
                });
    }

    @Transactional
    public ConversationResponseDto createGroupConversation(Long creatorId, CreateGroupRequestDto request) {
        // Create group conversation
        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
                .name(request.getName())
                .description(request.getDescription())
                .avatarUrl(request.getAvatarUrl())
                .createdBy(creatorId)
                .build();
        conversation = conversationRepository.save(conversation);

        // Add creator as admin
        User creator = userRepository.findById(creatorId).orElseThrow();
        ConversationMember creatorMember = ConversationMember.builder()
                .conversation(conversation)
                .user(creator)
                .role(MemberRole.ADMIN)
                .build();
        conversationMemberRepository.save(creatorMember);

        // Add other members
        for (Long memberId : request.getMemberIds()) {
            if (!memberId.equals(creatorId)) {
                User member = userRepository.findById(memberId).orElse(null);
                if (member != null) {
                    ConversationMember conversationMember = ConversationMember.builder()
                            .conversation(conversation)
                            .user(member)
                            .role(MemberRole.MEMBER)
                            .build();
                    conversationMemberRepository.save(conversationMember);
                }
            }
        }

        return convertToConversationResponseDto(conversation, creatorId);
    }

    /**
     * Get unread messages for user (for offline message sync)
     */
    @Transactional(readOnly = true)
    public List<MessageResponseDto> getUnreadMessagesForUser(Long userId) {
        try {
            // Get all messages where user has status SENT (not DELIVERED or READ)
            List<Message> unreadMessages = messageRepository.findUnreadMessagesForUser(userId);

            return unreadMessages.stream()
                    .map(this::convertToMessageResponseDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting unread messages for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Update message delivery status for specific user
     */
    @Transactional
    public void updateMessageDeliveryStatus(Long messageId, Long userId, MessageStatus status) {
        try {
            MessageRead messageRead = messageReadRepository.findByMessageIdAndUserId(messageId, userId)
                    .orElse(null);

            if (messageRead != null) {
                messageRead.setStatus(status);
                if (status == MessageStatus.DELIVERED) {
                    messageRead.setDeliveredAt(LocalDateTime.now());
                } else if (status == MessageStatus.READ) {
                    messageRead.setReadAt(LocalDateTime.now());
                }
                messageReadRepository.save(messageRead);

                log.debug("Updated message {} delivery status to {} for user {}", messageId, status, userId);
            }
        } catch (Exception e) {
            log.error("Error updating message delivery status for message {} and user {}: {}",
                    messageId, userId, e.getMessage());
        }
    }

    /**
     * Mark message as read (enhanced with WebSocket notification)
     */
    @Transactional
    public void markMessageAsRead(Long userId, Long messageId) {
        try {
            // Update message read status
            updateMessageDeliveryStatus(messageId, userId, MessageStatus.READ);

            // Reset unread count in Redis
            Message message = messageRepository.findById(messageId).orElse(null);
            if (message != null) {
                chatRedisService.resetUnreadCount(userId, message.getConversation().getId());

                // Publish read status to Kafka for real-time update
                MessageReadStatusDto readStatus = MessageReadStatusDto.builder()
                        .messageId(messageId)
                        .userId(userId)
                        .status("READ")
                        .readAt(LocalDateTime.now())
                        .build();

                chatKafkaService.publishMessageReadStatus(readStatus);
            }

        } catch (Exception e) {
            log.error("Error marking message as read for user {} and message {}: {}", userId, messageId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<MessageResponseDto> getConversationMessages(Long userId, Long conversationId, int page, int size) {
        // Verify user is member of conversation
        conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

        Pageable pageable = PageRequest.of(page, size);
        Page<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        return messages.map(this::convertToMessageResponseDto);
    }

    /**
     * Get ALL conversation messages from oldest to newest (no pagination)
     */
    @Transactional(readOnly = true)
    public List<MessageResponseDto> getAllConversationMessages(Long userId, Long conversationId) {
        try {
            // Verify user is member of conversation
            conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

            log.info("📜 Loading ALL messages for conversation {} from oldest to newest", conversationId);

            // Load ALL messages from oldest to newest
            List<Message> messages = messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId);

            // Convert to DTOs
            List<MessageResponseDto> messageDtos = messages.stream()
                    .map(this::convertToMessageResponseDto)
                    .collect(Collectors.toList());

            log.info("✅ Loaded {} total messages for conversation {}", messageDtos.size(), conversationId);
            return messageDtos;

        } catch (Exception e) {
            log.error("❌ Error loading all conversation messages: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get conversation messages from oldest to newest with pagination
     */
    @Transactional(readOnly = true)
    public Page<MessageResponseDto> getConversationMessagesFromOldest(Long userId, Long conversationId, int page, int size) {
        try {
            // Verify user is member of conversation
            conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

            log.info("📜 Loading messages for conversation {} from oldest (page {}, size {})", conversationId, page, size);

            Pageable pageable = PageRequest.of(page, size);
            Page<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, pageable);

            // Convert to DTOs
            Page<MessageResponseDto> result = messages.map(this::convertToMessageResponseDto);

            log.info("✅ Loaded {} messages from oldest for conversation {}", result.getNumberOfElements(), conversationId);
            return result;

        } catch (Exception e) {
            log.error("❌ Error loading conversation messages from oldest: {}", e.getMessage());
            return Page.empty();
        }
    }

    /**
     * Enhanced offline message sync - get missing messages since last sync
     */
    @Transactional(readOnly = true)
    public OfflineSyncResponseDto getMessagesForOfflineSync(Long userId, Long lastSeenMessageId,
                                                           java.time.LocalDateTime lastSyncTime) {
        try {
            log.info("🔄 Syncing offline messages for user {} (lastSeen: {}, lastSync: {})",
                    userId, lastSeenMessageId, lastSyncTime);

            // Get user's conversation IDs
            List<Long> userConversationIds = conversationMemberRepository.findConversationIdsByUserId(userId);

            List<Message> missedMessages;

            if (lastSeenMessageId != null) {
                // Use message ID based sync (more reliable)
                missedMessages = messageRepository.findMessagesAfterLastSeen(userConversationIds, lastSeenMessageId);
            } else if (lastSyncTime != null) {
                // Fallback to timestamp based sync
                missedMessages = userConversationIds.stream()
                        .flatMap(convId -> messageRepository.findMessagesAfterTimestamp(convId, lastSyncTime).stream())
                        .collect(Collectors.toList());
            } else {
                // Full unread messages sync
                missedMessages = messageRepository.findUnreadMessagesForUser(userId);
            }

            // Convert to DTOs
            List<MessageResponseDto> messageDtos = missedMessages.stream()
                    .map(this::convertToMessageResponseDto)
                    .collect(Collectors.toList());

            // Group by conversation for better organization
            Map<Long, List<MessageResponseDto>> messagesByConversation = messageDtos.stream()
                    .collect(Collectors.groupingBy(MessageResponseDto::getConversationId));

            OfflineSyncResponseDto response = OfflineSyncResponseDto.builder()
                    .userId(userId)
                    .totalMissedMessages(messageDtos.size())
                    .conversationCount(messagesByConversation.size())
                    .messagesByConversation(messagesByConversation)
                    .syncedAt(LocalDateTime.now())
                    .syncType(lastSeenMessageId != null ? "MESSAGE_ID_BASED" :
                             lastSyncTime != null ? "TIMESTAMP_BASED" : "FULL_UNREAD")
                    .build();

            log.info("✅ Offline sync complete: {} messages across {} conversations for user {}",
                    messageDtos.size(), messagesByConversation.size(), userId);

            return response;

        } catch (Exception e) {
            log.error("❌ Error in offline message sync: {}", e.getMessage());
            return OfflineSyncResponseDto.builder()
                    .userId(userId)
                    .totalMissedMessages(0)
                    .messagesByConversation(Map.of())
                    .build();
        }
    }

    /**
     * Get friends list for group chat creation
     */
    @Transactional(readOnly = true)
    public List<FriendForGroupChatDto> getFriendsForGroupChat(Long userId) {
        try {
            // Get user's friends from FriendService
            List<FriendDto> friends = friendService.getFriends();

            return friends.stream()
                    .map(friend -> FriendForGroupChatDto.builder()
                            .id(friend.getId())
                            .name(friend.getFirstName() + " " + friend.getLastName()) // Combine first and last name
                            .email(friend.getEmail())
                            .avatarUrl(friend.getAvatarUrl())
                            .isOnline(chatRedisService.isUserOnline(friend.getId()))
                            .lastSeen(friend.getFriendsSince()) // Use friendsSince as lastSeen for now
                            .isSelected(false) // Default to not selected
                            .build())
                    .toList(); // Use toList() instead of collect(Collectors.toList())

        } catch (Exception e) {
            log.error("❌ Error getting friends for group chat: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Create group conversation from friends list
     */
    @Transactional
    public ConversationResponseDto createGroupFromFriends(Long creatorId, CreateGroupFromFriendsRequestDto request) {
        try {
            log.info("🏗️ Creating group chat from friends for user: {}", creatorId);

            // Validate input
            if (request.getFriendIds() == null || request.getFriendIds().isEmpty()) {
                throw new IllegalArgumentException("Friend list cannot be empty");
            }

            if (request.getName() == null || request.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Group name is required");
            }

            // Get creator user
            User creator = userRepository.findById(creatorId).orElseThrow(
                    () -> new RuntimeException("Creator user not found"));

            // Validate that all selected users are actually friends of the creator
            List<FriendDto> userFriends = friendService.getFriends();
            List<Long> friendIds = userFriends.stream()
                    .map(FriendDto::getId)
                    .collect(Collectors.toList());

            // Check if all requested friend IDs are valid friends
            for (Long friendId : request.getFriendIds()) {
                if (!friendIds.contains(friendId)) {
                    User user = userRepository.findById(friendId).orElse(null);
                    String userName = user != null ? user.getEmail() : "Unknown";
                    throw new IllegalArgumentException("User " + userName + " is not your friend");
                }
            }

            // Create group conversation
            Conversation conversation = Conversation.builder()
                    .type(ConversationType.GROUP)
                    .name(request.getName().trim())
                    .description(request.getDescription())
                    .avatarUrl(request.getAvatarUrl())
                    .createdBy(creatorId)
                    .build();
            conversation = conversationRepository.save(conversation);

            // Add creator as admin
            ConversationMember creatorMember = ConversationMember.builder()
                    .conversation(conversation)
                    .user(creator)
                    .role(MemberRole.ADMIN)
                    .build();
            conversationMemberRepository.save(creatorMember);

            // Add friends as members
            int addedMembers = 0;
            for (Long friendId : request.getFriendIds()) {
                User friend = userRepository.findById(friendId).orElse(null);
                if (friend != null) {
                    ConversationMember friendMember = ConversationMember.builder()
                            .conversation(conversation)
                            .user(friend)
                            .role(MemberRole.MEMBER)
                            .build();
                    conversationMemberRepository.save(friendMember);
                    addedMembers++;

                    log.debug("➕ Added friend {} to group {}", friend.getEmail(), conversation.getName());
                }
            }

            log.info("✅ Group chat '{}' created successfully with {} members",
                    conversation.getName(), addedMembers + 1); // +1 for creator

            // Send welcome message (optional)
            sendWelcomeMessage(conversation, creator);

            return convertToConversationResponseDto(conversation, creatorId);

        } catch (Exception e) {
            log.error("❌ Error creating group from friends: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create group chat: " + e.getMessage(), e);
        }
    }

    /**
     * Send welcome message to new group
     */
    private void sendWelcomeMessage(Conversation conversation, User creator) {
        try {
            String welcomeContent = String.format("🎉 Welcome to %s! This group was created by %s.",
                    conversation.getName(),
                    creator.getFirstName() + " " + creator.getLastName());

            Message welcomeMessage = Message.builder()
                    .conversation(conversation)
                    .sender(creator)
                    .type(MessageType.TEXT)
                    .content(welcomeContent)
                    .build();

            messageRepository.save(welcomeMessage);

            // Create read status for all members
            List<Long> memberUserIds = conversationMemberRepository.findUserIdsByConversationId(conversation.getId());
            for (Long memberId : memberUserIds) {
                MessageRead messageRead = MessageRead.builder()
                        .message(welcomeMessage)
                        .user(userRepository.findById(memberId).orElse(null))
                        .status(memberId.equals(creator.getId()) ? MessageStatus.READ : MessageStatus.SENT)
                        .deliveredAt(LocalDateTime.now())
                        .readAt(memberId.equals(creator.getId()) ? LocalDateTime.now() : null)
                        .build();
                messageReadRepository.save(messageRead);
            }

            log.debug("📨 Welcome message sent to group: {}", conversation.getName());

        } catch (Exception e) {
            log.warn("⚠️ Failed to send welcome message to group {}: {}", conversation.getId(), e.getMessage());
            // Don't throw exception as this is not critical
        }
    }

    /**
     * Auto-mark all unread messages as read when user joins conversation via WebSocket
     */
    @Transactional
    public void autoMarkMessagesAsReadOnJoinConversation(Long userId, Long conversationId) {
        try {
            log.info("🔍 Auto-marking messages as read for user {} joining conversation {}", userId, conversationId);

            // Verify user is member of conversation
            conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

            // Get all unread messages for this user in this conversation
            List<MessageRead> unreadMessageReads = messageReadRepository
                    .findUnreadMessagesByUserAndConversation(userId, conversationId, MessageStatus.SENT);

            if (unreadMessageReads.isEmpty()) {
                log.debug("✅ No unread messages found for user {} in conversation {}", userId, conversationId);
                return;
            }

            LocalDateTime readTime = LocalDateTime.now();
            List<Long> markedMessageIds = new ArrayList<>();

            // Mark all unread messages as read
            for (MessageRead messageRead : unreadMessageReads) {
                messageRead.setStatus(MessageStatus.READ);
                messageRead.setReadAt(readTime);
                messageReadRepository.save(messageRead);
                markedMessageIds.add(messageRead.getMessage().getId());
            }

            log.info("✅ Auto-marked {} messages as read for user {} in conversation {}",
                    markedMessageIds.size(), userId, conversationId);

            // Reset unread count in Redis
            chatRedisService.resetUnreadCount(userId, conversationId);

            // Publish read status updates to Kafka for real-time notification
            publishBulkReadStatusUpdate(userId, conversationId, markedMessageIds, readTime);

        } catch (Exception e) {
            log.error("❌ Error auto-marking messages as read for user {} in conversation {}: {}",
                    userId, conversationId, e.getMessage(), e);
        }
    }

    /**
     * Publish bulk read status update for multiple messages
     */
    private void publishBulkReadStatusUpdate(Long userId, Long conversationId, List<Long> messageIds, LocalDateTime readTime) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return;

            // Create bulk read status notification
            BulkMessageReadStatusDto bulkReadStatus = BulkMessageReadStatusDto.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .userName(user.getFirstName() + " " + user.getLastName())
                    .userAvatar(user.getAvatarUrl())
                    .messageIds(messageIds)
                    .status("READ")
                    .readAt(readTime)
                    .autoMarked(true) // Flag to indicate this was auto-marked
                    .build();

            chatKafkaService.publishBulkMessageReadStatus(bulkReadStatus);

            log.debug("📡 Published bulk read status for {} messages in conversation {}",
                    messageIds.size(), conversationId);

        } catch (Exception e) {
            log.warn("⚠️ Failed to publish bulk read status: {}", e.getMessage());
        }
    }

    /**
     * Get unread message count for user in specific conversation
     */
    @Transactional(readOnly = true)
    public Integer getUnreadMessageCount(Long userId, Long conversationId) {
        try {
            return messageRepository.countUnreadMessages(conversationId, userId);
        } catch (Exception e) {
            log.error("❌ Error getting unread count for user {} in conversation {}: {}",
                    userId, conversationId, e.getMessage());
            return 0;
        }
    }

    /**
     * Check if user has unread messages in conversation
     */
    @Transactional(readOnly = true)
    public boolean hasUnreadMessages(Long userId, Long conversationId) {
        return getUnreadMessageCount(userId, conversationId) > 0;
    }

    // Helper methods
    public MessageResponseDto convertToMessageResponseDto(Message message) {
        User sender = message.getSender();
        String replyToContent = null;
        String replyToSenderName = null;

        if (message.getReplyToId() != null) {
            Message replyToMessage = messageRepository.findById(message.getReplyToId()).orElse(null);
            if (replyToMessage != null) {
                replyToContent = replyToMessage.getContent();
                replyToSenderName = replyToMessage.getSender().getFirstName() + " " + replyToMessage.getSender().getLastName();
            }
        }

        // 🔧 Generate fresh presigned URL for file attachments to fix 403 Forbidden errors
        String freshFileUrl = null;
        if (message.getFileUrl() != null && !message.getFileUrl().isEmpty()) {
            try {
                // Extract S3 key from the stored URL or use it directly if it's already an S3 key
                String s3Key = extractS3KeyFromUrl(message.getFileUrl());
                if (s3Key != null) {
                    freshFileUrl = s3Service.generateDownloadUrl(s3Key);
                    log.debug("🔧 Generated fresh presigned URL for message {} file: {}", message.getId(), message.getFileName());
                } else {
                    // Fallback to original URL if we can't extract S3 key
                    freshFileUrl = message.getFileUrl();
                    log.warn("⚠️ Could not extract S3 key from URL for message {}, using original URL", message.getId());
                }
            } catch (Exception e) {
                log.error("❌ Error generating fresh presigned URL for message {} file: {}", message.getId(), e.getMessage());
                // Fallback to original URL
                freshFileUrl = message.getFileUrl();
            }
        }

        return MessageResponseDto.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(sender.getId())
                .senderName(sender.getFirstName() + " " + sender.getLastName())
                .senderAvatar(sender.getAvatarUrl())
                .type(message.getType())
                .content(message.getContent())
                .fileName(message.getFileName())
                .fileUrl(freshFileUrl) // 🔧 Use fresh presigned URL instead of expired one
                .fileSize(message.getFileSize())
                .replyToId(message.getReplyToId())
                .replyToContent(replyToContent)
                .replyToSenderName(replyToSenderName)
                .isEdited(message.getIsEdited())
                .isDeleted(message.getIsDeleted())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .deliveredCount(messageReadRepository.countByMessageIdAndStatus(message.getId(), MessageStatus.DELIVERED))
                .readCount(messageReadRepository.countByMessageIdAndStatus(message.getId(), MessageStatus.READ))
                .build();
    }

    /**
     * 🔧 Extract S3 key from presigned URL or return the URL as-is if it's already an S3 key
     * Improved version to handle URL encoding and various URL formats
     */
    private String extractS3KeyFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        try {
            // If the URL looks like an S3 key (doesn't start with http), use it directly
            if (!fileUrl.startsWith("http")) {
                return java.net.URLDecoder.decode(fileUrl, "UTF-8");
            }

            // Extract S3 key from presigned URL
            // Format: https://bucket-name.s3.region.amazonaws.com/key?signature...
            java.net.URI uri = new java.net.URI(fileUrl);
            String path = uri.getPath();

            // Remove leading slash
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            // 🔧 Decode URL-encoded characters (e.g., %20 -> space, %28 -> (, %29 -> ))
            String decodedPath = java.net.URLDecoder.decode(path, "UTF-8");

            log.debug("🔧 Extracted S3 key: '{}' from URL: '{}'", decodedPath, fileUrl);
            return decodedPath.isEmpty() ? null : decodedPath;

        } catch (Exception e) {
            log.warn("⚠️ Could not extract S3 key from URL: {}, error: {}", fileUrl, e.getMessage());
            return null;
        }
    }

    private ConversationResponseDto convertToConversationResponseDto(Conversation conversation, Long currentUserId) {
        MessageResponseDto lastMessage = getLastMessageDto(conversation.getId());
        Integer unreadCount = messageRepository.countUnreadMessages(conversation.getId(), currentUserId);
        Boolean isOnline = false;

        if (conversation.getType() == ConversationType.DIRECT) {
            Long otherUserId = getOtherUserInDirectConversation(conversation.getId(), currentUserId);
            if (otherUserId != null) {
                isOnline = chatRedisService.isUserOnline(otherUserId);
            }
        }

        return ConversationResponseDto.builder()
                .id(conversation.getId())
                .type(conversation.getType().name())
                .name(getConversationName(conversation, currentUserId))
                .description(conversation.getDescription())
                .avatarUrl(getConversationAvatar(conversation, currentUserId))
                .memberCount(conversationMemberRepository.countActiveByConversationId(conversation.getId()))
                .lastMessage(lastMessage)
                .unreadCount(unreadCount)
                .isOnline(isOnline)
                .lastActivity(conversation.getUpdatedAt())
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    private MessageResponseDto getLastMessageDto(Long conversationId) {
        return messageRepository.findLastMessageByConversationId(conversationId)
                .map(this::convertToMessageResponseDto)
                .orElse(null);
    }

    private String getConversationName(Conversation conversation, Long currentUserId) {
        if (conversation.getType() == ConversationType.GROUP) {
            return conversation.getName();
        } else {
            // For direct conversations, return the other user's name
            Long otherUserId = getOtherUserInDirectConversation(conversation.getId(), currentUserId);
            if (otherUserId != null) {
                User otherUser = userRepository.findById(otherUserId).orElse(null);
                if (otherUser != null) {
                    return otherUser.getFirstName() + " " + otherUser.getLastName();
                }
            }
            return "Direct Chat";
        }
    }

    private String getConversationAvatar(Conversation conversation, Long currentUserId) {
        if (conversation.getType() == ConversationType.GROUP) {
            return conversation.getAvatarUrl();
        } else {
            // For direct conversations, return the other user's avatar
            Long otherUserId = getOtherUserInDirectConversation(conversation.getId(), currentUserId);
            if (otherUserId != null) {
                User otherUser = userRepository.findById(otherUserId).orElse(null);
                if (otherUser != null) {
                    return otherUser.getAvatarUrl();
                }
            }
            return null;
        }
    }

    private Long getOtherUserInDirectConversation(Long conversationId, Long currentUserId) {
        List<Long> memberIds = conversationMemberRepository.findUserIdsByConversationId(conversationId);
        return memberIds.stream()
                .filter(id -> !id.equals(currentUserId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get detailed read status for a specific message with formatted time display
     */
    @Transactional(readOnly = true)
    public List<DetailedMessageReadStatusDto> getDetailedMessageReadStatus(Long messageId, Long requesterId) {
        try {
            // Get the message to verify access
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message not found"));

            // Verify requester is member of the conversation
            conversationMemberRepository.findByConversationIdAndUserId(message.getConversation().getId(), requesterId)
                    .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

            // Get all read status for this message
            List<MessageRead> messageReads = messageReadRepository.findByMessageId(messageId);

            return messageReads.stream()
                    .map(this::convertToDetailedReadStatus)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Error getting detailed read status for message {}: {}", messageId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Convert MessageRead to DetailedMessageReadStatusDto with formatted time
     */
    private DetailedMessageReadStatusDto convertToDetailedReadStatus(MessageRead messageRead) {
        User user = messageRead.getUser();
        LocalDateTime deliveredAt = messageRead.getDeliveredAt();
        LocalDateTime readAt = messageRead.getReadAt();

        return DetailedMessageReadStatusDto.builder()
                .messageId(messageRead.getMessage().getId())
                .userId(user.getId())
                .userName(user.getFirstName() + " " + user.getLastName())
                .userAvatar(user.getAvatarUrl())
                .status(messageRead.getStatus().name())
                .deliveredAt(deliveredAt)
                .readAt(readAt)

                // Formatted time strings
                .deliveredAtFormatted(chatTimeFormatter.formatTime(deliveredAt))
                .readAtFormatted(chatTimeFormatter.formatTime(readAt))
                .deliveredDateFormatted(chatTimeFormatter.formatDate(deliveredAt))
                .readDateFormatted(chatTimeFormatter.formatDate(readAt))

                // Relative time
                .deliveredAtRelative(chatTimeFormatter.formatRelativeTime(deliveredAt))
                .readAtRelative(chatTimeFormatter.formatRelativeTime(readAt))

                // Additional info
                .isToday(chatTimeFormatter.isToday(readAt))
                .isRecent(chatTimeFormatter.isRecent(readAt))
                .readDelayMinutes(chatTimeFormatter.calculateMinutesBetween(deliveredAt, readAt))

                .build();
    }

    /**
     * Get read status summary for conversation - shows who has read recent messages
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getConversationReadStatusSummary(Long conversationId, Long userId) {
        try {
            // Verify user is member of conversation
            conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

            // Get recent messages (last 20)
            Pageable pageable = PageRequest.of(0, 20);
            Page<Message> recentMessages = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

            Map<String, Object> summary = new HashMap<>();
            List<Map<String, Object>> messageReadSummary = new ArrayList<>();

            for (Message message : recentMessages.getContent()) {
                List<MessageRead> reads = messageReadRepository.findByMessageId(message.getId());

                List<Map<String, Object>> readByUsers = reads.stream()
                        .filter(mr -> mr.getStatus() == MessageStatus.READ && mr.getReadAt() != null)
                        .map(mr -> {
                            Map<String, Object> readInfo = new HashMap<>();
                            readInfo.put("userId", mr.getUser().getId());
                            readInfo.put("userName", mr.getUser().getFirstName() + " " + mr.getUser().getLastName());
                            readInfo.put("readAt", mr.getReadAt());
                            readInfo.put("readAtFormatted", chatTimeFormatter.formatTime(mr.getReadAt()));
                            readInfo.put("readAtRelative", chatTimeFormatter.formatRelativeTime(mr.getReadAt()));
                            readInfo.put("isRecent", chatTimeFormatter.isRecent(mr.getReadAt()));
                            return readInfo;
                        })
                        .collect(Collectors.toList());

                Map<String, Object> messageInfo = new HashMap<>();
                messageInfo.put("messageId", message.getId());
                messageInfo.put("content", message.getContent());
                messageInfo.put("senderId", message.getSender().getId());
                messageInfo.put("senderName", message.getSender().getFirstName() + " " + message.getSender().getLastName());
                messageInfo.put("createdAt", message.getCreatedAt());
                messageInfo.put("createdAtFormatted", chatTimeFormatter.formatMessageTime(message.getCreatedAt()));
                messageInfo.put("readBy", readByUsers);
                messageInfo.put("readCount", readByUsers.size());

                messageReadSummary.add(messageInfo);
            }

            summary.put("conversationId", conversationId);
            summary.put("messages", messageReadSummary);
            summary.put("totalMessages", recentMessages.getTotalElements());
            summary.put("generatedAt", LocalDateTime.now());
            summary.put("generatedAtFormatted", chatTimeFormatter.formatDateTime(LocalDateTime.now()));

            return summary;

        } catch (Exception e) {
            log.error("❌ Error getting conversation read status summary: {}", e.getMessage(), e);
            return Map.of("error", "Failed to get read status summary");
        }
    }

    /**
     * Add members to group conversation
     */
    @Transactional
    public List<ConversationMemberDto> addMembersToConversation(Long userId, Long conversationId, List<Long> userIdsToAdd) {
        // Verify conversation exists and is a group
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new RuntimeException("Can only add members to group conversations");
        }

        // Verify current user is a member and has permission to add members
        ConversationMember currentUserMember = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("You are not a member of this conversation"));

        // Only admins can add members to group conversations
        if (currentUserMember.getRole() != MemberRole.ADMIN) {
            throw new RuntimeException("Only admins can add members to this conversation");
        }

        List<ConversationMemberDto> addedMembers = new ArrayList<>();

        for (Long userIdToAdd : userIdsToAdd) {
            // Check if user exists
            User userToAdd = userRepository.findById(userIdToAdd)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + userIdToAdd));

            // Check if user is already a member
            if (conversationMemberRepository.findByConversationIdAndUserId(conversationId, userIdToAdd).isPresent()) {
                log.warn("User {} is already a member of conversation {}", userIdToAdd, conversationId);
                continue; // Skip if already a member
            }

            // Add user as member
            ConversationMember newMember = ConversationMember.builder()
                    .conversation(conversation)
                    .user(userToAdd)
                    .role(MemberRole.MEMBER)
                    .joinedAt(LocalDateTime.now())
                    .build();

            conversationMemberRepository.save(newMember);

            // Convert to DTO
            ConversationMemberDto memberDto = ConversationMemberDto.builder()
                    .userId(userToAdd.getId())
                    .userName(userToAdd.getUserProfile() != null ?
                        (userToAdd.getUserProfile().getFirstName() + " " + userToAdd.getUserProfile().getLastName()).trim() :
                        userToAdd.getEmail())
                    .userAvatar(userToAdd.getUserProfile() != null ? userToAdd.getUserProfile().getAvtUrl() : null)
                    .role(MemberRole.MEMBER.name())
                    .joinedAt(LocalDateTime.now())
                    .isOnline(false) // TODO: Get actual online status
                    .build();

            addedMembers.add(memberDto);

            // Send notification message to the group
            try {
                String notificationContent = String.format("%s added %s to the group",
                    getCurrentUserDisplayName(userId),
                    getUserDisplayName(userToAdd));

                Message notificationMessage = Message.builder()
                        .conversation(conversation)
                        .sender(userRepository.findById(userId).orElse(null))
                        .type(MessageType.SYSTEM)
                        .content(notificationContent)
                        .build();

                messageRepository.save(notificationMessage);

                // Create read status for all members
                List<Long> allMemberIds = conversationMemberRepository.findUserIdsByConversationId(conversationId);
                for (Long memberId : allMemberIds) {
                    MessageRead messageRead = MessageRead.builder()
                            .message(notificationMessage)
                            .user(userRepository.findById(memberId).orElse(null))
                            .status(MessageStatus.DELIVERED)
                            .deliveredAt(LocalDateTime.now())
                            .build();
                    messageReadRepository.save(messageRead);
                }

            } catch (Exception e) {
                log.error("Failed to send notification message: {}", e.getMessage());
            }
        }

        // Update conversation timestamp
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        log.info("✅ Added {} members to conversation {}", addedMembers.size(), conversationId);
        return addedMembers;
    }

    /**
     * Remove member from group conversation
     */
    @Transactional
    public void removeMemberFromConversation(Long userId, Long conversationId, Long memberIdToRemove) {
        // Verify conversation exists and is a group
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new RuntimeException("Can only remove members from group conversations");
        }

        // Verify current user is a member and has permission
        ConversationMember currentUserMember = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("You are not a member of this conversation"));

        // Verify member to remove exists
        ConversationMember memberToRemove = conversationMemberRepository.findByConversationIdAndUserId(conversationId, memberIdToRemove)
                .orElseThrow(() -> new RuntimeException("Member not found in this conversation"));

        // Permission check: Only admins can remove others, or users can remove themselves
        if (currentUserMember.getRole() != MemberRole.ADMIN && !userId.equals(memberIdToRemove)) {
            throw new RuntimeException("Only admins can remove other members");
        }

        // Cannot remove the last admin
        if (memberToRemove.getRole() == MemberRole.ADMIN) {
            long adminCount = conversationMemberRepository.findByConversationId(conversationId)
                    .stream()
                    .filter(member -> member.getRole() == MemberRole.ADMIN)
                    .count();

            if (adminCount <= 1) {
                throw new RuntimeException("Cannot remove the last admin from the group");
            }
        }

        // Remove the member
        conversationMemberRepository.delete(memberToRemove);

        // Send notification message
        try {
            User removedUser = userRepository.findById(memberIdToRemove).orElse(null);
            String notificationContent;

            if (userId.equals(memberIdToRemove)) {
                notificationContent = String.format("%s left the group", getUserDisplayName(removedUser));
            } else {
                notificationContent = String.format("%s removed %s from the group",
                    getCurrentUserDisplayName(userId),
                    getUserDisplayName(removedUser));
            }

            Message notificationMessage = Message.builder()
                    .conversation(conversation)
                    .sender(userRepository.findById(userId).orElse(null))
                    .type(MessageType.SYSTEM)
                    .content(notificationContent)
                    .build();

            messageRepository.save(notificationMessage);

        } catch (Exception e) {
            log.error("Failed to send notification message: {}", e.getMessage());
        }

        // Update conversation timestamp
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        log.info("✅ Removed member {} from conversation {}", memberIdToRemove, conversationId);
    }

    // Helper methods
    private String getCurrentUserDisplayName(Long userId) {
        return userRepository.findById(userId)
                .map(this::getUserDisplayName)
                .orElse("Unknown User");
    }

    private String getUserDisplayName(User user) {
        if (user == null) return "Unknown User";

        if (user.getUserProfile() != null) {
            String firstName = user.getUserProfile().getFirstName();
            String lastName = user.getUserProfile().getLastName();

            if (firstName != null && lastName != null) {
                return (firstName + " " + lastName).trim();
            } else if (firstName != null) {
                return firstName;
            } else if (lastName != null) {
                return lastName;
            }
        }

        return user.getEmail();
    }

    /**
     * Get detailed information about a specific conversation
     * @param userId The ID of the user requesting the information
     * @param conversationId The ID of the conversation to get details for
     * @return A ConversationDetailDto containing all details of the conversation
     */
    @Transactional(readOnly = true)
    public com.example.taskmanagement_backend.controllers.ChatController.ConversationDetailDto getConversationDetails(Long userId, Long conversationId) {
        // Check if the user is a member of the conversation
        ConversationMember member = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this conversation"));

        if (!member.getIsActive()) {
            throw new RuntimeException("User is not an active member of this conversation");
        }

        // Get the conversation
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // Get all active members of the conversation
        List<ConversationMember> activeMembers = conversationMemberRepository.findActiveByConversationId(conversationId);
        List<ConversationMemberDto> memberDtos = activeMembers.stream()
                .map(this::convertToConversationMemberDto)
                .collect(Collectors.toList());

        // Count total messages
        Integer messageCount = messageRepository.countMessagesInConversation(conversationId).intValue();

        // Create and return the DTO with all necessary information
        com.example.taskmanagement_backend.controllers.ChatController.ConversationDetailDto detailDto =
            com.example.taskmanagement_backend.controllers.ChatController.ConversationDetailDto.builder()
                .id(conversation.getId())
                .build();

        // Set all the properties using reflection since we can't directly access the fields
        try {
            java.lang.reflect.Field typeField = detailDto.getClass().getDeclaredField("type");
            typeField.setAccessible(true);
            typeField.set(detailDto, conversation.getType().name());

            java.lang.reflect.Field nameField = detailDto.getClass().getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(detailDto, getConversationName(conversation, userId));

            java.lang.reflect.Field descField = detailDto.getClass().getDeclaredField("description");
            descField.setAccessible(true);
            descField.set(detailDto, conversation.getDescription());

            java.lang.reflect.Field avatarField = detailDto.getClass().getDeclaredField("avatarUrl");
            avatarField.setAccessible(true);
            avatarField.set(detailDto, getConversationAvatar(conversation, userId));

            java.lang.reflect.Field membersField = detailDto.getClass().getDeclaredField("members");
            membersField.setAccessible(true);
            membersField.set(detailDto, memberDtos);

            java.lang.reflect.Field messageCountField = detailDto.getClass().getDeclaredField("messageCount");
            messageCountField.setAccessible(true);
            messageCountField.set(detailDto, messageCount);

            java.lang.reflect.Field createdAtField = detailDto.getClass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(detailDto, conversation.getCreatedAt());
        } catch (Exception e) {
            log.error("Error setting fields on ConversationDetailDto: {}", e.getMessage());
        }

        return detailDto;
    }

    private ConversationMemberDto convertToConversationMemberDto(ConversationMember member) {
        User user = member.getUser();
        return ConversationMemberDto.builder()
                .userId(user.getId())
                .userName(user.getUserProfile() != null ?
                    (user.getUserProfile().getFirstName() + " " + user.getUserProfile().getLastName()).trim() :
                    user.getEmail())
                .userAvatar(user.getUserProfile() != null ? user.getUserProfile().getAvtUrl() : null)
                .role(member.getRole().name())
                .joinedAt(member.getJoinedAt())
                .isOnline(chatRedisService.isUserOnline(user.getId()))
                .build();
    }
}
