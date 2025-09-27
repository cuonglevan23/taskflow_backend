-- Chat System Database Migration
-- File: V1__Create_Chat_Tables.sql

-- Create conversations table
CREATE TABLE conversations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type ENUM('DIRECT', 'GROUP') NOT NULL,
    name VARCHAR(100),
    description VARCHAR(500),
    avatar_url VARCHAR(255),
    created_by BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_conversations_type (type),
    INDEX idx_conversations_created_by (created_by),
    INDEX idx_conversations_updated_at (updated_at),

    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Create conversation_members table
CREATE TABLE conversation_members (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('ADMIN', 'MEMBER') DEFAULT 'MEMBER',
    is_active BOOLEAN DEFAULT TRUE,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP NULL,

    UNIQUE KEY uk_conversation_user (conversation_id, user_id),
    INDEX idx_conversation_members_user_id (user_id),
    INDEX idx_conversation_members_conversation_id (conversation_id),
    INDEX idx_conversation_members_active (is_active),

    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create messages table
CREATE TABLE messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    type ENUM('TEXT', 'IMAGE', 'FILE', 'SYSTEM') DEFAULT 'TEXT',
    content TEXT,
    file_url VARCHAR(255),
    file_name VARCHAR(255),
    file_size BIGINT,
    reply_to_id BIGINT,
    is_edited BOOLEAN DEFAULT FALSE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_messages_conversation_id (conversation_id),
    INDEX idx_messages_sender_id (sender_id),
    INDEX idx_messages_created_at (created_at),
    INDEX idx_messages_conversation_created (conversation_id, created_at),
    INDEX idx_messages_reply_to_id (reply_to_id),

    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (reply_to_id) REFERENCES messages(id) ON DELETE SET NULL
);

-- Create message_read table
CREATE TABLE message_read (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status ENUM('SENT', 'DELIVERED', 'READ') DEFAULT 'SENT',
    delivered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_message_user_read (message_id, user_id),
    INDEX idx_message_read_user_id (user_id),
    INDEX idx_message_read_status (status),
    INDEX idx_message_read_message_id (message_id),

    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Add indexes for performance optimization
CREATE INDEX idx_conversations_active_updated ON conversations(is_active, updated_at);
CREATE INDEX idx_messages_not_deleted_created ON messages(is_deleted, created_at);
CREATE INDEX idx_conversation_members_active_conversation ON conversation_members(is_active, conversation_id);

-- Insert sample data for testing (optional)
-- INSERT INTO conversations (type, name, created_by) VALUES
-- ('GROUP', 'General Discussion', 1),
-- ('GROUP', 'Project Alpha', 1);
