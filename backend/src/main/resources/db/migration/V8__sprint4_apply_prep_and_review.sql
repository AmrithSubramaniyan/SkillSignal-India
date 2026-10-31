-- Sprint 4 (Stages 18-23): Questions & Answers, Packs & Readiness, Eligibility & Strict Rule, Submission State Machine & Idempotency, Review Queue, Safety Controls

ALTER TABLE jobs ADD COLUMN max_years_experience DOUBLE PRECISION;
ALTER TABLE jobs ADD COLUMN country VARCHAR(100) DEFAULT 'India';

CREATE TABLE answer_library (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    normalized_question VARCHAR(512) NOT NULL,
    question_text TEXT NOT NULL,
    category VARCHAR(64) NOT NULL,
    approved_answer TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_answer_library_user_norm UNIQUE (user_id, normalized_question)
);

CREATE INDEX idx_answer_library_user_id ON answer_library(user_id);

CREATE TABLE application_packs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    resume_version_id UUID,
    resume_hash VARCHAR(128),
    job_snapshot_json TEXT NOT NULL,
    job_snapshot_hash VARCHAR(128) NOT NULL,
    notes_three_tones_json TEXT NOT NULL,
    answers_json TEXT NOT NULL,
    truthfulness_report_json TEXT NOT NULL,
    truthfulness_passed BOOLEAN NOT NULL DEFAULT TRUE,
    readiness_score INTEGER NOT NULL DEFAULT 0,
    readiness_breakdown_json TEXT NOT NULL,
    routes_to_review_queue BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_application_packs_user_job UNIQUE (user_id, job_id)
);

CREATE INDEX idx_application_packs_user_id ON application_packs(user_id);

CREATE TABLE auto_apply_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    min_matched_skills INTEGER NOT NULL DEFAULT 3,
    require_50_percent_skill_coverage BOOLEAN NOT NULL DEFAULT TRUE,
    over_qualified_policy VARCHAR(32) NOT NULL DEFAULT 'BLOCK',
    no_experience_stated_policy VARCHAR(32) NOT NULL DEFAULT 'REVIEW',
    readiness_threshold INTEGER NOT NULL DEFAULT 70,
    daily_cap INTEGER NOT NULL DEFAULT 5,
    weekly_cap INTEGER NOT NULL DEFAULT 20,
    duplicate_cooldown_days INTEGER NOT NULL DEFAULT 30,
    global_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    enabled_sources_json TEXT NOT NULL DEFAULT '["manual","company_ats","email_apply"]',
    exclusions_json TEXT NOT NULL DEFAULT '{"companies":[],"roles":[],"sources":[],"keywords":[],"countries":[]}',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(255) NOT NULL,
    target_entity_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency_keys_user_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_idempotency_keys_user_id ON idempotency_keys(user_id);

CREATE TABLE application_submissions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    pack_id UUID,
    application_id UUID,
    state VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    rule_decision_json TEXT NOT NULL,
    why_applied_or_skipped TEXT NOT NULL,
    resume_hash VARCHAR(128),
    answers_used_json TEXT NOT NULL,
    pack_snapshot_json TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    approval_token_hash VARCHAR(128),
    approval_token_expires_at TIMESTAMP WITH TIME ZONE,
    approval_token_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_submissions_user_idemp UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_submissions_user_id ON application_submissions(user_id);
CREATE INDEX idx_submissions_user_state ON application_submissions(user_id, state);

CREATE TABLE application_submission_attempts (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES application_submissions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    from_state VARCHAR(64),
    to_state VARCHAR(64) NOT NULL,
    rule_decision_json TEXT NOT NULL,
    resume_hash VARCHAR(128),
    answers_used_json TEXT NOT NULL,
    pack_snapshot_json TEXT NOT NULL,
    provider_response TEXT,
    actor VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_submission_attempts_sub_id ON application_submission_attempts(submission_id);
CREATE INDEX idx_submission_attempts_user_id ON application_submission_attempts(user_id);
