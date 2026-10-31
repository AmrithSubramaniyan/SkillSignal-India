-- Sprint 5 (Stages 24-29): Rules & Dry Run, Email-Apply, Full Safety Governor, Mode 3 Enablement, Playwright Lite Agent, Learning Loop & Auto-Apply Centre

CREATE TABLE auto_apply_rules (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rule_name VARCHAR(255) NOT NULL,
    priority_order INTEGER NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    conditions_json TEXT NOT NULL,
    tier_t1_policy VARCHAR(32) NOT NULL DEFAULT 'REVIEW',
    tier_t2_policy VARCHAR(32) NOT NULL DEFAULT 'ELIGIBLE',
    tier_t3_policy VARCHAR(32) NOT NULL DEFAULT 'ELIGIBLE',
    time_window_start VARCHAR(16) NOT NULL DEFAULT '09:00',
    time_window_end VARCHAR(16) NOT NULL DEFAULT '19:00',
    company_cooldown_days INTEGER NOT NULL DEFAULT 30,
    override_company_cooldown_if_approved BOOLEAN NOT NULL DEFAULT FALSE,
    min_spread_minutes INTEGER NOT NULL DEFAULT 15,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auto_apply_rules_user_order ON auto_apply_rules(user_id, priority_order);

CREATE TABLE auto_apply_runs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rule_id UUID,
    dry_run BOOLEAN NOT NULL DEFAULT TRUE,
    total_jobs_evaluated INTEGER NOT NULL DEFAULT 0,
    matched_jobs_count INTEGER NOT NULL DEFAULT 0,
    submissions_created_count INTEGER NOT NULL DEFAULT 0,
    summary_text VARCHAR(512) NOT NULL,
    results_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auto_apply_runs_user_id ON auto_apply_runs(user_id);

CREATE TABLE connected_mailboxes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    email_address VARCHAR(255) NOT NULL,
    scopes VARCHAR(512) NOT NULL,
    encrypted_token_ref TEXT NOT NULL,
    ack_detection_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'CONNECTED',
    connected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_connected_mailboxes_user_id ON connected_mailboxes(user_id);

CREATE TABLE email_apply_messages (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    application_id UUID,
    mailbox_id UUID,
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(512) NOT NULL,
    body_text TEXT NOT NULL,
    resume_version_id UUID,
    approved_by_user BOOLEAN NOT NULL DEFAULT FALSE,
    provider_message_id VARCHAR(255),
    status VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    ack_detected_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_apply_messages_user_id ON email_apply_messages(user_id);

CREATE TABLE safety_events (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    source VARCHAR(64),
    severity VARCHAR(32) NOT NULL DEFAULT 'HIGH',
    action_taken VARCHAR(128) NOT NULL,
    details_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_safety_events_user_id ON safety_events(user_id);

CREATE TABLE source_circuit_breakers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'CLOSED',
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    window_request_count INTEGER NOT NULL DEFAULT 0,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_reason TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_circuit_breaker_user_source UNIQUE (user_id, source)
);

CREATE INDEX idx_circuit_breaker_user_id ON source_circuit_breakers(user_id);

CREATE TABLE apply_agents (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    agent_name VARCHAR(255) NOT NULL,
    device_token_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_apply_agents_user_id ON apply_agents(user_id);

CREATE TABLE portal_settings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    portal VARCHAR(64) NOT NULL,
    consent_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    consent_version VARCHAR(32),
    typed_confirmation VARCHAR(128),
    apply_mode VARCHAR(32) NOT NULL DEFAULT 'FILL_ONLY',
    completed_fill_only_count INTEGER NOT NULL DEFAULT 0,
    dry_run_completed BOOLEAN NOT NULL DEFAULT FALSE,
    login_state VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_portal_settings_user_portal UNIQUE (user_id, portal)
);

CREATE INDEX idx_portal_settings_user_id ON portal_settings(user_id);

CREATE TABLE apply_tasks (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    submission_id UUID,
    agent_id UUID,
    portal VARCHAR(64) NOT NULL,
    apply_mode VARCHAR(32) NOT NULL DEFAULT 'FILL_ONLY',
    status VARCHAR(64) NOT NULL DEFAULT 'QUEUED',
    blocker_type VARCHAR(64),
    blocker_message TEXT,
    confirmation_signal VARCHAR(64),
    payload_json TEXT NOT NULL,
    result_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_apply_tasks_user_status ON apply_tasks(user_id, status);

CREATE TABLE apply_evidence (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES apply_tasks(id) ON DELETE CASCADE,
    evidence_type VARCHAR(64) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    summary_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_apply_evidence_user_id ON apply_evidence(user_id);

CREATE TABLE rule_change_suggestions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    suggestion_title VARCHAR(255) NOT NULL,
    rationale TEXT NOT NULL,
    proposed_change_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_USER_CONSENT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rule_suggestions_user_id ON rule_change_suggestions(user_id);

CREATE TABLE skip_reason_feedback (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    submission_id UUID,
    job_id UUID,
    skip_reason VARCHAR(255) NOT NULL,
    agree_with_skip BOOLEAN NOT NULL,
    user_comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skip_feedback_user_id ON skip_reason_feedback(user_id);

