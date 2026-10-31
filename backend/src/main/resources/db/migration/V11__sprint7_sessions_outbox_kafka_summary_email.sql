-- ============================================================================
-- Sprint 7 (V11): Sessions (Check-in / Check-out), Outbox Events,
--                 Session Applied Items, and Session Summary Emails
-- ============================================================================

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    checked_in_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_out_at TIMESTAMP WITH TIME ZONE NULL,
    beacon_received_at TIMESTAMP WITH TIME ZONE NULL,
    beacon_token_hash VARCHAR(128) NOT NULL,
    checkout_reason VARCHAR(32) NULL,
    time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    summary_email_status VARCHAR(64) NOT NULL DEFAULT 'NONE',
    checkout_event_id UUID NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_sessions_user_checked_out ON user_sessions(user_id, checked_out_at);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic VARCHAR(128) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT NULL
);

CREATE INDEX idx_outbox_events_unpublished ON outbox_events(published_at, created_at);
CREATE INDEX idx_outbox_events_user ON outbox_events(user_id);

CREATE TABLE session_applied_items (
    id UUID PRIMARY KEY,
    session_id UUID NULL REFERENCES user_sessions(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    application_id UUID NOT NULL UNIQUE,
    company VARCHAR(255) NOT NULL,
    job_title VARCHAR(255) NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL,
    method VARCHAR(128) NOT NULL,
    confirmation_type VARCHAR(64) NOT NULL,
    while_away BOOLEAN NOT NULL DEFAULT FALSE,
    event_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_session_applied_items_session ON session_applied_items(session_id, applied_at);
CREATE INDEX idx_session_applied_items_user ON session_applied_items(user_id, applied_at);

CREATE TABLE session_summary_emails (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES user_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provider_message_id VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body_html TEXT NOT NULL,
    body_text TEXT NOT NULL
);

CREATE INDEX idx_session_summary_emails_user_sent ON session_summary_emails(user_id, sent_at);

ALTER TABLE user_notification_preferences
    ADD COLUMN session_summary_email_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_notification_preferences
    ADD COLUMN include_while_away_applications BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE user_notification_preferences
    ADD COLUMN unsubscribe_token_hash VARCHAR(128) NULL;

