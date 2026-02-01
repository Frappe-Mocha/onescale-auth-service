-- OneScale Authentication Service - Database Schema
-- PostgreSQL 15+
--
-- This script creates the database schema for the authentication microservice
-- It includes tables for users and refresh tokens with proper indexes and constraints

-- =============================================================================
-- USERS TABLE
-- =============================================================================
-- Stores user account information from Firebase Authentication
-- Users are created when they first authenticate via Firebase

CREATE TABLE IF NOT EXISTS users (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,

    -- Firebase Integration
    firebase_uid VARCHAR(128) NOT NULL UNIQUE,

    -- User Contact Information
    email VARCHAR(255) UNIQUE,
    mobile_number VARCHAR(20) UNIQUE,

    -- User Profile
    full_name VARCHAR(255),
    profile_picture_url VARCHAR(500),

    -- Verification Status
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_mobile_verified BOOLEAN NOT NULL DEFAULT FALSE,

    -- Account Status
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Tracking
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for optimized queries
CREATE INDEX IF NOT EXISTS idx_user_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_user_mobile ON users(mobile_number);
CREATE INDEX IF NOT EXISTS idx_user_firebase_uid ON users(firebase_uid);

-- Comments for documentation
COMMENT ON TABLE users IS 'Stores user account information from Firebase Authentication';
COMMENT ON COLUMN users.firebase_uid IS 'Unique identifier from Firebase Authentication (e.g., abc123xyz)';
COMMENT ON COLUMN users.email IS 'User email address (from Firebase email/password auth)';
COMMENT ON COLUMN users.mobile_number IS 'User phone number in E.164 format (from Firebase phone auth)';
COMMENT ON COLUMN users.is_email_verified IS 'Whether email has been verified via Firebase';
COMMENT ON COLUMN users.is_mobile_verified IS 'Whether mobile number has been verified via Firebase';
COMMENT ON COLUMN users.is_active IS 'Account status - false means soft deleted';
COMMENT ON COLUMN users.last_login_at IS 'Timestamp of most recent successful authentication';

-- =============================================================================
-- REFRESH TOKENS TABLE
-- =============================================================================
-- Stores JWT refresh tokens for secure token management
-- Refresh tokens are long-lived (7 days) and can be revoked

CREATE TABLE IF NOT EXISTS refresh_tokens (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,

    -- Token Data
    token VARCHAR(1000) NOT NULL UNIQUE,

    -- User Relationship
    user_id BIGINT NOT NULL,

    -- Token Lifecycle
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign Key Constraint
    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- Indexes for optimized queries
CREATE INDEX IF NOT EXISTS idx_refresh_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expires ON refresh_tokens(expires_at);

-- Comments for documentation
COMMENT ON TABLE refresh_tokens IS 'Stores JWT refresh tokens with revocation support';
COMMENT ON COLUMN refresh_tokens.token IS 'The JWT refresh token string (up to 1000 characters)';
COMMENT ON COLUMN refresh_tokens.user_id IS 'Reference to the user who owns this token';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'Token expiration timestamp (7 days from creation)';
COMMENT ON COLUMN refresh_tokens.is_revoked IS 'Whether token has been explicitly revoked (logout)';
COMMENT ON COLUMN refresh_tokens.revoked_at IS 'Timestamp when token was revoked';

-- =============================================================================
-- AUTOMATIC TIMESTAMP UPDATE TRIGGER
-- =============================================================================
-- Automatically updates the updated_at column when a row is modified

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach trigger to users table
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- SAMPLE QUERIES
-- =============================================================================

-- Find user by Firebase UID
-- SELECT * FROM users WHERE firebase_uid = 'abc123xyz';

-- Find user by email
-- SELECT * FROM users WHERE email = 'user@example.com';

-- Find user by mobile number
-- SELECT * FROM users WHERE mobile_number = '+919876543210';

-- Get all valid refresh tokens for a user
-- SELECT * FROM refresh_tokens
-- WHERE user_id = 1
--   AND is_revoked = FALSE
--   AND expires_at > CURRENT_TIMESTAMP;

-- Revoke all tokens for a user (logout from all devices)
-- UPDATE refresh_tokens
-- SET is_revoked = TRUE, revoked_at = CURRENT_TIMESTAMP
-- WHERE user_id = 1 AND is_revoked = FALSE;

-- Clean up expired tokens (maintenance query - run periodically)
-- DELETE FROM refresh_tokens
-- WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '30 days';

-- Get user with token count
-- SELECT u.*, COUNT(rt.id) as active_sessions
-- FROM users u
-- LEFT JOIN refresh_tokens rt ON u.id = rt.user_id
--   AND rt.is_revoked = FALSE
--   AND rt.expires_at > CURRENT_TIMESTAMP
-- WHERE u.id = 1
-- GROUP BY u.id;

-- =============================================================================
-- DATA RETENTION NOTES
-- =============================================================================
--
-- 1. Expired refresh tokens can be safely deleted after they expire
-- 2. Revoked tokens should be kept for audit purposes (recommended: 30 days)
-- 3. Soft-deleted users (is_active = false) can be purged after grace period
-- 4. Consider implementing periodic cleanup jobs for old data
--
-- Example cleanup job (run daily):
-- DELETE FROM refresh_tokens
-- WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '30 days';
