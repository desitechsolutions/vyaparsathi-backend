-- OAuth2 social login: provider identity columns on users table
ALTER TABLE users
    ADD COLUMN auth_provider   VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN auth_provider_id VARCHAR(255) NULL;

-- Unique constraint so two signups for the same provider+sub cannot collide.
-- MySQL allows multiple NULLs in a unique index, so LOCAL rows are unaffected.
CREATE UNIQUE INDEX idx_users_oauth_provider
    ON users (auth_provider, auth_provider_id);
