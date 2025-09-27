-- Add start_date column to tasks table

ALTER TABLE tasks ADD COLUMN start_date DATE;

-- Add comment for documentation
COMMENT ON COLUMN tasks.start_date IS 'The date when the task should start';

-- Optional: Add index for start_date queries (useful for filtering tasks by start date)
CREATE INDEX IF NOT EXISTS idx_tasks_start_date ON tasks(start_date);

-- Optional: Add composite index for date range queries (start_date and deadline)
CREATE INDEX IF NOT EXISTS idx_tasks_date_range ON tasks(start_date, deadline);