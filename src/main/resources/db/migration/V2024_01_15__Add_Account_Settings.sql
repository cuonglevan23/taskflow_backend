-- Migration script to add account settings columns to user_profiles table
-- Add preferred_language and preferred_theme columns

ALTER TABLE user_profiles
ADD COLUMN preferred_language VARCHAR(10) DEFAULT 'EN',
ADD COLUMN preferred_theme VARCHAR(10) DEFAULT 'DARK';

-- Update existing records to have default values
UPDATE user_profiles
SET preferred_language = 'EN', preferred_theme = 'DARK'
WHERE preferred_language IS NULL OR preferred_theme IS NULL;
