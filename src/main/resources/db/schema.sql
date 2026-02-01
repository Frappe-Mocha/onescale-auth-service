-- OneScale Auth Service Database Schema (Firebase Authentication)

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    firebase_uid VARCHAR(128) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    mobile_number VARCHAR(20) UNIQUE,
    full_name VARCHAR(255),
    profile_picture_url VARCHAR(500),
    is_email_verified BOOLEAN DEFAULT FALSE,
    is_mobile_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create refresh_tokens table
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(1000) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indexes for users table
CREATE INDEX IF NOT EXISTS idx_user_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_user_mobile ON users(mobile_number);
CREATE INDEX IF NOT EXISTS idx_user_firebase_uid ON users(firebase_uid);

-- Create indexes for refresh_tokens table
CREATE INDEX IF NOT EXISTS idx_refresh_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expires ON refresh_tokens(expires_at);

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger for users table
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Insert comments for documentation
COMMENT ON TABLE users IS 'Stores user account information authenticated via Firebase';
COMMENT ON TABLE refresh_tokens IS 'Stores refresh tokens for JWT authentication';

COMMENT ON COLUMN users.firebase_uid IS 'Firebase User ID (unique identifier from Firebase Auth)';
COMMENT ON COLUMN users.email IS 'User email address (from Firebase)';
COMMENT ON COLUMN users.mobile_number IS 'User mobile number in E.164 format (from Firebase phone auth)';
COMMENT ON COLUMN users.is_email_verified IS 'Whether the email has been verified in Firebase';
COMMENT ON COLUMN users.is_mobile_verified IS 'Whether the mobile number has been verified in Firebase';
COMMENT ON COLUMN users.is_active IS 'Whether the user account is active';
COMMENT ON COLUMN users.last_login_at IS 'Timestamp of the last successful login';

COMMENT ON COLUMN refresh_tokens.token IS 'The JWT refresh token string';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'Timestamp when the refresh token expires';
COMMENT ON COLUMN refresh_tokens.is_revoked IS 'Whether the refresh token has been revoked';
COMMENT ON COLUMN refresh_tokens.revoked_at IS 'Timestamp when the token was revoked';
