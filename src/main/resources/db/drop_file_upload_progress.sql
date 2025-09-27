-- Drop file_upload_progress table and related indexes
-- Execute this SQL script to remove file upload progress tracking completely

DROP TABLE IF EXISTS file_upload_progress;

-- If there are any foreign key constraints referencing this table, they should be dropped first
-- ALTER TABLE other_table DROP FOREIGN KEY fk_file_upload_progress;

-- Clean up any remaining indexes (if they exist independently)
-- DROP INDEX IF EXISTS idx_upload_session_id;
-- DROP INDEX IF EXISTS idx_upload_id;
-- DROP INDEX IF EXISTS idx_task_id_status;

-- Note: This will permanently remove all file upload progress data
-- Make sure to backup the database before running this script if needed
