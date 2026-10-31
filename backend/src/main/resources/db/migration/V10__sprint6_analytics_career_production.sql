-- Sprint 6: Analytics, Career (Skill Roadmap & Interview Prep), Business & Production (Feature Flags, Feedback, Usage Events)

CREATE TABLE skill_roadmap_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_name VARCHAR(150) NOT NULL,
    week_number INTEGER NOT NULL DEFAULT 1,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    learning_links_json TEXT NOT NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skill_roadmap_user_id ON skill_roadmap_items(user_id, week_number);

CREATE TABLE interview_prep_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID,
    topic VARCHAR(120) NOT NULL,
    session_type VARCHAR(64) NOT NULL DEFAULT 'MOCK_INTERVIEW',
    questions_json TEXT NOT NULL,
    user_answer TEXT,
    rubric_feedback_json TEXT,
    weak_topics_json TEXT,
    score INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_interview_prep_user_id ON interview_prep_sessions(user_id);

CREATE TABLE feature_flags (
    id UUID PRIMARY KEY,
    flag_key VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    free_tier_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    paid_tier_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    free_tier_limit VARCHAR(100) NOT NULL,
    paid_tier_limit VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO feature_flags (id, flag_key, display_name, description, free_tier_enabled, paid_tier_enabled, free_tier_limit, paid_tier_limit)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'RESUME_VERSIONS', 'Resume Versions & Truthfulness Check', 'Upload, parse, and verify resume versions with zero unsupported claims.', TRUE, TRUE, 'Up to 3 versions', 'Up to 10 versions'),
    ('a0000000-0000-0000-0000-000000000002', 'AI_JOB_SEARCH_AND_MATCH', 'AI Job Search & Explainable Match', 'Natural-language job search across permitted public boards with 60/10/20/10 match scoring.', TRUE, TRUE, 'Unlimited stored jobs', 'Unlimited + priority refresh'),
    ('a0000000-0000-0000-0000-000000000003', 'ASSISTED_AND_REVIEW_APPLY', 'Mode 1 Assisted & Mode 2 Review Queue', 'Truthfulness-checked application packs, cover notes in 3 tones, and mobile approval queue.', TRUE, TRUE, '5 packs/day', '20 packs/day'),
    ('a0000000-0000-0000-0000-000000000004', 'MODE3_AND_EMAIL_APPLY', 'Mode 3 Local Agent & OAuth Email-Apply', 'Consent-gated OAuth email apply and local Playwright apply-agent (FILL_ONLY default).', FALSE, TRUE, 'Dry-run & Fill-Only preview', 'Enabled with consent & safety caps (3/day)'),
    ('a0000000-0000-0000-0000-000000000005', 'CAREER_ROADMAP_AND_MOCK_INTERVIEW', 'Skill Roadmap & Rubric Mock Interviews', 'Weekly missing-skill study plan and text mock interviews with rubric feedback.', TRUE, TRUE, '3 mock sessions/week', 'Unlimited mock sessions');

CREATE TABLE user_feedback (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category VARCHAR(64) NOT NULL DEFAULT 'GENERAL',
    rating INTEGER NOT NULL DEFAULT 5,
    message TEXT NOT NULL,
    page_context VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_feedback_user_id ON user_feedback(user_id);

CREATE TABLE usage_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_name VARCHAR(100) NOT NULL,
    metadata_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usage_events_user_id ON usage_events(user_id, created_at);

