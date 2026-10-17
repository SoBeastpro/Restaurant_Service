ALTER TABLE users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD CONSTRAINT users_failed_login_attempts_check CHECK (failed_login_attempts >= 0);

CREATE INDEX users_temporarily_locked_idx
    ON users (locked_until) WHERE locked_until IS NOT NULL;
