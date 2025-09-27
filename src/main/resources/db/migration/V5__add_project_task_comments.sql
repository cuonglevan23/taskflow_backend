-- Create project_task_comments table
CREATE TABLE project_task_comments (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    project_task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_project_task_comment_task FOREIGN KEY (project_task_id) REFERENCES project_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_task_comment_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX idx_project_task_comments_project_task_id ON project_task_comments(project_task_id);
CREATE INDEX idx_project_task_comments_user_id ON project_task_comments(user_id);
CREATE INDEX idx_project_task_comments_created_at ON project_task_comments(created_at);
