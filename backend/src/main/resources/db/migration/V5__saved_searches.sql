-- Stage 11: Saved Searches with scheduled daily refresh

CREATE TABLE saved_searches (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    raw_query VARCHAR(1000),
    filters_json TEXT NOT NULL,
    daily_refresh_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_refreshed_at TIMESTAMP WITH TIME ZONE,
    last_match_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_saved_searches_user_id ON saved_searches(user_id);
CREATE INDEX idx_saved_searches_daily_refresh ON saved_searches(daily_refresh_enabled);
