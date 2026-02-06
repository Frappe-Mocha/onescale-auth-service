-- OneScale Authentication Service - Database Schema
-- PostgreSQL 15+
--
-- This script creates the database schema for the authentication microservice
-- It includes tables for users and refresh tokens with proper indexes and constraints

-- =============================================================================
-- USERS TABLE
-- =============================================================================
-- Stores user account information.
-- Users are created when the frontend POSTs to /register after completing
-- OAuth / OTP verification on the client side.  The backend never contacts
-- Firebase or any other OAuth provider directly.

CREATE TABLE IF NOT EXISTS users (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,

    -- Backend-issued UUID.  Included in every JWT this user receives.
    -- The /token endpoint requires it to issue new tokens.
    client_id VARCHAR(36) NOT NULL UNIQUE,

    -- Android device identifier supplied by the frontend.
    -- Updated on every login so the backend can validate it at token time.
    device_id VARCHAR(255) NOT NULL,

    -- Auth provider used on the frontend: GOOGLE, FACEBOOK, EMAIL, MOBILE, PASSWORD
    provider VARCHAR(20) NOT NULL,

    -- Password (BCrypt hash) - only for PASSWORD provider, null for OAuth providers
    password_hash VARCHAR(255),

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
CREATE INDEX IF NOT EXISTS idx_user_client_id ON users(client_id);
CREATE INDEX IF NOT EXISTS idx_user_device_id ON users(device_id);

-- Comments for documentation
COMMENT ON TABLE users IS 'User accounts — created by the frontend after client-side OAuth/OTP verification';
COMMENT ON COLUMN users.client_id IS 'Backend-generated UUID; required by /token to issue JWTs';
COMMENT ON COLUMN users.device_id IS 'Android device identifier; updated on every login';
COMMENT ON COLUMN users.provider IS 'Auth provider: PASSWORD (backend-validated), GOOGLE, FACEBOOK, EMAIL, MOBILE (OAuth)';
COMMENT ON COLUMN users.password_hash IS 'BCrypt password hash (only for PASSWORD provider, null for OAuth)';
COMMENT ON COLUMN users.email IS 'User email address (unique, nullable)';
COMMENT ON COLUMN users.mobile_number IS 'User phone number in E.164 format (unique, nullable)';
COMMENT ON COLUMN users.is_email_verified IS 'Whether email was supplied at registration';
COMMENT ON COLUMN users.is_mobile_verified IS 'Whether mobile number was supplied at registration';
COMMENT ON COLUMN users.is_active IS 'Account status — false means soft-deleted';
COMMENT ON COLUMN users.last_login_at IS 'Timestamp of most recent successful login';

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

-- Find user by client_id (used internally by /token)
-- SELECT * FROM users WHERE client_id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';

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
