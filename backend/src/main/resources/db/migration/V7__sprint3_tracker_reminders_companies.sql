-- Sprint 3 (Stages 14-17): Full Tracker, Reminders & Notifications, and Companies

ALTER TABLE applications ADD COLUMN sub_state VARCHAR(64);
ALTER TABLE applications ADD COLUMN submission_method VARCHAR(64) NOT NULL DEFAULT 'ASSISTED';
ALTER TABLE applications ADD COLUMN confirmation_type VARCHAR(64) NOT NULL DEFAULT 'UNCONFIRMED';
ALTER TABLE applications ADD COLUMN recruiter_contacts TEXT;
ALTER TABLE applications ADD COLUMN assessment_date DATE;
ALTER TABLE applications ADD COLUMN interview_date DATE;
ALTER TABLE applications ADD COLUMN next_action VARCHAR(255);
ALTER TABLE applications ADD COLUMN source VARCHAR(100);
ALTER TABLE applications ADD COLUMN application_url VARCHAR(1024);

CREATE TABLE user_notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    quiet_hours_start VARCHAR(8) NOT NULL DEFAULT '22:00',
    quiet_hours_end VARCHAR(8) NOT NULL DEFAULT '08:00',
    daily_summary_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reminders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    application_id UUID REFERENCES applications(id) ON DELETE CASCADE,
    reminder_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    snoozed_until TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reminders_user_id ON reminders(user_id);
CREATE INDEX idx_reminders_user_due ON reminders(user_id, status, due_at);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reminder_id UUID REFERENCES reminders(id) ON DELETE SET NULL,
    channel VARCHAR(32) NOT NULL DEFAULT 'IN_APP',
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    suppressed_by_quiet_hours BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);

CREATE TABLE notification_sent_logs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    dedup_key VARCHAR(255) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_sent_dedup UNIQUE (user_id, dedup_key, channel)
);

CREATE INDEX idx_notification_sent_user_id ON notification_sent_logs(user_id);

CREATE TABLE companies (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    tier VARCHAR(16) NOT NULL DEFAULT 'T2',
    is_priority BOOLEAN NOT NULL DEFAULT FALSE,
    website VARCHAR(512),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_companies_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_companies_user_id ON companies(user_id);

