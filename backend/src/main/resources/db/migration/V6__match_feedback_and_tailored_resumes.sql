-- Stage 12 & Stage 13: Full match fit/feedback columns and tailored_resumes table

ALTER TABLE job_matches ADD COLUMN salary_fit VARCHAR(255) NOT NULL DEFAULT 'not enough data';
ALTER TABLE job_matches ADD COLUMN qualification_fit VARCHAR(255) NOT NULL DEFAULT 'not enough data';
ALTER TABLE job_matches ADD COLUMN user_feedback VARCHAR(32);
ALTER TABLE job_matches ADD COLUMN feedback_note VARCHAR(500);

CREATE TABLE tailored_resumes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resume_version_id UUID NOT NULL REFERENCES resume_versions(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    keyword_gaps_json TEXT NOT NULL,
    tips_json TEXT NOT NULL,
    original_text TEXT NOT NULL,
    tailored_text TEXT NOT NULL,
    diff_json TEXT NOT NULL,
    truthfulness_passed BOOLEAN NOT NULL DEFAULT FALSE,
    unsupported_claims_json TEXT NOT NULL,
    claim_traces_json TEXT NOT NULL,
    user_approved BOOLEAN NOT NULL DEFAULT FALSE,
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tailored_resumes_user_id ON tailored_resumes(user_id);
CREATE INDEX idx_tailored_resumes_job_id ON tailored_resumes(job_id);
