-- Add CASCADE DELETE to project_task_activities foreign key constraint
-- This allows automatic deletion of activities when a project task is deleted

-- Drop the existing foreign key constraint
ALTER TABLE `project_task_activities`
DROP FOREIGN KEY `fk_project_task_activity_task`;

-- Add the new foreign key constraint with CASCADE DELETE
ALTER TABLE `project_task_activities`
ADD CONSTRAINT `fk_project_task_activity_task`
FOREIGN KEY (`project_task_id`) REFERENCES `project_tasks` (`id`)
ON DELETE CASCADE ON UPDATE CASCADE;
