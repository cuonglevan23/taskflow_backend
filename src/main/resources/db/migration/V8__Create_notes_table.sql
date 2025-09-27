-- V8__Create_notes_table.sql
CREATE TABLE notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content JSON,
    description TEXT(1000),
    user_id BIGINT,
    project_id BIGINT,
    creator_id BIGINT NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Foreign key constraints
    CONSTRAINT fk_notes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notes_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_notes_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE,

    -- Ensure note belongs to either user OR project, not both
    CONSTRAINT chk_note_ownership CHECK (
        (user_id IS NOT NULL AND project_id IS NULL) OR
        (user_id IS NULL AND project_id IS NOT NULL)
    ),

    -- Indexes for better performance
    INDEX idx_notes_user_id (user_id),
    INDEX idx_notes_project_id (project_id),
    INDEX idx_notes_creator_id (creator_id),
    INDEX idx_notes_created_at (created_at),
    INDEX idx_notes_updated_at (updated_at),
    INDEX idx_notes_title (title),
    INDEX idx_notes_archived (is_archived),
    INDEX idx_notes_public (is_public)
);

