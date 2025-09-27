-- Add bookmark and archive functionality to notifications table
-- Date: 2025-09-18
-- Description: Adding bookmark and archive fields for inbox functionality

ALTER TABLE notifications
ADD COLUMN is_bookmarked BOOLEAN DEFAULT FALSE,
ADD COLUMN is_archived BOOLEAN DEFAULT FALSE,
ADD COLUMN bookmarked_at TIMESTAMP,
ADD COLUMN archived_at TIMESTAMP;

-- Add indexes for better query performance
CREATE INDEX idx_notification_user_bookmarked ON notifications(user_id, is_bookmarked, bookmarked_at);
CREATE INDEX idx_notification_user_archived ON notifications(user_id, is_archived, archived_at);
CREATE INDEX idx_notification_user_active ON notifications(user_id, is_archived, created_at);

-- Update existing notifications to have default values
UPDATE notifications
SET is_bookmarked = FALSE, is_archived = FALSE
WHERE is_bookmarked IS NULL OR is_archived IS NULL;
