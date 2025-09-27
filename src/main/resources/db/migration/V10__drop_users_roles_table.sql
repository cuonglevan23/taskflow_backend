-- Migration to drop users_roles table since we now use systemRole field in users table
-- This table is no longer needed as we manage roles directly through the systemRole enum

-- Drop the users_roles junction table
DROP TABLE IF EXISTS users_roles;

-- Note: We keep the roles table for now in case it's referenced elsewhere
-- but the users_roles relationship table is no longer needed
