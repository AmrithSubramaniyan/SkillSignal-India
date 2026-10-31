-- V4: Source Capabilities Registry & Extended Job Source Metadata

CREATE TABLE source_capabilities (
    id UUID PRIMARY KEY,
    source VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(150) NOT NULL,
    access_method VARCHAR(150) NOT NULL,
    allowed_actions VARCHAR(255) NOT NULL DEFAULT 'ASSISTED_APPLY',
    rate_limit VARCHAR(100) NOT NULL,
    terms_reviewed_at TIMESTAMP WITH TIME ZONE,
    confirmation_signals VARCHAR(255),
    status_note VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE jobs ADD COLUMN source VARCHAR(100) NOT NULL DEFAULT 'manual';
ALTER TABLE jobs ADD COLUMN external_job_id VARCHAR(255);
ALTER TABLE jobs ADD COLUMN employment_type VARCHAR(64) DEFAULT 'FULL_TIME';
ALTER TABLE jobs ADD COLUMN salary_min DOUBLE PRECISION;
ALTER TABLE jobs ADD COLUMN salary_max DOUBLE PRECISION;
ALTER TABLE jobs ADD COLUMN salary_currency VARCHAR(16);
ALTER TABLE jobs ADD COLUMN salary_period VARCHAR(32);
ALTER TABLE jobs ADD COLUMN required_education VARCHAR(255);
ALTER TABLE jobs ADD COLUMN posted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE jobs ADD COLUMN dedup_key VARCHAR(255);
ALTER TABLE jobs ADD COLUMN source_records_json TEXT;
ALTER TABLE jobs ADD COLUMN is_stale BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE jobs ADD COLUMN stale_reason VARCHAR(255);
ALTER TABLE jobs ADD COLUMN is_saved BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_jobs_dedup_key ON jobs(user_id, dedup_key);
CREATE INDEX idx_jobs_source ON jobs(user_id, source);

