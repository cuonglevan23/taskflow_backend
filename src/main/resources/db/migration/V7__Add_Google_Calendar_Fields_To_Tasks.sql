-- ✅ Migration: Add Google Calendar fields to tasks table
-- Version: V7
-- Description: Add columns for Google Calendar integration

-- Add Google Calendar Event URL field
ALTER TABLE tasks
ADD COLUMN google_calendar_event_url VARCHAR(1000) NULL
COMMENT 'URL link to Google Calendar event for user to click and view';

-- Add Google Meet Link field
ALTER TABLE tasks
ADD COLUMN google_meet_link VARCHAR(1000) NULL
COMMENT 'Google Meet link if the calendar event has a meeting';

-- Add Calendar Sync Status field
ALTER TABLE tasks
ADD COLUMN is_synced_to_calendar BOOLEAN DEFAULT FALSE
COMMENT 'Whether this task has been synced to Google Calendar';

-- Add Calendar Sync Timestamp field
ALTER TABLE tasks
ADD COLUMN calendar_synced_at DATETIME NULL
COMMENT 'When this task was last synced to Google Calendar';

-- Create index for better query performance on calendar-related fields
CREATE INDEX idx_tasks_calendar_sync
ON tasks(is_synced_to_calendar, calendar_synced_at);

CREATE INDEX idx_tasks_calendar_event_id
ON tasks(google_calendar_event_id);

-- Update existing tasks to have proper default values
UPDATE tasks
SET is_synced_to_calendar = FALSE
WHERE is_synced_to_calendar IS NULL;

-- Add helpful comments to existing google_calendar_event_id field if it exists
-- (This field should already exist from previous migrations)
ALTER TABLE tasks
MODIFY COLUMN google_calendar_event_id VARCHAR(255) NULL
COMMENT 'Google Calendar Event ID for tracking the associated calendar event';
