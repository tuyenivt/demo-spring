-- Spring Modulith event publication lifecycle columns
ALTER TABLE event_publication
    ADD COLUMN status VARCHAR(20) NULL,
    ADD COLUMN completion_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_resubmission_date TIMESTAMP NULL;

UPDATE event_publication
SET status = IF(completion_date IS NULL, 'PUBLISHED', 'COMPLETED');

CREATE INDEX idx_event_publication_status ON event_publication (status);
