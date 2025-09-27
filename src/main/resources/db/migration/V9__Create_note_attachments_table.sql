-- V9__Create_note_attachments_table.sql
CREATE TABLE note_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL UNIQUE,
    file_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    description VARCHAR(500),
    note_id BIGINT NOT NULL,
    uploaded_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Foreign key constraints
    CONSTRAINT fk_note_attachments_note FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE,
    CONSTRAINT fk_note_attachments_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE CASCADE,

    -- Indexes for better performance
    INDEX idx_note_attachments_note_id (note_id),
    INDEX idx_note_attachments_uploaded_by (uploaded_by),
    INDEX idx_note_attachments_created_at (created_at),
    INDEX idx_note_attachments_stored_file_name (stored_file_name),
    INDEX idx_note_attachments_content_type (content_type)
);

