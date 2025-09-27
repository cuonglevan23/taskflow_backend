-- Create project_task_activities table
CREATE TABLE project_task_activities (
    id BIGSERIAL PRIMARY KEY,
    project_task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    old_value VARCHAR(1000),
    new_value VARCHAR(1000),
    field_name VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_task_activity_task FOREIGN KEY (project_task_id) REFERENCES project_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_task_activity_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX idx_project_task_activities_project_task_id ON project_task_activities(project_task_id);
CREATE INDEX idx_project_task_activities_user_id ON project_task_activities(user_id);
CREATE INDEX idx_project_task_activities_created_at ON project_task_activities(created_at);
CREATE INDEX idx_project_task_activities_activity_type ON project_task_activities(activity_type);
